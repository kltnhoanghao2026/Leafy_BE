import os
import logging
import logging.config
from dotenv import load_dotenv

# Load .env FIRST — must happen before any LangChain/LangSmith imports
# so that LANGCHAIN_TRACING_V2 and related vars are present in the environment.
load_dotenv()

logging.config.dictConfig({
    "version": 1,
    "disable_existing_loggers": False,
    "formatters": {
        "default": {
            "format": "%(levelname)-8s %(name)s - %(message)s",
        },
    },
    "handlers": {
        "console": {
            "class": "logging.StreamHandler",
            "formatter": "default",
            "stream": "ext://sys.stdout",
        },
    },
    "root": {
        "handlers": ["console"],
        "level": "INFO",
    },
    "loggers": {
        "httpx":               {"level": "WARNING", "propagate": True},
        "httpcore":            {"level": "WARNING", "propagate": True},
        "langchain":           {"level": "WARNING", "propagate": True},
        "langchain_core":      {"level": "WARNING", "propagate": True},
        "openai":              {"level": "WARNING", "propagate": True},
        "sentence_transformers": {"level": "WARNING", "propagate": True},
        "fastembed":           {"level": "WARNING", "propagate": True},
        "qdrant_client":       {"level": "WARNING", "propagate": True},
    },
})

from fastapi import FastAPI
from contextlib import asynccontextmanager
from pydantic import BaseModel, Field

from app.core.security import SecurityContextMiddleware
from app.exceptions.global_exception_handler import register_exception_handlers
from app.controllers.chat_controller import router as chat_router
from app.controllers.ingestion_controller import router as ingestion_router
from app.controllers.conversation_controller import router as conversation_router
from app.controllers.plan_generation_controller import router as plan_generation_router
from app.controllers.chunks_controller import router as chunks_router
import app.agents.rag_agent as rag_agent_module
import app.agents.plan_agent as plan_agent_module
from langgraph.checkpoint.sqlite.aio import AsyncSqliteSaver

import asyncio
import shutil
import uuid
from pathlib import Path

from app.utils.file_utils import calculate_file_hash
from app.services.vector_db import get_vector_service
from app.services.task_manager import get_task_manager
from app.workers.document import process_document

logger = logging.getLogger(__name__)

# ── LangSmith tracing confirmation ──────────────────────────────────────────
_tracing_enabled = os.getenv("LANGCHAIN_TRACING_V2", "false").lower() == "true"
_project = os.getenv("LANGCHAIN_PROJECT", "default")
if _tracing_enabled:
    logger.info("LangSmith tracing ENABLED — project: %s", _project)
else:
    logger.warning("LangSmith tracing is DISABLED (LANGCHAIN_TRACING_V2 != true)")
# ────────────────────────────────────────────────────────────────────────────

async def ingest_knowledge_examples():
    """Auto-ingest markdown documents from knowledge-example folder at startup."""
    knowledge_dir = Path("knowledge-example")
    upload_dir = Path("uploads")
    upload_dir.mkdir(exist_ok=True)
    
    if not knowledge_dir.exists():
        logger.warning("knowledge-example directory not found, skipping pre-ingestion.")
        return

    try:
        vector_service = get_vector_service()
        task_manager = get_task_manager()
    except Exception as e:
        logger.warning("Could not initialize vector_service or task_manager: %s", e)
        return

    for file_path in knowledge_dir.glob("*.md"):
        try:
            file_hash = await calculate_file_hash(file_path)
            if vector_service.check_existing_hash(file_hash):
                logger.info("File %s already ingested, skipping.", file_path.name)
                continue
                
            logger.info("Auto-ingesting knowledge file: %s", file_path.name)
            task_id = str(uuid.uuid4())
            
            # Create a copy since process_document removes it after completion
            temp_file_path = upload_dir / f"{task_id}_{file_path.name}"
            shutil.copy2(file_path, temp_file_path)
            
            metadata = {
                "original_filename": file_path.name,
                "content_type": "text/markdown",
                # No user_id → treated as public knowledge base doc,
                # visible to ALL users via the IsEmptyCondition filter in hybrid_search.
                "category": "agronomy",
                "variety": "coffee"
            }
            task_manager.create_task(task_id, file_info=metadata)
            
            asyncio.create_task(process_document(temp_file_path, metadata, file_hash, task_id))
        except Exception as e:
            logger.error("Error auto-ingesting %s: %s", file_path.name, e)

