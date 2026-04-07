"""
Router Node

Intelligent routing decision based on intent detection + confidence/completeness scoring.

Routes:
  - planning → Treatment Planner directly (skip generation + safety)
  - fast     → Gemini Flash (high confidence internal docs)
  - deep     → Web Search + Gemini Pro (low confidence / incomplete)
"""

import os
import logging
import re
from langchain_core.prompts import ChatPromptTemplate
from pydantic import BaseModel, Field

from app.agents.rag_state import GraphState
from app.core.ai_providers import get_chat_model

logger = logging.getLogger(__name__)


_PLANNING_INTENT_RE = re.compile(
    r"\b(treatment plan|recovery plan|action plan|care plan|"
    r"step-by-step|what steps|schedule|spray calendar|"
    r"give me a plan|plan for|"
    r"kế hoạch|lịch trình|lịch phun|phác đồ|quy trình xử lý)\b",
    re.IGNORECASE,
)


class RouterDecision(BaseModel):
    """Decision model for routing logic."""
    is_planning_request: bool = Field(
        description=(
            "True if the user is explicitly asking for a treatment plan, recovery schedule, "
            "action plan, or step-by-step disease management program for a specific plant. "
            "False for general questions, explanations, or advice."
        )
    )
    confidence_score: float = Field(
        description="Confidence that retrieved docs fully answer the question (0-1)"
    )
    completeness_score: float = Field(
        description="Score indicating if there are information gaps (0-1)"
    )
    reasoning: str = Field(description="Brief explanation of the routing decision")


