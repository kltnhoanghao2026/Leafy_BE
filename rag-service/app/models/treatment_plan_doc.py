import uuid
import logging
from datetime import datetime, timezone
from typing import Optional, Dict, Any, List, Literal
from pydantic import BaseModel, Field

logger = logging.getLogger(__name__)


class TreatmentPlanDoc(BaseModel):
    """
    MongoDB document model for a persisted TreatmentPlan.
    Mirrors the pattern used by DiagnoseRequest / DiagnoseResult in disease-detection-service.
    """

    planId: str = Field(default_factory=lambda: str(uuid.uuid4()))
    userId: str = Field(..., description="ID of the user who triggered the plan generation.")
    question: str = Field(..., description="Original user question that produced this plan.")
    plantId: Optional[str] = Field(None, description="Plant ID extracted from the TreatmentPlan.")
    diseaseName: Optional[str] = Field(None, description="Disease name from the TreatmentPlan.")
    severityLevel: Optional[str] = Field(None)
    urgency: Optional[str] = Field(None)
    source: Optional[Literal["websearch", "documents"]] = Field(
        None,
        description=(
            "Primary evidence source used when creating this plan. "
            "Accepted values: `websearch`, `documents`."
        ),
    )
    plan: Dict[str, Any] = Field(..., description="Full serialized TreatmentPlan object.")
    source_documents: Optional[List[Dict[str, Any]]] = Field(
        None,
        description=(
            "Top-k reranked document chunks from the Qdrant vector store that were "
            "used as evidence to generate this treatment plan. Each entry contains "
            "'page_content' and 'metadata' keys."
        ),
    )
    web_search_results: Optional[List[Dict[str, Any]]] = Field(
        None,
        description=(
            "External Tavily web search results used to supplement the vector store "
            "retrieval (deep path only). Each entry contains 'title', 'url', "
            "'content', and 'score' keys."
        ),
    )
    createdAt: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
