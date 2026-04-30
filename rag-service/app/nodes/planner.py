"""
General Planner Node

Generates a structured, chronological agronomic action schedule (Plan)
for coffee operations including routine care, treatment, pruning/denoting,
and seasonal field activities using retrieved knowledge and Gemini Pro.

This is the final node in the pipeline - it enriches the text generation
with machine-readable structured data for direct consumption by a frontend
calendar / event scheduler.
"""

import logging
from datetime import date, timedelta

from langchain_core.messages import AIMessage

from app.agents.rag_state import GraphState
from app.core.ai_providers import get_gemini_pro
from app.schemas import Plan

logger = logging.getLogger(__name__)


def planner(state: GraphState) -> dict:
    """
    Generate a structured Plan from retrieved agronomic documents.

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
          - `generated_plan`: dict (serialized Plan incl. calculated dates)
          - `plant_id`: str extracted by the LLM from the question
    """
    question = state["question"]
    documents = state.get("documents", [])
    web_results = state.get("web_search_results") or []
    language = state.get("language", "English")
    refinement_guidance = (state.get("refinement_guidance") or "").strip()

    logger.info("[GENERAL PLANNER] Building structured agronomic plan")

    if not documents and not web_results:
        logger.warning("[GENERAL PLANNER] No documents or web results available - skipping plan generation")
        return {"generated_plan": None, "plant_id": None}

    llm = get_gemini_pro(temperature=0)
    structured_llm = llm.with_structured_output(Plan)

    docs_context = "\n---\n".join(
        f"Agronomic Source {i + 1}:\n{doc.page_content}"
        for i, doc in enumerate(documents[:5])
    ) if documents else "(No internal knowledge base documents available for this query.)"

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
        logger.info("[GENERAL PLANNER] Incorporating %d web sources into plan", len(web_results))

    safety_issues = state.get("safety_issues", [])
    safety_note = ""
    if safety_issues:
        issues_text = "\n".join(f"  - {issue}" for issue in safety_issues)
        safety_note = (
            "\n\nSAFETY CONSTRAINTS - the following issues were flagged by the safety auditor. "
            f"Your plan MUST avoid these:\n{issues_text}"
        )
        if refinement_guidance:
            safety_note += f"\n\nACTIONABLE REFINEMENT GUIDANCE:\n{refinement_guidance}"

    export_context_keywords = [
        "export", "xuat khau", "eu", "usda", "japan", "premium retail", "international",
        "rainforest alliance", "4c", "mrl", "residue",
    ]
    export_context = any(k in question.lower() for k in export_context_keywords)

    env_state = state.get("env_state") or {}
    env_context = ""
    if env_state:
        soil = env_state.get("soil", {})
        weather = env_state.get("weather", {})
        gps = env_state.get("gps", {})
        env_context = f"""
Current Environmental Context (from IoT sensors - use this to adjust recommendations):
  Location  : lat={gps.get('latitude')}, lon={gps.get('longitude')}, altitude={gps.get('altitude_m')}m
  Soil      : pH={soil.get('ph')}, moisture={soil.get('moisture_pct')}%, temp={soil.get('temperature_c')}C
              N={soil.get('nitrogen_ppm')}ppm, P={soil.get('phosphorus_ppm')}ppm, K={soil.get('potassium_ppm')}ppm
  Weather   : {weather.get('air_temp_c')}C, humidity={weather.get('humidity_pct')}%, wind={weather.get('wind_speed_kmh')}km/h
              Rain last 7d={weather.get('rainfall_mm_last_7d')}mm, Rain forecast 24h={weather.get('forecast_rain_24h')}
  Note: If rain is forecast in the next 24h, DO NOT schedule spray events for today - reschedule to after the rain.
        If humidity > 80%, increase scouting frequency and adjust fungal prevention timing.
"""

    system_prompt = f"""You are an Expert Agronomist Planner for coffee cultivation operations.
Your task is to create a precise, actionable general plan that can include:
- routine care (irrigation, nutrition, scouting),
- treatment actions (including pesticide/fungicide when truly needed),
- pruning/denoting/stumping/canopy-reset tasks,
- field maintenance and lifecycle events.

The plan must match user intent. Do NOT force disease treatment when the user only asks for care/maintenance.
{env_context}

EventType values and when to use them:
  Routine Care:
    IRRIGATION         - any watering task
    NUTRITION          - fertiliser / soil amendment (NOT medicine)
    WEED_CONTROL       - weeding, cleanup, ground management, herbicide when justified
    PRUNING            - structural/sanitary pruning and denoting/stumping/canopy reset tasks
  Health & Medical:
    SCOUTING           - routine field inspection / monitoring
    DISEASE_DETECTED   - visual confirmation of disease/pest (only when relevant)
    TREATMENT_APPLICATION - curative spray, biocontrol agent, fungicide, etc.
    QUARANTINE         - isolate infected plant to prevent spread
    HEALTH_RECOVERY    - end-of-treatment health check (only when treatment path is used)
  Growth & Lifecycle:
    PHENOLOGY          - record a growth stage milestone
    REPOT              - transplant to larger container or field
    HARVEST            - cherry picking event

Rules:
- Build a plan type that matches user intent: CARE / TREATMENT / MIXED.
- CARE plans should emphasize IRRIGATION, NUTRITION, SCOUTING, WEED_CONTROL, PRUNING.
- TREATMENT plans should include DISEASE_DETECTED and HEALTH_RECOVERY when disease/pest context is explicit.
- MIXED plans may combine routine care with treatment and pruning/denoting actions.
- Do not include DISEASE_DETECTED or HEALTH_RECOVERY if there is no disease/pest context.
- Immediate actions today -> isPlanned: false
- All future scheduled actions -> isPlanned: true
- Calculate `daysFromNow` from the protocol timings
  (e.g. "repeat in 2 weeks" -> second spray event has daysFromNow = first_spray + 14)
- Be specific in `description`: include exact dosage, concentration, PPE, and method when chemical treatment is involved.
- Extract the plant ID from the user query and set it in `plantId`.
- This schema requires `diseaseName`. If this is NOT a disease-specific request, set:
    diseaseName = "General Plant Care"
- For TREATMENT_APPLICATION events, you MUST populate these dedicated fields:
    * `phiDays` -> integer - the Pre-Harvest Interval in days from the product label
                        (e.g. 7, 14, 21). Set to null for all other event types.
    * `ppeRequired` -> string - full PPE list required for the application
                        (e.g. "Respirator, chemical-resistant gloves, rubber boots, protective coveralls").
                        Set to null for all other event types.
    * `mrlNote` -> string - required ONLY when produce targets export/premium retail
                        channels or user explicitly asks for MRL/compliance details.
                        (e.g. "Comply with EU MRL for Captan. Strict PHI adherence mandatory.").
                        If EXPORT_CONTEXT_DETECTED is false, set to null unless a warning is still useful.
- Provide a realistic `estimated_cost` covering chemicals, tools, and effort required.
  (e.g., "$10-$20" or "500,000 VND").
- For pruning/denoting tasks, include clear cutback intensity and sanitation workflow
  (tool disinfection, debris handling, follow-up scouting).

EXPORT_CONTEXT_DETECTED={export_context}

CHEMICAL APPLICATION SAFETY - MANDATORY for every TREATMENT_APPLICATION event:
When the treatment involves chemical pesticides or fungicides, the `description` field MUST
include ALL of the following (the "4 Rights" principle + regulatory requirements):

  1. RIGHT CHEMICAL:
     - Name the exact registered product and active ingredient.
     - Confirm it targets the specific pest/disease.
     - State: "Only use chemicals on the approved list - banned substances are prohibited."

  2. RIGHT TIME:
     - Specify the optimal window.

  3. RIGHT DOSAGE:
     - Give the exact concentration from the label.
     - Warn: "Do NOT under-dose (causes resistance) or over-dose (phytotoxicity / MRL violation)."

  4. RIGHT METHOD:
     - Describe spraying technique and target zones.

  5. PRE-HARVEST INTERVAL (PHI):
     - State the mandatory withdrawal period before harvest.

  6. PERSONAL PROTECTIVE EQUIPMENT (PPE):
     - Include full PPE requirements.

  7. MAXIMUM RESIDUE LIMIT (MRL):
     - If produce targets export or premium retail, include explicit MRL compliance notes.{safety_note}"""

    prompt = f"""{system_prompt}

Agronomic Knowledge (from knowledge base):
{docs_context}{web_context}

User Query:
{question}

Generate the complete Plan object now.
IMPORTANT: The entire output, including plan descriptions, notes, and ALL events MUST be detailed in the following language: {language}."""

    try:
        plan: Plan = structured_llm.invoke(prompt)
    except Exception as e:
        logger.error("[GENERAL PLANNER] Structured output failed: %s", e, exc_info=True)
        return {"generated_plan": None, "plant_id": None}

    today = date.today()
    final_plan = plan.dict()

    schedule = final_plan.get("schedule") or []
    if not schedule:
        logger.warning("[GENERAL PLANNER] Empty schedule generated - skipping plan")
        return {"generated_plan": None, "plant_id": None}

    for event in schedule:
        start = today + timedelta(days=event["daysFromNow"])
        end = start + timedelta(days=max(0, event["durationDays"] - 1))
        event["calculatedStartDate"] = start.isoformat()
        event["calculatedEndDate"] = end.isoformat()

    if web_results:
        final_plan["source"] = "websearch"
    elif documents:
        final_plan["source"] = "documents"

    if not final_plan.get("diseaseName"):
        final_plan["diseaseName"] = "General Plant Care"

    if not final_plan.get("urgency"):
        final_plan["urgency"] = "NORMAL"

    schedule.sort(key=lambda e: e["daysFromNow"])

    generation_lines = [
        f"[{ev['eventType']}] {ev['note']}: {ev['description']}"
        for ev in schedule
    ]
    generation_text = "\n\n".join(generation_lines)

    logger.info(
        "[GENERAL PLANNER] Plan generated for plantId=%s | objective=%s | source=%s | events=%d | confidence=%.2f",
        final_plan.get("plantId"),
        final_plan.get("diseaseName"),
        final_plan.get("source"),
        len(schedule),
        final_plan.get("confidenceScore", 0.0),
    )

    return {
        "generated_plan": final_plan,
        "plant_id": final_plan.get("plantId"),
        "generation": generation_text,
        "messages": [AIMessage(content=generation_text)],
    }


# Backward-compatible alias for existing imports.
planner = planner
