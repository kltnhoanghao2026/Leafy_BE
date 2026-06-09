import json
import logging
import uuid
from typing import Any, AsyncGenerator, Dict, List, Optional

from fastapi import Request
from langchain_core.messages import HumanMessage

import app.agents.rag_agent as rag_agent_module
from app.core.security import UserPrincipal
from app.dto.chat.chat_dto import ChatRequest, ChatResponse
from app.exceptions.app_exception import AppException
from app.exceptions.error_code import ErrorCode
from app.repositories.chat_repository import get_chat_repository

logger = logging.getLogger(__name__)


class ChatService:
    """
    Service layer for chat execution.

    Responsibilities:
    - Build and execute RAG graph calls.
    - Build chat response DTOs.
    - Produce SSE events for streaming endpoint.
    """

    _instance = None

    def __new__(cls):
        if cls._instance is None:
            instance = super().__new__(cls)
            instance._chat_repository = get_chat_repository()
            cls._instance = instance
        return cls._instance

    def _build_initial_state(self, request: ChatRequest, user_id: str, auth_header: Optional[str]) -> Dict[str, Any]:
        """Create a clean per-turn graph state payload."""
        return {
            "messages": [HumanMessage(content=request.question)],
            "question": request.question,
            "retry_count": 0,
            "language": request.language,
            "user_id": user_id,
            "authorization": auth_header,
            # Reset turn-scoped fields so previous checkpoint state does not leak
            # into the new user question on the same thread.
            "generation": "",
            "documents": [],
            "web_search_results": [],
            "safety_issues": [],
            "refinement_count": 0,
            "refinement_guidance": "",
            "generated_plan": None,
            "plant_id": request.plant_id or None,
            "farm_plot_id": request.farm_plot_id or None,
            "farm_zone_id": request.farm_zone_id or None,
            # Map frontend "planner" → internal "planning"; "auto" → None (use auto-routing)
            "forced_route": (
                "planning" if request.route == "planner"
                else request.route if request.route not in (None, "auto")
                else None
            ),
            # Dedicated Qdrant retrieval query (optional) — decoupled from the
            # generation prompt.  Hybrid search prefers this over `question` when set.
            "search_query": request.search_query or None,
        }

    def _build_chat_result(
        self,
        final_state: Dict[str, Any],
        *,
        thread_id: str,
        saved_plan_id: Optional[str],
    ) -> ChatResponse:
        generated_plan: Optional[Dict[str, Any]] = final_state.get("generated_plan")
        return ChatResponse(
            answer=final_state.get("generation", "I could not generate an answer."),
            thread_id=thread_id,
            documents=final_state.get("documents", []),
            plan=generated_plan,
            plant_id=generated_plan.get("plantId") if generated_plan else None,
            web_search_results=final_state.get("web_search_results", []),
            saved_plan_id=saved_plan_id,
        )

    def _json_default(self, value: Any) -> Any:
        """Fallback serializer for SSE payloads."""
        if hasattr(value, "model_dump"):
            return value.model_dump(mode="json")
        if hasattr(value, "dict"):
            return value.dict()
        return str(value)

    def _to_sse(self, event_name: str, payload: Dict[str, Any]) -> str:
        data = json.dumps(payload, default=self._json_default)
        return f"event: {event_name}\ndata: {data}\n\n"

    def _chunk_text(self, text: str, chunk_size: int = 180) -> List[str]:
        if not text:
            return []
        return [text[i : i + chunk_size] for i in range(0, len(text), chunk_size)]

    def _build_state_snapshot(self, node_update: Dict[str, Any]) -> Dict[str, Any]:
        """Compact state snapshot for stream clients (safe, bounded, and JSON-friendly)."""
        snapshot: Dict[str, Any] = {}

        if "path_type" in node_update:
            snapshot["path_type"] = node_update.get("path_type")
        if "intent" in node_update:
            snapshot["intent"] = node_update.get("intent")
        if "retry_count" in node_update:
            snapshot["retry_count"] = node_update.get("retry_count")
        if "refinement_count" in node_update:
            snapshot["refinement_count"] = node_update.get("refinement_count")
        if "confidence_score" in node_update:
            snapshot["confidence_score"] = node_update.get("confidence_score")
        if "completeness_score" in node_update:
            snapshot["completeness_score"] = node_update.get("completeness_score")
        if "safety_passed" in node_update:
            snapshot["safety_passed"] = node_update.get("safety_passed")
        if "safety_issues" in node_update:
            snapshot["safety_issues"] = node_update.get("safety_issues")

        if "documents" in node_update:
            documents = node_update.get("documents") or []
            snapshot["documents_count"] = len(documents)
        if "candidate_docs" in node_update:
            candidates = node_update.get("candidate_docs") or []
            snapshot["candidate_docs_count"] = len(candidates)
        if "reranked_docs" in node_update:
            reranked = node_update.get("reranked_docs") or []
            snapshot["reranked_docs_count"] = len(reranked)
        if "web_search_results" in node_update:
            web = node_update.get("web_search_results") or []
            snapshot["web_search_results_count"] = len(web)

        generation = node_update.get("generation")
        if isinstance(generation, str) and generation:
            snapshot["generation_length"] = len(generation)

        generated_plan = node_update.get("generated_plan")
        if isinstance(generated_plan, dict) and generated_plan:
            schedule = generated_plan.get("schedule") or []
            snapshot["generated_plan"] = {
                "plantId": generated_plan.get("plantId"),
                "diseaseName": generated_plan.get("diseaseName"),
                "event_count": len(schedule),
            }

        return snapshot

    def _get_graph(self):
        graph = rag_agent_module.rag_app
        if graph is None:
            raise AppException(
                ErrorCode.RAG_PIPELINE_ERROR,
                "RAG graph is not initialised yet - service may still be starting up.",
            )
        return graph

    async def run_chat(
        self,
        request: ChatRequest,
        current_user: UserPrincipal,
        auth_header: Optional[str] = None,
        save_conversation: bool = True,
    ) -> ChatResponse:
        """Execute non-streaming chat flow and return final response DTO."""
        thread_id = request.thread_id or str(uuid.uuid4())
        config = {"configurable": {"thread_id": thread_id}, "recursion_limit": 50}
        initial_state = self._build_initial_state(request, current_user.id, auth_header)

        try:
            graph = self._get_graph()
            final_state = await graph.ainvoke(initial_state, config=config)
        except AppException:
            raise
        except Exception as e:
            logger.error("RAG pipeline error for user %s: %s", current_user.id, e, exc_info=True)
            raise AppException(ErrorCode.RAG_PIPELINE_ERROR, str(e))

        saved_plan_id = None
        if save_conversation:
            saved_plan_id = self._chat_repository.persist_plan_if_any(
                final_state,
                user_id=current_user.id,
                question=request.question,
                auth_header=auth_header,
            )

        result = self._build_chat_result(
            final_state,
            thread_id=thread_id,
            saved_plan_id=saved_plan_id,
        )

        if save_conversation:
            self._chat_repository.persist_conversation_turn(
                user_id=current_user.id,
                thread_id=thread_id,
                question=request.question,
                answer=result.answer,
                final_state=final_state,
                saved_plan_id=saved_plan_id,
                rag_state="completed",
                current_node="END",
                step=None,
            )
        return result

    async def stream_chat(
        self,
        request: ChatRequest,
        raw_request: Request,
        current_user: UserPrincipal,
        auth_header: Optional[str] = None,
    ) -> AsyncGenerator[str, None]:
        """Stream chat execution via SSE events."""
        thread_id = request.thread_id or str(uuid.uuid4())
        config = {"configurable": {"thread_id": thread_id}, "recursion_limit": 50}
        initial_state = self._build_initial_state(request, current_user.id, auth_header)

        graph = self._get_graph()

        step = 0
        latest_state: Dict[str, Any] = dict(initial_state)
        last_generation = ""
        disconnected = False
        node_step: Dict[str, int] = {}
        last_node = "START"

        yield self._to_sse(
            "state",
            {
                "rag_state": "started",
                "step": step,
                "current_node": "START",
                "thread_id": thread_id,
            },
        )

        try:
            async for event in graph.astream_events(initial_state, config=config, version="v2"):
                if await raw_request.is_disconnected():
                    logger.info("SSE client disconnected - userId=%s threadId=%s", current_user.id, thread_id)
                    disconnected = True
                    break

                if not isinstance(event, dict):
                    continue

                event_name = event.get("event")
                metadata = event.get("metadata") if isinstance(event.get("metadata"), dict) else {}
                node_name = metadata.get("langgraph_node") if isinstance(metadata.get("langgraph_node"), str) else None

                if not node_name or node_name in {"__start__", "__end__"}:
                    continue

                if event_name == "on_chain_start":
                    step += 1
                    node_step[node_name] = step
                    last_node = node_name
                    yield self._to_sse(
                        "state",
                        {
                            "rag_state": "running",
                            "step": step,
                            "current_node": node_name,
                            "updated_fields": [],
                            "state": {},
                        },
                    )
                    continue

                if event_name == "on_chat_model_stream":
                    current_step = node_step.get(node_name, step)
                    chunk_data = event.get("data", {}).get("chunk")
                    if chunk_data and hasattr(chunk_data, "content") and isinstance(chunk_data.content, str) and chunk_data.content:
                        text_chunk = chunk_data.content
                        last_generation += text_chunk
                        yield self._to_sse(
                            "response_chunk",
                            {
                                "rag_state": "streaming_response",
                                "step": current_step,
                                "current_node": node_name,
                                "chunk": text_chunk,
                            },
                        )
                    continue

                if event_name != "on_chain_end":
                    continue

                current_step = node_step.get(node_name, step)
                last_node = node_name
                data = event.get("data") if isinstance(event.get("data"), dict) else {}
                output = data.get("output") if isinstance(data.get("output"), dict) else {}

                if not isinstance(output, dict):
                    continue

                node_data = output
                latest_state.update(node_data)

                yield self._to_sse(
                    "state",
                    {
                        "rag_state": "running",
                        "step": current_step,
                        "current_node": node_name,
                        "updated_fields": list(node_data.keys()),
                        "state": self._build_state_snapshot(node_data),
                    },
                )

                generation = node_data.get("generation")
                if isinstance(generation, str) and generation:
                    if generation.startswith(last_generation):
                        delta = generation[len(last_generation) :]
                    else:
                        delta = generation

                    if delta:
                        chunks = self._chunk_text(delta)
                        for idx, chunk in enumerate(chunks, start=1):
                            yield self._to_sse(
                                "response_chunk",
                                {
                                    "rag_state": "streaming_response",
                                    "step": current_step,
                                    "current_node": node_name,
                                    "chunk_index": idx,
                                    "chunk": chunk,
                                },
                            )

                    last_generation = generation

            if disconnected:
                return

            saved_plan_id = self._chat_repository.persist_plan_if_any(
                latest_state,
                user_id=current_user.id,
                question=request.question,
                auth_header=auth_header,
            )
            result = self._build_chat_result(
                latest_state,
                thread_id=thread_id,
                saved_plan_id=saved_plan_id,
            )

            conversation_id = self._chat_repository.persist_conversation_turn(
                user_id=current_user.id,
                thread_id=thread_id,
                question=request.question,
                answer=result.answer,
                final_state=latest_state,
                saved_plan_id=saved_plan_id,
                rag_state="completed",
                current_node=last_node or "END",
                step=step,
            )

            yield self._to_sse(
                "completed",
                {
                    "rag_state": "completed",
                    "step": step,
                    "current_node": "END",
                    "conversation_id": conversation_id,
                    "result": {
                        "answer": result.answer,
                        "thread_id": result.thread_id,
                        "documents": self._chat_repository.serialize_documents(result.documents or []),
                        "plan": result.plan,
                        "plant_id": result.plant_id,
                        "web_search_results": result.web_search_results,
                        "saved_plan_id": result.saved_plan_id,
                    },
                },
            )
        except Exception as e:
            logger.error(
                "RAG streaming pipeline error for user %s: %s",
                current_user.id,
                e,
                exc_info=True,
            )

            self._chat_repository.persist_conversation_turn(
                user_id=current_user.id,
                thread_id=thread_id,
                question=request.question,
                answer=str(e),
                final_state=latest_state,
                saved_plan_id=None,
                rag_state="error",
                current_node="ERROR",
                step=step,
            )

            yield self._to_sse(
                "error",
                {
                    "rag_state": "error",
                    "step": step,
                    "current_node": "ERROR",
                    "message": str(e),
                },
            )


def get_chat_service() -> ChatService:
    return ChatService()
