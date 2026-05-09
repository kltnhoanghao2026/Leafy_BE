import logging
from typing import Any, Dict, List, Optional

from app.models.plan_doc import PlanDoc
from app.repositories.conversation_repository import get_conversation_repository
from app.repositories.plan_repository import get_plan_repository

logger = logging.getLogger(__name__)


class ChatRepository:
    """
    Repository layer for chat-related persistence concerns.

    Responsibilities:
    - Convert graph documents to JSON-safe records.
    - Persist generated treatment plans to MongoDB.
    """

    _instance = None

    def __new__(cls):
        if cls._instance is None:
            instance = super().__new__(cls)
            instance._conversation_repository = get_conversation_repository()
            cls._instance = instance
        return cls._instance

    def serialize_document(self, doc: Any) -> Dict[str, Any]:
        """Best-effort conversion of a document-like object to a JSON-safe dict."""
        if isinstance(doc, dict):
            return {
                "page_content": doc.get("page_content", ""),
                "metadata": doc.get("metadata", {}),
            }

        page_content = getattr(doc, "page_content", "")
        metadata = getattr(doc, "metadata", {})
        return {
            "page_content": page_content,
            "metadata": metadata if isinstance(metadata, dict) else {},
        }

    def serialize_documents(self, raw_docs: List[Any]) -> List[Dict[str, Any]]:
        return [self.serialize_document(doc) for doc in raw_docs]

    def resolve_plan_source(
        self,
        generated_plan: Dict[str, Any],
        source_documents: List[Dict[str, Any]],
        web_search_results: List[Dict[str, Any]],
    ) -> Optional[str]:
        source = generated_plan.get("source")
        if isinstance(source, str):
            normalized = source.strip().lower()
            if normalized in {"websearch", "documents"}:
                return normalized

        if web_search_results:
            return "websearch"
        if source_documents:
            return "documents"
        return None

    def persist_plan_if_any(
        self,
        final_state: Dict[str, Any],
        *,
        user_id: str,
        question: str,
    ) -> Optional[str]:
        """Persist generated treatment plan (if present); never raises to callers."""
        generated_plan: Optional[Dict[str, Any]] = final_state.get("generated_plan")
        if not generated_plan:
            return None

        try:
            raw_docs = final_state.get("documents") or []
            source_documents = self.serialize_documents(raw_docs)
            web_search_results = final_state.get("web_search_results") or []

            plan_source = self.resolve_plan_source(
                generated_plan,
                source_documents,
                web_search_results,
            )
            if plan_source:
                generated_plan = {**generated_plan, "source": plan_source}

            doc = PlanDoc(
                userId=user_id,
                question=question,
                plantId=generated_plan.get("plantId"),
                diseaseName=generated_plan.get("diseaseName"),
                severityLevel=generated_plan.get("severityLevel"),
                urgency=generated_plan.get("urgency"),
                source=plan_source,
                plan=generated_plan,
                source_documents=source_documents or None,
                web_search_results=web_search_results or None,
            )
            repo = get_plan_repository()
            saved_plan_id = repo.save_plan(doc.model_dump(mode="json"))
            logger.info(
                "Plan persisted - planId=%s, userId=%s, source=%s, docs=%d, web=%d",
                saved_plan_id,
                user_id,
                plan_source,
                len(source_documents),
                len(web_search_results),
            )
            return saved_plan_id
        except Exception as e:
            # Save errors are logged but NEVER propagate to the caller
            logger.error("Failed to persist Plan: %s", e, exc_info=True)
            return None

    def _build_pipeline_state(
        self,
        *,
        rag_state: Optional[str],
        current_node: Optional[str],
        step: Optional[int],
    ) -> Dict[str, Any]:
        payload: Dict[str, Any] = {}
        if rag_state:
            payload["ragState"] = rag_state
        if current_node:
            payload["currentNode"] = current_node
        if step is not None:
            payload["step"] = step
        return payload

    def _build_response_meta(
        self,
        final_state: Dict[str, Any],
        *,
        saved_plan_id: Optional[str],
    ) -> Dict[str, Any]:
        documents = final_state.get("documents") or []
        web_results = final_state.get("web_search_results") or []
        generated_plan = final_state.get("generated_plan")
        return {
            "documentsCount": len(documents),
            "webResultsCount": len(web_results),
            "savedPlanId": saved_plan_id,
            "plan": generated_plan if isinstance(generated_plan, dict) else None,
        }

    def persist_conversation_turn(
        self,
        *,
        user_id: str,
        thread_id: str,
        question: str,
        answer: str,
        final_state: Dict[str, Any],
        saved_plan_id: Optional[str],
        rag_state: Optional[str] = None,
        current_node: Optional[str] = None,
        step: Optional[int] = None,
    ) -> Optional[str]:
        if not thread_id:
            return None

        try:
            pipeline_state = self._build_pipeline_state(
                rag_state=rag_state,
                current_node=current_node,
                step=step,
            )
            response_meta = self._build_response_meta(
                final_state,
                saved_plan_id=saved_plan_id,
            )

            result = self._conversation_repository.upsert_turn(
                user_id=user_id,
                thread_id=thread_id,
                question=question,
                answer=answer,
                pipeline_state=pipeline_state or None,
                response_meta=response_meta,
            )
            conversation_id = result.get("conversationId")
            logger.info(
                "Conversation turn persisted - conversationId=%s, threadId=%s, userId=%s",
                conversation_id,
                thread_id,
                user_id,
            )
            return conversation_id
        except Exception as e:
            logger.error("Failed to persist conversation turn: %s", e, exc_info=True)
            return None


def get_chat_repository() -> ChatRepository:
    return ChatRepository()
