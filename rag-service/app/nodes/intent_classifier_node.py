"""
Intent Classifier Node

Fast-path keyword detection + LLM classification to route chit-chat away from
the full RAG pipeline.  Agricultural queries proceed through the normal pipeline.

Decision:
  - "chit_chat"         → chit_chat node → END  (no retrieval, no safety audit)
  - "agricultural_query" → env_state → full RAG pipeline
"""

import re
import logging

from pydantic import BaseModel, Field

from app.agents.rag_state import GraphState
from app.core.ai_providers import get_gemini_flash

logger = logging.getLogger(__name__)

# ─────────────────────────────────────────────────────────────────────────────
# Keyword patterns (no LLM cost)
# ─────────────────────────────────────────────────────────────────────────────

_GREETING_RE = re.compile(
    r"^(hi|hello|hey|xin chào|chào|howdy|"
    r"good\s*(morning|afternoon|evening|day|evening)|"
    r"hola|bonjour|ciao|yo|sup|what'?s\s*up|whats?\s*up|hiya|greetings?)[\W]*$",
    re.IGNORECASE,
)

_CHIT_CHAT_RE = re.compile(
    r"\b(how are you|how'?re you|bạn (có )?khỏe (không|ko)|"
    r"you'?re (great|awesome|amazing|cool|nice)|"
    r"thank(s| you)|cảm ơn|thanks?|"
    r"what('?s| is) your name|who are you|bạn tên gì|bạn là ai|"
    r"tell me (a )?joke|make me (laugh|smile)|"
    r"good(bye| night)| bye |tạm biệt|see you (later|soon)|"
    r"what can you do\??|what do you know\??|can you help)\b",
    re.IGNORECASE,
)

# Strong agricultural signal — presence means we skip the LLM classifier entirely
_AGRI_SIGNAL_RE = re.compile(
    r"\b(coffee|cà phê|plant|cây|pest|sâu|bệnh|disease|fertilizer|phân bón|"
    r"harvest|thu hoạch|soil|đất|leaf|lá|root|rễ|fungicide|thuốc|spray|phun|"
    r"treatment|điều trị|irrigation|tưới|crop|mùa vụ|rust|gỉ sắt|borer|mọt|"
    r"robusta|arabica|tây nguyên|central highland|agri|nông|vườn|farm)\b",
    re.IGNORECASE,
)


class IntentDecision(BaseModel):
    """Structured classification output from the LLM."""
    intent: str = Field(
        description=(
            "Either 'agricultural_query' for any farming, plant-care, or crop question, "
            "or 'chit_chat' for greetings, small talk, jokes, identity questions, etc."
        )
    )
    reasoning: str = Field(description="One-sentence explanation of this classification")


def classify_intent(state: GraphState) -> dict:
    """
    Classify user intent as 'chit_chat' or 'agricultural_query'.

    Evaluation order (cheapest first):
    1. Agricultural keyword fast-pass → 'agricultural_query' (no LLM)
    2. Greeting / chit-chat pattern   → 'chit_chat'           (no LLM)
    3. Very short message (<= 5 words, no agri terms) → 'chit_chat' (heuristic)
    4. Gemini Flash structured output for ambiguous cases

    Args:
        state: Current graph state with 'question'

    Returns:
        Dict with 'intent' set to 'chit_chat' or 'agricultural_query'
    """
    question = state.get("question", "").strip()
    logger.info("[CLASSIFIER] Classifying intent — '%.80s'", question)

    # ── 1. Strong agricultural signals → skip LLM ──────────────────────────
    if _AGRI_SIGNAL_RE.search(question):
        logger.info("[CLASSIFIER] Agricultural signal detected → agricultural_query (fast-pass)")
        return {"intent": "agricultural_query"}

    # ── 2. Definite greeting / chit-chat patterns → skip LLM ───────────────
    if _GREETING_RE.match(question) or _CHIT_CHAT_RE.search(question):
        logger.info("[CLASSIFIER] Chit-chat pattern matched → chit_chat (fast-pass)")
        return {"intent": "chit_chat"}

    # ── 3. Very short message without agricultural terms ────────────────────
    if len(question.split()) <= 5:
        logger.info("[CLASSIFIER] Short non-agricultural message → chit_chat (heuristic)")
        return {"intent": "chit_chat"}

    # ── 4. LLM fallback for ambiguous messages ──────────────────────────────
    try:
        llm = get_gemini_flash(temperature=0)
        structured_llm = llm.with_structured_output(IntentDecision)

        prompt = (
            "You are a routing classifier for the Leafy Coffee Advisory AI.\n\n"
            "Classify the user message as:\n"
            "- 'agricultural_query': questions about coffee plants, farming, pests, diseases, "
            "soil, fertilisers, weather effects on crops, treatment plans, irrigation, harvest, etc.\n"
            "- 'chit_chat': greetings, small talk, personal questions about the AI, jokes, "
            "or any conversation unrelated to agriculture or plant care.\n\n"
            f"User message: {question}\n\n"
            "Classify concisely."
        )

        result = structured_llm.invoke(prompt)
        intent = result.intent if result.intent in ("chit_chat", "agricultural_query") else "agricultural_query"
        logger.info("[CLASSIFIER] LLM → %s (%s)", intent, result.reasoning)
        return {"intent": intent}

    except Exception as exc:
        logger.warning("[CLASSIFIER] LLM failed (%s) — defaulting to agricultural_query", exc)
        return {"intent": "agricultural_query"}
