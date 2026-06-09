from typing import List, Optional, Dict, Any
from pydantic import BaseModel, Field


class PlanGenerationRequest(BaseModel):
    disease_name: str = Field(
        ...,
        description="The name of the detected disease.",
        examples=["Coffee Leaf Rust"],
    )
    severity_level: Optional[str] = Field(
        None,
        description="Severity level of the disease: LOW | MEDIUM | HIGH.",
        examples=["MEDIUM"],
    )
    plant_id: Optional[str] = Field(
        None,
        description="Optional Plant ID associated with this disease.",
        examples=["plant123"],
    )
    farm_plot_id: Optional[str] = Field(
        None,
        description="Farm plot ID to use for IoT environmental context.",
        examples=["abc123def456abc123def456"],
    )
    farm_zone_id: Optional[str] = Field(
        None,
        description="Farm zone ID to use for IoT environmental context.",
        examples=["abc123def456abc123def456"],
    )
    language: Optional[str] = Field(
        "Vietnamese",
        description="The language for the generated plan.",
        examples=["Vietnamese", "English"],
    )
    image_url: Optional[str] = Field(
        None,
        description="Presigned URL of the disease image for severity assessment.",
    )
    include_web_search: bool = Field(
        True,
        description=(
            "Whether to invoke web search when reranked documents are insufficient. "
            "When False, only the knowledge-base retrieval path is used."
        ),
    )


class PlanSource(BaseModel):
    title: str
    content: str
    url: Optional[str] = None
    score: float = Field(ge=0.0, le=1.0)


class PlanMetadata(BaseModel):
    disease_type: str = Field(description="'fungal' | 'insect' | 'mite' | 'unknown'")
    best_rerank_score: float = Field(ge=0.0, le=1.0)
    web_search_used: bool
    web_sources_count: int = 0
    refinement_attempts: int = 0
    safety_passed: bool = True
    # Policy fields (populated for the 4 supported diseases)
    policy_key: Optional[str] = Field(
        None,
        description="Key in DISEASE_POLICIES: 'phoma' | 'rust' | 'miner' | 'red_spider_mite'",
    )
    preferred_spray_interval_days: Optional[List[int]] = Field(
        None,
        description="Preferred spray interval [min_days, max_days], e.g. [7, 14]",
    )
    frac_rotation_required: Optional[bool] = Field(
        None,
        description="Whether FRAC fungicide rotation is required for this disease",
    )
    humidity_sensitive: Optional[bool] = Field(
        None,
        description="Whether humidity strongly influences disease spread",
    )


class PlanGenerationResponse(BaseModel):
    plan: Dict[str, Any] = Field(description="The full treatment plan dict")
    documents: List[PlanSource] = Field(default_factory=list)
    web_sources: List[PlanSource] = Field(default_factory=list)
    metadata: PlanMetadata
    saved_plan_id: Optional[str] = Field(
        None,
        description=(
            "The ID of the plan persisted in plant-management-service. "
            "Present when the plan was successfully saved; null if save failed (plan "
            "generation itself still succeeded — save failure is non-fatal)."
        ),
    )
    rag_plan_id: Optional[str] = Field(
        None,
        description=(
            "The ID of the plan persisted in rag-service's own MongoDB. "
            "Useful for retrieving the plan via GET /rag/v2/plans/{planId} "
            "without going through plant-management-service."
        ),
    )
