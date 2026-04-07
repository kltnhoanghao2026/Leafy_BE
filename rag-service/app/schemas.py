from pydantic import BaseModel, Field
from typing import Optional, Dict, Any, List, Literal
from datetime import datetime
from enum import Enum


class TaskStatus(str, Enum):
    PENDING = "pending"
    PROCESSING = "processing"
    COMPLETED = "completed"
    FAILED = "failed"


class TaskSchema(BaseModel):
    task_id: str = Field(
        ...,
        description="Unique identifier for the background processing task.",
        examples=["3fa85f64-5717-4562-b3fc-2c963f66afa6"],
    )
    status: TaskStatus = Field(
        ...,
        description="Current lifecycle state of the task.",
    )
    created_at: datetime = Field(
        ...,
        description="UTC timestamp when the task was created.",
    )
    updated_at: datetime = Field(
        ...,
        description="UTC timestamp of the last status update.",
    )
    message: Optional[str] = Field(
        None,
        description="Human-readable status message or progress note.",
        examples=["Document processing completed successfully."],
    )
    file_info: Optional[Dict[str, Any]] = Field(
        None,
        description="Metadata extracted from the uploaded file (filename, content-type, category, variety, user_id …).",
        examples=[{"original_filename": "report.pdf", "content_type": "application/pdf", "category": "agronomy"}],
    )
    error: Optional[str] = Field(
        None,
        description="Error message if the task ended in a `failed` state.",
    )

    model_config = {
        "json_schema_extra": {
            "example": {
                "task_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                "status": "completed",
                "created_at": "2024-06-01T12:00:00Z",
                "updated_at": "2024-06-01T12:00:45Z",
                "message": "Document processing completed successfully.",
                "file_info": {
                    "original_filename": "plant-disease-report.pdf",
                    "content_type": "application/pdf",
                    "category": "agronomy",
                    "variety": "corn",
                    "user_id": "usr_001",
                },
                "error": None,
            }
        }
    }


class IngestResponse(BaseModel):
    task_id: str = Field(
        ...,
        description="UUID of the background processing task. Use this to poll `/tasks/{task_id}` for progress.",
        examples=["3fa85f64-5717-4562-b3fc-2c963f66afa6"],
    )
    status: str = Field(
        ...,
        description=(
            "`accepted` — document queued for processing. "
            "`skipped` — an identical document (same SHA-256 hash) already exists in the vector store."
        ),
        examples=["accepted"],
    )
    message: str = Field(
        ...,
        description="Human-readable summary of the ingestion outcome.",
        examples=["Document accepted for processing."],
    )
    file_id: Optional[str] = Field(
        None,
        description="SHA-256 content hash of the uploaded file. Doubles as the deduplication key.",
        examples=["e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"],
    )

    model_config = {
        "json_schema_extra": {
            "example": {
                "task_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                "status": "accepted",
                "message": "Document accepted for processing.",
                "file_id": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            }
        }
    }


# ── EventType Enum ────────────────────────────────────────────────────────────
# 1. Routine Care
#    IRRIGATION, NUTRITION, WEED_CONTROL, PRUNING
# 2. Health & Medical
#    SCOUTING, DISEASE_DETECTED, TREATMENT_APPLICATION, QUARANTINE, HEALTH_RECOVERY
# 3. Growth & Lifecycle
#    PHENOLOGY, REPOT, HARVEST
# ─────────────────────────────────────────────────────────────────────────────

EventTypeEnum = Literal[
    # --- Routine Care ---
    "IRRIGATION",           # Drip, sprinkler, or manual watering (measured)
    "NUTRITION",            # NPK, organic compost, foliar spray, lime
    "WEED_CONTROL",         # Manual weeding or herbicide application
    "PRUNING",              # Structural (stumping) or sanitary (dead branch removal)

    # --- Health & Medical ---
    "SCOUTING",             # Routine field check (may find nothing)
    "DISEASE_DETECTED",     # Visual confirmation of a symptom (Rust, Berry Borer, etc.)
    "TREATMENT_APPLICATION",# Curative action: spray fungicide, release beneficial insects
    "QUARANTINE",           # Isolate plant (nursery / pot) to prevent spread
    "HEALTH_RECOVERY",      # End of treatment cycle — plant declared recovered

    # --- Growth & Lifecycle ---
    "PHENOLOGY",            # Growth stage notes: Flowering, Pinhead, Fruit Filling, Ripening
    "REPOT",                # Move from nursery bag to larger pot or field
    "HARVEST",              # Cherry picking — used for totalYieldKg tracking
]