@asynccontextmanager
async def lifespan(fastapi_app: FastAPI):
    """
    FastAPI lifespan handler.

    Initialises the AsyncSqliteSaver inside its own async context manager so the
    underlying aiosqlite connection stays open for the entire lifetime of the service.
    The compiled LangGraph is stored back onto the rag_agent module so all
    coroutines that import `rag_agent_module.rag_app` see the live graph.
    """
    db_path = os.getenv("CHECKPOINT_DB_PATH", "checkpoints.db")
    logger.info("[STARTUP] Initialising AsyncSqliteSaver at '%s'", db_path)
    async with AsyncSqliteSaver.from_conn_string(db_path) as checkpointer:
        rag_agent_module.rag_app = rag_agent_module.build_graph(checkpointer)
        plan_agent_module.plan_app = plan_agent_module.build_graph(checkpointer)
        logger.info("[STARTUP] LangGraph RAG pipeline ready")
        
        # Run auto-ingestion for knowledge examples
        await ingest_knowledge_examples()
        
        yield
    # Connection is closed automatically when the async context exits on shutdown
    rag_agent_module.rag_app = None
    plan_agent_module.plan_app = None
    logger.info("[SHUTDOWN] AsyncSqliteSaver connection closed")

tags_metadata = [
    {
        "name": "Ingestion",
        "description": (
            "Endpoints for uploading and processing documents into the vector store. "
            "Supports **PDF**, **DOCX**, and **plain-text** files up to 20 MB. "
            "Processing runs asynchronously — use the `/tasks` endpoints to track progress."
        ),
    },
    {
        "name": "Chat",
        "description": (
            "RAG-powered conversational endpoint. Queries are routed through a "
            "LangGraph state-machine that runs hybrid search, "
            "reranking, and self-correction before generating a grounded answer."
        ),
    },
    {
        "name": "Treatment Plans",
        "description": "CRUD endpoints for persisted Plan documents.",
    },
    {
        "name": "Conversations",
        "description": "List/load/manage persisted chat conversations.",
    },
    {
        "name": "Health",
        "description": "Service liveness and observability checks.",
    },
]

app = FastAPI(
    title="RAG Service API",
    lifespan=lifespan,
    description=(
        "## Overview\n"
        "A production-grade **Retrieval-Augmented Generation** service built with "
        "[FastAPI](https://fastapi.tiangolo.com) and [LangGraph](https://langchain-ai.github.io/langgraph/).\n\n"
        "### Architecture\n"
        "```\n"
        "Client ──► /ingest  ──► [Parse → Chunk → Embed] ──► Qdrant Vector DB\n"
        "Client ──► /chat    ──► LangGraph Pipeline\n"
        "                          ├── Hybrid Search (Dense + BM25)\n"
        "                          ├── Cross-Encoder Reranking\n"
        "                          └── Grounded Generation (Gemini / GPT)\n"
        "                              └── Plan → MongoDB (leafy_rag)\n"
        "```\n\n"
        "### Authentication\n"
        "All endpoints require `X-User-Id` and `X-User-Email` headers (set by the API Gateway).\n\n"
        "### Supported File Types\n"
        "| Format | MIME Type |\n"
        "|--------|----------|\n"
        "| PDF    | `application/pdf` |\n"
        "| DOCX   | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |\n"
        "| TXT    | `text/plain` |\n"
        "| Markdown | `text/markdown` |\n"
    ),
    version="2.0.0",
    contact={
        "name": "RAG Service Team",
        "url": "https://github.com/your-org/rag-service",
        "email": "support@example.com",
    },
    license_info={"name": "MIT", "url": "https://opensource.org/licenses/MIT"},
    openapi_tags=tags_metadata,
    docs_url="/docs",
    redoc_url="/redoc",
    openapi_url="/v3/api-docs",  # Matches Spring Boot API Gateway swagger expectations
)

# ── Middleware ───────────────────────────────────────────────────────────────
app.add_middleware(SecurityContextMiddleware)

# ── Exception Handlers ───────────────────────────────────────────────────────
register_exception_handlers(app)

# ── Routers ──────────────────────────────────────────────────────────────────
app.include_router(ingestion_router, prefix="/rag/v1", tags=["Ingestion"])
app.include_router(chat_router, prefix="/rag/v1", tags=["Chat"])
app.include_router(chunks_router, prefix="/rag/v1", tags=["Chunks"])
app.include_router(plan_generation_router, prefix="/rag/v2/plans", tags=["Treatment Plans"])
app.include_router(conversation_router, prefix="/rag/v1/conversations", tags=["Conversations"])


# ── Health Check ─────────────────────────────────────────────────────────────
class HealthResponse(BaseModel):
    status: str = Field("ok")
    langsmith_tracing: bool = Field(...)
    langsmith_project: str = Field(...)


@app.get("/health", tags=["Health"])
async def k8s_health_check():
    """Kubernetes readiness/liveness probe endpoint."""
    return {"status": "UP"}


@app.get("/rag/health", tags=["Health"], response_model=HealthResponse, summary="Service health check")
async def health_check():
    """Returns liveness status and LangSmith tracing configuration."""
    return HealthResponse(
        status="ok",
        langsmith_tracing=_tracing_enabled,
        langsmith_project=_project,
    )

@app.get("/actuator/health", tags=["Health"], summary="Eureka health check")
async def eureka_health_check():
    """Returns status UP for Eureka."""
    return {"status": "UP"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8199)
