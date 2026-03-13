"""
Treatment Planner Node

Generates a structured, chronological treatment schedule (TreatmentPlan)
for a diseased plant using retrieved medical protocols and Gemini Pro.

This is the final node in the pipeline — it enriches the text generation
with machine-readable structured data for direct consumption by a frontend
calendar / event scheduler.
"""

import logging
from datetime import date, timedelta

from app.agents.rag_state import GraphState
from app.core.ai_providers import get_gemini_pro
from app.schemas import TreatmentPlan, PlantEvent

logger = logging.getLogger(__name__)


def treatment_planner(state: GraphState) -> dict:
    """
    Generate a structured TreatmentPlan from retrieved medical documents.

    Uses Gemini Pro with structured output (via Pydantic) to produce a
    chronological list of PlantEvent objects that map directly to the
    PlantEvents table in the database.

    Post-processing calculates absolute ISO dates for each event based on
    `days_from_now` so the frontend can render a calendar immediately.

    Args:
        state: Current graph state. Reads `question`, `documents`,
               and optionally `safety_issues` for context.

    Returns:
        Updated state with:
          - `generated_plan`: dict (serialized TreatmentPlan incl. calculated dates)
          - `plant_id`: str extracted by the LLM from the question
    """
    question = state["question"]
    documents = state.get("documents", [])
    web_results = state.get("web_search_results") or []
    language = state.get("language", "English")

    logger.info("[TREATMENT PLANNER] Building structured treatment plan")

    # Short-circuit: need at least one knowledge source
    if not documents and not web_results:
        logger.warning("[TREATMENT PLANNER] No documents or web results available — skipping plan generation")
        return {"generated_plan": None, "plant_id": None}

    # --- Bind structured output schema to the model ---
    llm = get_gemini_pro(temperature=0)
    structured_llm = llm.with_structured_output(TreatmentPlan)

    # --- Build prompt ---
    docs_context = "\n---\n".join(
        f"Protocol {i + 1}:\n{doc.page_content}"
        for i, doc in enumerate(documents[:5])  # Use top 5 medical docs
    ) if documents else "(No internal knowledge base documents available for this query.)"

    # --- Web search context (Tavily) ---
    web_context = ""
    if web_results:
        web_lines = "\n---\n".join(
            f"Web Source {i + 1}: {r.get('title', 'Untitled')}\n"
            f"URL: {r.get('url', '')}\n"
            f"Content: {r.get('content', '')}"
            for i, r in enumerate(web_results[:5])
        )
        web_context = f"""

Web Sources (current regulations, local research, recent outbreak data):
{web_lines}"""
        logger.info("[TREATMENT PLANNER] Incorporating %d web sources into plan", len(web_results))

    # Append any safety constraints from previous checks
    safety_issues = state.get("safety_issues", [])
    safety_note = ""
    if safety_issues:
        issues_text = "\n".join(f"  - {issue}" for issue in safety_issues)
        safety_note = (
            f"\n\nSAFETY CONSTRAINTS — the following issues were flagged by the safety auditor. "
            f"Your plan MUST avoid these:\n{issues_text}"
        )

    # Format env_state context for the prompt
    env_state = state.get("env_state") or {}
    env_context = ""
    if env_state:
        soil = env_state.get("soil", {})
        weather = env_state.get("weather", {})
        gps = env_state.get("gps", {})
        env_context = f"""
Current Environmental Context (from IoT sensors — use this to adjust recommendations):
  Location  : lat={gps.get('latitude')}, lon={gps.get('longitude')}, altitude={gps.get('altitude_m')}m
  Soil      : pH={soil.get('ph')}, moisture={soil.get('moisture_pct')}%, temp={soil.get('temperature_c')}°C
              N={soil.get('nitrogen_ppm')}ppm, P={soil.get('phosphorus_ppm')}ppm, K={soil.get('potassium_ppm')}ppm
  Weather   : {weather.get('air_temp_c')}°C, humidity={weather.get('humidity_pct')}%, wind={weather.get('wind_speed_kmh')}km/h
              Rain last 7d={weather.get('rainfall_mm_last_7d')}mm, Rain forecast 24h={weather.get('forecast_rain_24h')}
  Note: If rain is forecast in the next 24h, DO NOT schedule spray events for today — reschedule to after the rain.
        If humidity > 80%, increase spray frequency or use contact fungicides.
"""

    system_prompt = f"""You are an Expert Agronomist Planner specializing in plant disease recovery.
Your task is to create a precise, actionable treatment schedule for a diseased plant.
{env_context}

EventType values and when to use them:
  Routine Care:
    IRRIGATION         — any watering task
    NUTRITION          — fertiliser / soil amendment (NOT medicine)
    WEED_CONTROL       — weeding or herbicide
    PRUNING            — structural or sanitary pruning / leaf removal
  Health & Medical:
    SCOUTING           — routine field inspection
    DISEASE_DETECTED   — first visual confirmation of disease/pest (use for day 0 diagnosis)
    TREATMENT_APPLICATION — curative spray, biocontrol agent, fungicide, etc.
    QUARANTINE         — isolate infected plant to prevent spread
    HEALTH_RECOVERY    — end-of-treatment health check (use for final recovery event)
  Growth & Lifecycle:
    PHENOLOGY          — record a growth stage milestone
    REPOT              — transplant to larger container or field
    HARVEST            — cherry picking event

Rules:
- First event: DISEASE_DETECTED, days_from_now: 0, isPlanned: false
- Final event: HEALTH_RECOVERY, isPlanned: true
- Immediate actions today → isPlanned: false
- All future scheduled actions → isPlanned: true
- Calculate `days_from_now` from the protocol timings
  (e.g. "repeat in 2 weeks" → second spray event has days_from_now = first_spray + 14)
- Be specific in `description`: include exact dosage, concentration, PPE, and application method.
- Extract the plant ID from the user query and set it in `plant_id`.
- For TREATMENT_APPLICATION events, you MUST populate these 3 dedicated fields:
    • `phi_days`     → integer — the Pre-Harvest Interval in days from the product label
                        (e.g. 7, 14, 21). Set to null for all other event types.
    • `ppe_required` → string — full PPE list required for the application
                        (e.g. "Respirator, chemical-resistant gloves, rubber boots, protective coveralls").
                        Set to null for all other event types.
    • `mrl_note`     → string — MRL compliance note for export/retail produce
                        (e.g. "Comply with EU MRL for Captan. Strict PHI adherence mandatory.").
                        Set to null for all other event types.
- Provide a realistic `estimated_cost` covering chemicals, tools, and effort required.
  (e.g., "$10-$20" or "500,000 VND").

CHEMICAL APPLICATION SAFETY — MANDATORY for every TREATMENT_APPLICATION event:
When the treatment involves chemical pesticides or fungicides, the `description` field MUST
include ALL of the following (the "4 Rights" / "4 Đúng" principle + regulatory requirements):

  1. RIGHT CHEMICAL (Đúng thuốc):
     - Name the exact registered product and active ingredient.
     - Confirm it targets the specific pest/disease.
     - State: "Only use chemicals on the approved list — banned substances are prohibited."

  2. RIGHT TIME (Đúng lúc):
     - Specify the optimal window (e.g. "Apply when larvae have just hatched" or
       "Apply at first sign of symptoms, before lesions spread").

  3. RIGHT DOSAGE (Đúng liều lượng):
     - Give the exact concentration from the label (e.g. "0.5% — 5 ml per litre of water").
     - Warn: "Do NOT under-dose (causes resistance) or over-dose (phytotoxicity / MRL violation)."

  4. RIGHT METHOD (Đúng cách):
     - Describe spraying technique and target zones
       (e.g. "Spray the underside of leaves and the stem base where pests hide").

  5. PRE-HARVEST INTERVAL — PHI (Thời gian cách ly):
     - State the mandatory withdrawal period before harvest
       (e.g. "Stop spraying at least 14 days before harvest so residues decompose fully").
     - This is the most critical food-safety requirement. NEVER omit it.

  6. PERSONAL PROTECTIVE EQUIPMENT — PPE (Bảo hộ lao động):
     - Always include: "Wear full PPE: respirator/mask, chemical-resistant gloves,
       rubber boots, and protective coveralls to prevent toxic exposure."

  7. MAXIMUM RESIDUE LIMIT — MRL (Dư lượng tối đa cho phép):
     - If produce targets export or premium retail, add:
       "Ensure residues stay within MRL limits of the target market (EU, USA, Japan, etc.).
       Strict PHI compliance is required."{safety_note}"""

    prompt = f"""{system_prompt}

Medical Protocols (from knowledge base):
{docs_context}{web_context}

User Query:
{question}

Generate the complete TreatmentPlan object now.
IMPORTANT: The entire output, including the plan description, notes, and ALL events MUST be detailed in the following language: {language}. Do not output English if {language} is different."""

    # --- Invoke structured LLM ---
    try:
        plan: TreatmentPlan = structured_llm.invoke(prompt)
    except Exception as e:
        logger.error("[TREATMENT PLANNER] Structured output failed: %s", e, exc_info=True)
        return {"generated_plan": None, "plant_id": None}

    # --- Post-processing: calculate absolute ISO dates ---
    today = date.today()
    final_plan = plan.dict()

    for event in final_plan["schedule"]:
        start = today + timedelta(days=event["days_from_now"])
        end = start + timedelta(days=max(0, event["duration_days"] - 1))
        event["calculated_start_date"] = start.isoformat()
        event["calculated_end_date"] = end.isoformat()

    # Sort schedule by days_from_now ascending (safety net)
    final_plan["schedule"].sort(key=lambda e: e["days_from_now"])

    # --- Build a flat text generation for safety auditors ---
    # Concatenates all event descriptions so dosage_auditor and regulatory_check
    # can inspect chemical content without knowing about the plan structure.
    generation_lines = [
        f"[{ev['eventType']}] {ev['note']}: {ev['description']}"
        for ev in final_plan["schedule"]
    ]
    generation_text = "\n\n".join(generation_lines)

    logger.info(
        "[TREATMENT PLANNER] Plan generated for plant_id=%s | disease=%s | events=%d | confidence=%.2f",
        final_plan.get("plant_id"),
        final_plan.get("disease_name"),
        len(final_plan["schedule"]),
        final_plan.get("confidence_score", 0.0),
    )

    return {
        "generated_plan": final_plan,
        "plant_id": final_plan.get("plant_id"),
        "generation": generation_text,   # Consumed by dosage_auditor + regulatory_check
    }