TreatmentPlanSourceEnum = Literal["websearch", "documents"]


class PlantEvent(BaseModel):
    """A single scheduled action in the treatment plan.

    Field names are camelCase to match plant-management-service
    PlantEventCreateRequest DTO exactly.
    """

    eventType: EventTypeEnum = Field(
        ...,
        description=(
            "Category of the action. Guidelines:\n"
            "• IRRIGATION — any watering task\n"
            "• NUTRITION — fertiliser / soil amendment (NOT the same as medicine)\n"
            "• WEED_CONTROL — weeding or herbicide\n"
            "• PRUNING — structural or sanitary pruning\n"
            "• SCOUTING — routine inspection (result may be 'clean')\n"
            "• DISEASE_DETECTED — first visual confirmation of a disease/pest\n"
            "• TREATMENT_APPLICATION — curative spray, biocontrol, etc.\n"
            "• QUARANTINE — isolate infected plant\n"
            "• HEALTH_RECOVERY — marks end of treatment cycle\n"
            "• PHENOLOGY — record a growth stage milestone\n"
            "• REPOT — transplant to larger container or field\n"
            "• HARVEST — cherry picking event"
        ),
    )
    daysFromNow: int = Field(
        ...,
        description=(
            "Offset in days from today: 0 = today, 1 = tomorrow, 14 = two weeks, etc. "
            "Calculate gaps from the protocol timing "
            "(e.g. 'repeat in 2 weeks' → second event has daysFromNow = first + 14)."
        ),
    )
    durationDays: int = Field(
        default=1,
        description="How many calendar days the task spans. Usually 1 for discrete actions.",
    )
    note: str = Field(
        ...,
        description="Short title / label for the event, e.g. 'Copper Fungicide Spray'.",
    )
    description: str = Field(
        ...,
        description=(
            "Detailed instructions: dosage, method, and safety precautions. "
            "Be specific — e.g. 'Apply Captan 50 WP at 0.5% concentration. "
            "Wear PPE. Ensure full leaf coverage. Avoid spraying in direct sunlight.'"
        ),
    )
    isPlanned: bool = Field(
        ...,
        description="True if this is a future scheduled event. False if it should happen immediately (today).",
    )
    # ── Chemical application safety fields (TREATMENT_APPLICATION only) ───────
    phiDays: Optional[int] = Field(
        default=None,
        description=(
            "Pre-Harvest Interval in days (Thời gian cách ly). "
            "REQUIRED for TREATMENT_APPLICATION events — the minimum number of days "
            "that must pass between the last spray and harvest "
            "(e.g. 7, 14, 21). Leave null for non-chemical events."
        ),
    )
    ppeRequired: Optional[str] = Field(
        default=None,
        description=(
            "Personal Protective Equipment requirements (Bảo hộ lao động). "
            "REQUIRED for TREATMENT_APPLICATION events. "
            "List all mandatory PPE, e.g. "
            "'Respirator/mask, chemical-resistant gloves, rubber boots, protective coveralls'. "
            "Leave null for non-chemical events."
        ),
    )
    mrlNote: Optional[str] = Field(
        default=None,
        description=(
            "Maximum Residue Limit note (Dư lượng tối đa cho phép — MRL). "
            "REQUIRED for TREATMENT_APPLICATION events when produce may enter "
            "export or premium retail channels. "
            "State the relevant MRL standard, e.g. "
            "'Comply with EU MRL for this active ingredient. Strict PHI adherence is mandatory.' "
            "Leave null for non-chemical events."
        ),
    )
    estimatedCost: Optional[str] = Field(
        default=None,
        description=(
            "Estimated cost for this specific event (chemicals, labour). "
            "e.g. '200,000 VND' or '$5–$10'. Leave null if unknown."
        ),
    )
    # ── Scope fields (set by caller, not by LLM) ─────────────────────────────
    farmPlotId: Optional[str] = Field(
        default=None,
        description="Farm plot ID this event belongs to. Populated by the caller from plant context.",
    )
    farmZoneId: Optional[str] = Field(
        default=None,
        description="Farm zone ID this event belongs to. Populated by the caller from plant context.",
    )
    # ── Source tracking (filled after plan is persisted to MongoDB) ───────────
    sourcePlanId: Optional[str] = Field(
        default=None,
        description="ID of the TreatmentPlan MongoDB document that generated this event.",
    )


