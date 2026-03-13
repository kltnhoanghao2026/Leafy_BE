import logging
from typing import List

from fastapi import APIRouter, Depends

from app.agents.rag_agent import rag_app
from app.core.security import get_current_user, UserPrincipal
from app.dto.chat.chat_dto import ChatRequest, ChatResponse
from app.dto.response.api_response import ApiResponse
from app.exceptions.app_exception import AppException
from app.exceptions.error_code import ErrorCode
from app.models.treatment_plan_doc import TreatmentPlanDoc
from app.repositories.treatment_plan_repository import get_treatment_plan_repository

logger = logging.getLogger(__name__)

router = APIRouter()


@router.post(
    "/chat",
    response_model=ApiResponse[ChatResponse],
    summary="Ask a question via the RAG pipeline",
    responses={
        200: {"description": "Grounded answer successfully generated."},
        401: {"description": "Authentication required."},
        500: {"description": "Internal error during graph execution."},
    },
)
async def chat(
    request: ChatRequest,
    current_user: UserPrincipal = Depends(get_current_user),
):
    """
    Submit a natural-language question and receive a **grounded answer** produced
    by the LangGraph RAG state-machine.

    **Pipeline stages (executed in order):**
    1. **Router** — decides whether a fast or deep retrieval path is needed.
    2. **HyDE** — generates a hypothetical answer to improve embedding alignment.
    3. **Hybrid Search** — combines dense vector search with BM25 sparse retrieval.
    4. **Reranker** — cross-encoder reranking to surface the most relevant chunks.
    5. **Generation** — the LLM (Gemini / GPT) synthesises the final answer.
    6. **Self-correction** — if the answer is not grounded, the query is transformed
       and the graph retries (up to `max_retries` times).

    If the query is a treatment-plan request, the generated plan is automatically
    persisted to MongoDB (`leafy_rag.treatment_plans`).
    """
    try:
        initial_state = {
            "question": request.question,
            "retry_count": 0,
            "language": request.language,
        }
        final_state = await rag_app.ainvoke(initial_state)
    except Exception as e:
        logger.error("RAG pipeline error for user %s: %s", current_user.id, e, exc_info=True)
        raise AppException(ErrorCode.RAG_PIPELINE_ERROR, str(e))

    # ── Persist TreatmentPlan to MongoDB (if pipeline produced one) ────────────
    saved_plan_id = None
    generated_plan: dict = final_state.get("generated_plan")
    if generated_plan:
        try:
            # Serialize LangChain Document objects → plain dicts for MongoDB storage
            raw_docs = final_state.get("documents") or []
            source_documents = [
                {"page_content": doc.page_content, "metadata": doc.metadata}
                for doc in raw_docs
            ]

            doc = TreatmentPlanDoc(
                userId=current_user.id,
                question=request.question,
                plant_id=final_state.get("plant_id"),
                disease_name=generated_plan.get("disease_name"),
                severity_level=generated_plan.get("severity_level"),
                urgency=generated_plan.get("urgency"),
                plan=generated_plan,
                source_documents=source_documents or None,
                web_search_results=final_state.get("web_search_results") or None,
            )
            repo = get_treatment_plan_repository()
            saved_plan_id = repo.save_plan(doc.model_dump(mode="json"))
            logger.info(
                "TreatmentPlan persisted — planId=%s, userId=%s, docs=%d, web=%d",
                saved_plan_id,
                current_user.id,
                len(source_documents),
                len(final_state.get("web_search_results") or []),
            )
        except Exception as e:
            # Save errors are logged but NEVER propagate to the caller
            logger.error("Failed to persist TreatmentPlan: %s", e, exc_info=True)

    result = ChatResponse(
        answer=final_state.get("generation", "I could not generate an answer."),
        documents=final_state.get("documents", []),
        treatment_plan=generated_plan,
        plant_id=final_state.get("plant_id"),
        web_search_results=final_state.get("web_search_results", []),
        saved_plan_id=saved_plan_id,
    )
    return ApiResponse.success(result=result)
