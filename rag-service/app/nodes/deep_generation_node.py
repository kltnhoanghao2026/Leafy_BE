"""
Deep Generation Node

Uses Gemini 1.5 Pro for complex reasoning and research when
internal documents are insufficient. Combines internal docs with
web search results for comprehensive answers.
"""

import logging
from datetime import datetime
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_core.output_parsers import StrOutputParser
from langchain_core.messages import AIMessage, SystemMessage

from app.agents.rag_state import GraphState
from app.core.ai_providers import get_gemini_pro

logger = logging.getLogger(__name__)

# Maximum number of previous message pairs (human + AI) to keep in history.
_MAX_HISTORY_PAIRS = 5


def deep_generation(state: GraphState) -> dict:
    """
    Generate comprehensive answer using Gemini Pro (deep path).
    
    Used when internal documents have low confidence/completeness.
    Combines internal knowledge with web search results and environmental
    data for thorough, research-backed responses.
    
    Args:
        state: Current graph state with documents, web_search_results, and env_state
        
    Returns:
        Updated state with generation
    """
    logger.info("[DEEP GEN] Generating answer with Gemini Pro (%d internal docs, %d web results)",
                len(state.get('documents', [])), len(state.get('web_search_results', [])))
    
    question = state["question"]
    documents = state.get("documents", [])
    web_results = state.get("web_search_results", [])
    safety_issues = state.get("safety_issues", [])
    refinement_count = state.get("refinement_count", 0)
    refinement_guidance = state.get("refinement_guidance", "")
    env_state = state.get("env_state", {})
    language = state.get("language", "English")
    # When a clarification round-trip occurred, original_question holds the
    # intent-bearing query while `question` is only the clarifying reply.
    original_question = (state.get("original_question") or "").strip()

    # Build history: all messages except the last one (the current HumanMessage).
    # Truncate to the last _MAX_HISTORY_PAIRS * 2 messages to control token budget.
    all_messages = state.get("messages", [])
    history = all_messages[:-1] if len(all_messages) > 1 else []
    history = history[-(_MAX_HISTORY_PAIRS * 2):]

    # Prepend a rolling summary as a SystemMessage when the history was trimmed
    summary = state.get("summary")
    if summary:
        history = [SystemMessage(content=f"[Earlier conversation summary]: {summary}")] + history
    
    # Build comprehensive context
    context_parts = []
    
    # Add environmental state first (from IoT sensors)
    if env_state:
        soil = env_state.get("soil", {})
        weather = env_state.get("weather", {})
        gps = env_state.get("gps", {})
        farm_info = env_state.get("farm_info", {})
        farm_lines = ""
        if farm_info:
            farm_lines = (
                f"Farm plot  : {farm_info.get('plot_name') or 'N/A'} (code: {farm_info.get('plot_code') or 'N/A'})"
                f", area={farm_info.get('plot_area_m2') or 'N/A'}m², address={farm_info.get('plot_address') or 'N/A'}\n            "
                f"Zone       : {farm_info.get('zone_name') or 'N/A'} (code: {farm_info.get('zone_code') or 'N/A'})"
                f", area={farm_info.get('zone_area_m2') or 'N/A'}m², soil_type={farm_info.get('soil_type') or 'N/A'}"
                f", crop_type={farm_info.get('crop_type') or 'N/A'}\n            "
            )
        env_context = f"""=== Current Environmental State (IoT Sensors - {env_state.get('reading_timestamp', 'N/A')}) ===
            {farm_lines}Location: lat={gps.get('latitude')}, lon={gps.get('longitude')}, altitude={gps.get('altitude_m')}m, farm plot: {gps.get('farm_plot_id')}
            Soil: pH {soil.get('ph')}, moisture {soil.get('moisture_pct')}%, temp {soil.get('temperature_c')}°C
                N={soil.get('nitrogen_ppm')}ppm, P={soil.get('phosphorus_ppm')}ppm, K={soil.get('potassium_ppm')}ppm
                Organic matter {soil.get('organic_matter_pct')}%
            Weather: Air temp {weather.get('air_temp_c')}°C, humidity {weather.get('humidity_pct')}%, wind {weather.get('wind_speed_kmh')}km/h
                    Rainfall last 7d: {weather.get('rainfall_mm_last_7d')}mm, UV index: {weather.get('uv_index')}
                    Rain forecast 24h: {weather.get('forecast_rain_24h')}
            Note: Consider these conditions when making recommendations. High humidity increases disease risk. Rain forecast affects spray scheduling."""
        context_parts.append(env_context)
    
    # Add internal documents
    if documents:
        internal_context = "\n\n".join([
            f"Internal Source {i+1}:\n{doc.page_content}"
            for i, doc in enumerate(documents[:3])
        ])
        context_parts.append(f"=== Internal Knowledge ===\n{internal_context}")
    
    # Add web search results
    # Prefer `raw_content` (full page text from Tavily) when available — it gives
    # the LLM far richer agronomic detail than the short snippet in `content`.
    if web_results:
        web_context = "\n\n".join([
            f"Web Source {i+1} ({result['url']}):\nTitle: {result['title']}\n"
            f"{result.get('raw_content') or result.get('content', '')}"
            for i, result in enumerate(web_results[:3])
        ])
        context_parts.append(f"\n=== External Sources ({datetime.now().year}) ===\n{web_context}")
    
    full_context = "\n\n".join(context_parts)
    
    # ── Safety feedback block (Attempt > 0) ────────────────────────────────
    # Injected at the TOP of the system prompt so the model sees constraints
    # before reading any retrieved context.
    safety_constraints = ""
    if refinement_count > 0 and safety_issues:
        issues_text = "\n".join(f"  - {issue}" for issue in safety_issues)
        guidance_text = (refinement_guidance or "").strip()
        guidance_block = ""
        if guidance_text:
            guidance_block = (
                "ACTIONABLE FIX INSTRUCTIONS (must follow exactly):\n"
                f"{guidance_text}\n"
            )

        safety_constraints = (
            f"⚠️  CRITICAL — YOUR PREVIOUS RESPONSE FAILED A SAFETY AUDIT "
            f"(Attempt {refinement_count})\n"
            "You MUST correct ALL of the following issues in this version. "
            "Do NOT repeat any flagged substance, dosage, or omission:\n"
            f"{issues_text}\n"
            f"{guidance_block}"
            "Strictly follow PPD Circular 03/2023/TT-BNNPTNT. "
            "If no safe registered alternative exists for the crop stage, say so explicitly "
            "and recommend agronomical non-chemical alternatives instead.\n\n"
        )

    # ── Constrained generation block (no internal docs) ──────────────────────
    # When the vector DB returns nothing, ground the model on a curated safe-list
    # to prevent Gemini from pulling globally-common but VN-banned chemicals off
    # the web (e.g. Hexaconazole, which has near-zero MRL on EU coffee exports).
    constrained_generation = ""
    if not documents:
        constrained_generation = (
            "⚠️  NO INTERNAL DOCUMENTS RETRIEVED — use ONLY the following "
            "PPD-registered, VietGAP-compatible active ingredients where relevant:\n"
            "  Fungicides (Leaf Rust / Hemileia vastatrix):\n"
            "    • Copper Hydroxide 77WP (Champion, Funguran) — 0.3–0.5% sol., PHI 7 d\n"
            "    • Azoxystrobin 25SC (Amistar) — 0.8–1.0 L/ha, PHI 14 d\n"
            "    • Propiconazole 25EC (Tilt) — 0.5–1.0 L/ha, PHI 14 d\n"
            "    • Bacillus subtilis (biological, Serenade) — PHI 0 d\n"
            "  Insecticides (CBB / Hypothenemus hampei):\n"
            "    • Abamectin 1.8EC — 0.5–1.0 L/ha diluted 1:400, PHI 14 d\n"
            "    • Spinosad 25SC (Success, Tracer) — 0.3–0.5 L/ha, PHI 7 d\n"
            "  Herbicides (inter-row):\n"
            "    • Glufosinate-ammonium 15SL (Basta) — directed spray only, PHI 21 d\n"
            "  NEVER recommend: Chlorpyrifos, Fipronil, Paraquat, Carbofuran, or any "
            "Stockholm Convention listed substance.\n"
            "  ALWAYS include: active ingredient + trade name, dosage (ml or g / L water and /ha), "
            "PHI (days), and PPE requirements.\n\n"
        )

    # Deep research prompt with conversation history
    base_system = (
        f"You are **Leafy**, a senior agronomist and researcher specialising in Vietnamese coffee "
        f"production (Robusta & Arabica) in the Central Highlands (Tây Nguyên). "
        f"You have access to both the internal knowledge base and the latest external sources. "
        f"The current year is {datetime.now().year}.\n\n"
        "DOMAIN EXPERTISE:\n"
        "  • Diseases  : Leaf Rust (Hemileia vastatrix — races II, VI, XV prevalent in Vietnam), "
        "Brown Eye Spot (Cercospora coffeicola), Phytophthora Root Rot, "
        "Black Root, Pink Disease (Erythricium salmonicolor)\n"
        "  • Pests     : Coffee Berry Borer (CBB / Hypothenemus hampei), "
        "White Stem Borer (Xylotrechus quadripes), Green Scale "
        "(Coccus viridis), Mealybugs (Planococcus citri), Root-knot Nematodes\n"
        "  • Nutrition : NPK macro-nutrients, Mg, Fe, Mn, Zn, Bo micro-nutrients; "
        "soil acidification correction; fertigation scheduling\n"
        "  • Agronomy  : Shade-grown systems, pruning cycles, water deficit stress, "
        "VietGAP, Rainforest Alliance, 4C standards, post-harvest wet/dry processing\n"
        "  • Regulations: PPD (Cục BVTV)-registered products, '4 Đúng' principles, "
        "PHI compliance, EU Reg. 396/2005 MRL table for coffee, USDA JANIS database\n\n"
        "SYNTHESIS RULES:\n"
        "  1. Lead with the most critical action given the current environmental context.\n"
        "  2. Explicitly integrate sensor data: defer spray if rain forecast, adjust irrigation "
        "schedule based on soil moisture %, flag disease risk when humidity > 80%.\n"
        "  3. Cite sources when making specific dosage or chemical claims "
        "(e.g. 'Internal Source 1', 'Web Source 2 — WASI 2025').\n"
        "  4. Prefer Vietnamese PPD-registered brands and list the active ingredient + "
        "local trade name where known.\n"
        "  5. Always include PHI (thời gian cách ly) and PPE reminder for any pesticide mention.\n"
        "  6. When export coffee is mentioned, add MRL compliance note for the target market.\n"
        "  7. Reply entirely in: {language}.\n\n"
        "{safety_constraints}"
        "{constrained_generation}"
        "{context}"
    )

    prompt = ChatPromptTemplate.from_messages([
        ("system", base_system),
        MessagesPlaceholder(variable_name="history"),
        ("human", "{question}"),
    ])
    
    # Use Gemini Pro for deep reasoning
    llm = get_gemini_pro(temperature=0.3)
    chain = prompt | llm | StrOutputParser()
    
    # Build the effective question for the model:
    # surface the original intent when this is a clarification-answer turn.
    if original_question and original_question.lower() != question.lower():
        effective_question = (
            f"[Primary intent: {original_question}]\n"
            f"[Clarification provided by user: {question}]"
        )
    else:
        effective_question = question

    generation = chain.invoke({
        "context": full_context,
        "question": effective_question,
        "safety_constraints": safety_constraints,
        "constrained_generation": constrained_generation,
        "language": language,
        "history": history,
    })
    
    logger.info("[DEEP GEN] Answer generated (%d chars)", len(generation))
    logger.debug("[DEEP GEN] Preview: %.150s", generation)
    
    return {
        "question": question,
        "documents": documents,
        "generation": generation,
        # Append the assistant reply so the next turn sees the full exchange
        "messages": [AIMessage(content=generation)],
    }