class TreatmentPlan(BaseModel):
    """Structured recovery plan for a diseased plant.

    Field names are camelCase to match the plant-management-service TreatmentPlan
    MongoDB model and PlantEventCreateRequest DTO.
    """

    # --- Identity ---
    plantId: str = Field(
        ...,
        description="The ID of the plant extracted from the user query.",
    )
    diseaseName: str = Field(
        ...,
        description="Full name of the identified disease, e.g. 'Brown Eye Spot (Cercospora coffeicola)'.",
    )

    # --- Assessment ---
    confidenceScore: float = Field(
        ...,
        description="Confidence in this diagnosis and plan (0.0 = uncertain, 1.0 = certain).",
    )
    severityLevel: str = Field(
        ...,
        description=(
            "Severity of the infection: "
            "'LOW' — cosmetic damage only, no yield impact; "
            "'MEDIUM' — yield risk if untreated; "
            "'HIGH' — risk of plant death or total crop loss."
        ),
    )
    urgency: str = Field(
        ...,
        description=(
            "Time sensitivity of the first action: "
            "'IMMEDIATE' — must act within 24 hours; "
            "'HIGH' — act within 3 days; "
            "'NORMAL' — can wait until within the week."
        ),
    )
    source: Optional[TreatmentPlanSourceEnum] = Field(
        default=None,
        description=(
            "Primary evidence source used when generating this plan. "
            "`documents` means internal vector-store retrieval and "
            "`websearch` means external web-search-assisted retrieval."
        ),
    )

    # --- Farm scope (optional — populated by caller, not the LLM) ---
    farmPlotId: Optional[str] = Field(
        default=None,
        description="Farm plot this plan is scoped to. Set by caller from plant context.",
    )
    farmZoneId: Optional[str] = Field(
        default=None,
        description="Farm zone this plan is scoped to. Set by caller from plant context.",
    )

    # --- Action Plan ---
    schedule: List[PlantEvent] = Field(
        ...,
        description="Chronological list of PlantEvent items ordered by daysFromNow (ascending).",
    )

    # --- Resources & Safety ---
    requiredInputs: List[str] = Field(
        default=[],
        description=(
            "All tools, materials, and chemicals needed to execute the full plan. "
            "e.g. ['Captan 50 WP fungicide', 'Knapsack sprayer', 'Pruning shears', 'PPE kit']"
        ),
    )
    safetyWarnings: List[str] = Field(
        default=[],
        description=(
            "Critical safety notes applicable to the whole plan. "
            "e.g. ['Wear full PPE during all spray events', "
            "'PHI: stop spraying 14 days before harvest', "
            "'Product is toxic to aquatic life — avoid spray near water sources']"
        ),
    )

    # --- Outcome Tracking ---
    successIndicators: str = Field(
        ...,
        description=(
            "Visual or measurable signs that the treatment is working. "
            "e.g. 'Fungal spots stop spreading and turn grey/dry within 7 days. "
            "No new lesions visible after second spray. Leaf colour returns to healthy green.'"
        ),
    )
    estimatedCost: Optional[str] = Field(
        default=None,
        description=(
            "Estimated cost range or specific amount for the entire treatment plan, "
            "including materials, chemicals, and labor (e.g., '$50-$100', '1.5M VND')."
        ),
    )


