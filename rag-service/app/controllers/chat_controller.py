from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse

from app.core.security import get_current_user, UserPrincipal
from app.dto.chat.chat_dto import ChatRequest, ChatResponse
from app.dto.response.api_response import ApiResponse
from app.services.chat_service import get_chat_service

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
    2. **Hybrid Search** — combines dense vector search with BM25 sparse retrieval.
    3. **Reranker** — cross-encoder reranking to surface the most relevant chunks.
    4. **Generation** — the LLM (Gemini / GPT) synthesises the final answer.
    5. **Self-correction** — if the answer is not grounded, the query is transformed
       and the graph retries (up to `max_retries` times).

    If the query is a treatment-plan request, the generated plan is automatically
    persisted to MongoDB (`leafy_rag.treatment_plans`).
    """
    service = get_chat_service()
    result = await service.run_chat(request, current_user)
    return ApiResponse.success(result=result)


@router.post(
    "/chat/stream",
    summary="Stream RAG state transitions and response chunks",
    responses={
        200: {
            "description": (
                "Server-Sent Events stream of RAG progress. "
                "Events include state updates (step/node), response chunks, and completion payload."
            )
        },
        401: {"description": "Authentication required."},
        500: {"description": "Internal error during graph execution."},
    },
)
async def chat_stream(
    request: ChatRequest,
    raw_request: Request,
    current_user: UserPrincipal = Depends(get_current_user),
):
    """
    Stream the RAG execution in real time using Server-Sent Events (SSE).

    Event stream contract:
    - `state`: graph progress update with `rag_state`, `step`, `current_node`, and compact state snapshot
    - `response_chunk`: incremental text chunks whenever generation text is produced
    - `completed`: final response payload compatible with `ChatResponse`
    - `error`: terminal failure event
    """
    service = get_chat_service()

    return StreamingResponse(
        service.stream_chat(request, raw_request, current_user),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