def route_decision(state: GraphState) -> dict:
    """
    Evaluate the user question, retrieved documents, and environmental state, then decide the routing path.

    Routes:
    - Planning intent detected → Planning Path (treatment_planner directly)
    - High confidence + complete → Fast Path (Gemini Flash)
    - Low confidence OR incomplete → Deep Path (Web Search + Gemini Pro)

    Args:
        state: Current graph state with reranked documents and env_state

    Returns:
        Updated state with path_type, confidence_score, completeness_score
    """
    logger.info("[ROUTER] Evaluating routing decision")

    question = state["question"]
    documents = state.get("documents", [])
    env_state = state.get("env_state", {})

    # Set default path if no documents
    if not documents:
        # Planning intent must still reach treatment planner even when the
        # internal KB has no hits. In that case the graph should use web_search_plan
        # and then call treatment_planner.
        if _PLANNING_INTENT_RE.search(question):
            logger.info(
                "[ROUTER] No documents but planning intent detected — PLANNING path "
                "(web_search_plan -> treatment_planner)"
            )
            return {
                "question": question,
                "path_type": "planning",
                "confidence_score": 0.0,
                "completeness_score": 0.0,
            }

        # Short / generic questions with no retrieved docs don't benefit from a
        # Tavily web-search round-trip — use Gemini Flash's pre-training knowledge.
        # Long or detail-heavy questions still warrant web search (deep path).
        _DETAIL_SIGNAL_RE = re.compile(
            r"\b(dosage|concentration|ppm|mg|kg/ha|g/ha|l/ha|treatment plan|"
            r"specific|exactly|how much|how many|rate|latest|diagnosis|"
            r"liều lượng|nồng độ|loại thuốc|phòng trừ|điều trị|bao nhiêu)\b",
            re.IGNORECASE,
        )
        is_simple = len(question.split()) < 12 and not _DETAIL_SIGNAL_RE.search(question)
        if is_simple:
            logger.info("[ROUTER] No documents + simple query — FAST path (no web search)")
            return {
                "question": question,
                "path_type": "fast",
                "confidence_score": 0.3,
                "completeness_score": 0.3,
            }
        logger.warning("[ROUTER] No documents retrieved — forcing DEEP path (web search)")
        return {
            "question": question,
            "path_type": "deep",
            "confidence_score": 0.0,
            "completeness_score": 0.0,
        }

    # Use LLM to detect intent + score confidence and completeness
    llm = get_chat_model(temperature=0)
    structured_llm = llm.with_structured_output(RouterDecision)

    system = """You are a routing expert for the **Leafy Coffee Advisory RAG system** — a precision agricultural AI focused on Vietnamese coffee cultivation (Robusta and Arabica) in the Central Highlands (Tây Nguyên) and other growing regions.

Your job is to classify the incoming user question, evaluate the retrieved knowledge-base documents, and factor in the current IoT environmental readings.

───────────────────────────────────────────────────────────────────
COFFEE DOMAIN CONTEXT (apply to all routing decisions)
───────────────────────────────────────────────────────────────────
Key crops         : Coffea canephora (Robusta), Coffea arabica (Arabica)
Primary pests     : Coffee Berry Borer (CBB / Hypothenemus hampei), White Stem Borer
                   (Xylotrechus quadripes), Mealybugs (Planococcus citri)
Primary diseases  : Coffee Leaf Rust (Hemileia vastatrix), Brown Eye Spot
                   (Cercospora coffeicola), Phytophthora Root Rot, Wilt
Nutritional issues: K-deficiency (irregular maturation), N-deficiency,
                   Fe/Mn chlorosis on high-pH soils
Harvest           : Dry-season flowering (Jan–Mar) → wet-season berry development
                   → harvest peak Oct–Dec in Dak Lak / Lam Dong
Critical thresholds:
   - Humidity > 80% + warm nights → high Leaf Rust sporulation risk
   - Forecast rain in 24 h → do NOT recommend fungicide/pesticide spray
   - Soil pH < 5.0 or > 6.5 → nutrient lock-out risk
   - Temperature > 32°C sustained → heat stress, increase irrigation

───────────────────────────────────────────────────────────────────
STEP 1 — Detect planning intent (is_planning_request)
───────────────────────────────────────────────────────────────────
Set TRUE if the user asks for ANY of:
  - A treatment or recovery plan / action schedule for a specific coffee plant or plot
  - A step-by-step disease/pest management programme (Leaf Rust, CBB, etc.)
  - A chronological list of agronomic events (spray calendar, fertilisation schedule, etc.)
  - Keywords: "create a plan", "schedule", "what steps", "treatment plan",
              "kế hoạch", "lịch trình", "lịch phun", "kế hoạch xử lý"
Set FALSE for general informational questions, identification requests, or advice.

───────────────────────────────────────────────────────────────────
STEP 2 — Score retrieved documents (only when is_planning_request is False)
───────────────────────────────────────────────────────────────────
confidence_score (0–1): How relevant and accurate are the docs for this question?
  • 0.9–1.0: Docs directly address the specific coffee disease / pest / nutrient issue
  • 0.6–0.9: Docs are related but may miss variety-specific or regional nuance
  • < 0.6  : Docs are generic, cover a different crop, or miss the question entirely

completeness_score (0–1): Do the docs cover everything needed to answer?
  • High if: dosage, timing, PHI, PPE, and Vietnamese regulatory context are present
  • Lower if: chemical names lack local brand equivalents, no PHI/MRL data, no IoT context

Additional routing factors:
  - If humidity > 80% and docs lack fungicide-specific guidance → route DEEP
  - If rain is forecast and docs lack spray deferral guidance → route DEEP
  - If question involves a specific coffee variety (e.g. TR4, TN1, Catimor) without
    variety-specific docs → route DEEP
  - If the question is about export MRL compliance (EU/USDA JANIS) → always DEEP
"""

    router_prompt = ChatPromptTemplate.from_messages([
        ("system", system),
        ("human", """Question: {question}

Environmental Context: {env_context}

Retrieved Documents:
{documents}

Classify the intent and evaluate the documents.""")
    ])

    router_chain = router_prompt | structured_llm

    # Format documents for prompt
    docs_text = "\n\n".join([
        f"Document {i+1}:\n{doc.page_content[:300]}..."
        for i, doc in enumerate(documents[:3])
    ])

    # Format environmental context
    env_context = "No environmental data available"
    if env_state:
        soil = env_state.get("soil", {})
        weather = env_state.get("weather", {})
        env_context = f"""Soil: pH {soil.get('ph')}, moisture {soil.get('moisture_pct')}%, temp {soil.get('temperature_c')}°C
Weather: {weather.get('air_temp_c')}°C, humidity {weather.get('humidity_pct')}%, rain forecast: {weather.get('forecast_rain_24h')}"""

    try:
        decision = router_chain.invoke({
            "question": question,
            "documents": docs_text,
            "env_context": env_context
        })
    except Exception as e:
        logger.warning("[ROUTER] Structured output parsing failed: %s — defaulting to DEEP path", e)
        return {
            "question": question,
            "path_type": "deep",
            "confidence_score": 0.0,
            "completeness_score": 0.0,
        }

    # Planning intent takes priority over confidence scoring
    if decision.is_planning_request:
        path_type = "planning"
        logger.info("[ROUTER] → PLANNING PATH (treatment planner — skip generation)")
    else:
        # Get thresholds from environment
        confidence_threshold = float(os.getenv("CONFIDENCE_THRESHOLD", "0.6"))
        completeness_threshold = float(os.getenv("COMPLETENESS_THRESHOLD", "0.6"))

        if (decision.confidence_score >= confidence_threshold
                and decision.completeness_score >= completeness_threshold):
            path_type = "fast"
            logger.info("[ROUTER] → FAST PATH (confidence=%.2f, completeness=%.2f)",
                        decision.confidence_score, decision.completeness_score)
        else:
            path_type = "deep"
            logger.info("[ROUTER] → DEEP PATH (confidence=%.2f, completeness=%.2f)",
                        decision.confidence_score, decision.completeness_score)

    logger.debug("[ROUTER] Reasoning: %s", decision.reasoning)

    return {
        "question": question,
        "path_type": path_type,
        "confidence_score": decision.confidence_score,
        "completeness_score": decision.completeness_score,
    }
