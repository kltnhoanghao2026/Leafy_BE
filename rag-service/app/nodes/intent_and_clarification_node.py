"""
Intent and Clarification Node

Merges intent classification and clarification checks into a single node to reduce LLM overhead.
Evaluates user intent and checks if more context is needed before proceeding to retrieval.

Decisions:
    - "direct" -> direct node -> END
    - "needs_clarification" -> clarification response -> END
    - "proceed" -> env_state -> full RAG pipeline
"""

import logging
from typing import List

from langchain_core.messages import AIMessage, HumanMessage
from pydantic import BaseModel, Field

from app.agents.rag_state import GraphState
from app.core.ai_providers import get_gemini_flash
from app.core.constants import AGRI_SIGNAL_RE, DIRECT_SIGNAL_RE, GREETING_RE

logger = logging.getLogger(__name__)

# How many prior message pairs to send to the clarification LLM for context.
_HISTORY_PAIRS = 3

class IntentAndClarificationDecision(BaseModel):
    """LLM decision on intent and whether the question needs more detail."""
    intent: str = Field(
        description="Either 'direct' or 'agriculture_query'."
    )
    needs_clarification: bool = Field(
        description="True ONLY if the question is so vague that generating a useful answer is impossible without more info."
    )
    clarification_question: str = Field(
        description="The short, friendly follow-up question to send to the user, if needs_clarification is True."
    )
    missing_info: List[str] = Field(
        description="Brief list of what is missing or ambiguous. Empty list when not needed."
    )

_SYSTEM_PROMPT = """\
You are a routing and quality-check assistant for an AI agronomist named Leafy that advises Vietnamese coffee farmers.

Your job: 
1. Classify the user message intent as 'direct' (small talk, jokes) or 'agriculture_query' (farming, plant care).
2. If 'agriculture_query', decide whether the user's question has enough context to generate a specific, actionable response.

IMPORTANT: You will receive the recent conversation history BEFORE the user's current message. If the crop type, plant, or relevant context was already established in any prior message, treat the current question as SUFFICIENT (needs_clarification=False) — do NOT ask again.

--- SUFFICIENT (needs_clarification = false) ---
A question is sufficient when:
• It is a GENERAL agricultural concept question.
• For a PLAN request (treatment, care, schedule), it MUST have BOTH:
    1. The crop/plant name (e.g. coffee, cà phê, pepper, tiêu)
    2. The context: either the growth stage (e.g. "cây non", "đang ra hoa") OR the disease severity (e.g. "rụng lá hàng loạt", "rỉ sắt nặng").
    (If this context is already established in the history, treat it as sufficient.)
• It is a simple follow-up referring to an existing plan.

--- NEEDS CLARIFICATION (needs_clarification = true) ---
Flag as needing clarification when:
• The user asks for a personalised plan or specific treatment, BUT:
    - The crop/plant type is missing (not in message AND not in history).
    - OR the context (growth stage / disease severity) is missing.
• The request is too vague to produce a meaningful agronomic response.

If needs_clarification=true, write clarification_question as a SINGLE SHORT friendly sentence in {language}. Ask specifically for the missing pieces. Do not explain why you need it. Example: "Bạn đang trồng loại cây gì và mức độ nhiễm bệnh hiện tại ra sao?"
"""

def intent_and_clarification(state: GraphState) -> dict:
    """
    Classify user intent and determine if more detail is needed.
    """
    question = state.get("question", "").strip()
    language = state.get("language") or "Vietnamese"

    logger.info("[INTENT_CLARIFY] Checking intent and sufficiency: '%.80s'", question)

    if state.get("forced_route") in ("fast", "deep", "planning"):
        logger.info("[INTENT_CLARIFY] Forced route '%s' — fast-pass to proceed", state.get("forced_route"))
        return {"intent": "agriculture_query", "needs_clarification": False, "clarification_question": ""}

    if GREETING_RE.match(question) or DIRECT_SIGNAL_RE.search(question):
        logger.info("[INTENT_CLARIFY] Direct pattern matched → direct")
        return {"intent": "direct", "needs_clarification": False, "clarification_question": ""}

    # Check recent history for context
    all_messages = state.get("messages", [])
    history = all_messages[:-1] if len(all_messages) > 1 else []
    
    recent_human_texts = [
        m.content for m in history[-6:]
        if isinstance(m, HumanMessage)
    ]
    
    history_msgs = []
    for msg in history[-((_HISTORY_PAIRS) * 2):]:
        if isinstance(msg, HumanMessage):
            history_msgs.append({"role": "user", "content": msg.content[:400] + "…" if len(msg.content) > 400 else msg.content})
        else:
            history_msgs.append({"role": "assistant", "content": msg.content[:200] + "…" if len(msg.content) > 200 else msg.content})

    has_agri_history = any(AGRI_SIGNAL_RE.search(t) for t in recent_human_texts)
    
    # Very short with no agricultural context
    if len(question.split()) <= 4 and not AGRI_SIGNAL_RE.search(question) and not has_agri_history:
        logger.info("[INTENT_CLARIFY] Short non-agri message with no prior context → direct")
        return {"intent": "direct", "needs_clarification": False, "clarification_question": ""}

    try:
        llm = get_gemini_flash(temperature=0).with_structured_output(IntentAndClarificationDecision)
        messages = (
            [{"role": "system", "content": _SYSTEM_PROMPT.format(language=language)}]
            + history_msgs
            + [{"role": "user", "content": question}]
        )
        decision: IntentAndClarificationDecision = llm.invoke(messages)
        
        intent = decision.intent if decision.intent in ("direct", "agriculture_query") else "agriculture_query"
        
        if intent == "direct":
            logger.info("[INTENT_CLARIFY] LLM → direct")
            return {"intent": "direct", "needs_clarification": False, "clarification_question": ""}
            
        if decision.needs_clarification:
            logger.info("[INTENT_CLARIFY] Insufficient — asking: '%s'", decision.clarification_question)
            return {
                "intent": "agriculture_query",
                "needs_clarification": True,
                "clarification_question": decision.clarification_question,
                "original_question": question, # Save original question for planner
            }
        
        logger.info("[INTENT_CLARIFY] Question is sufficient → proceed")
        return {
            "intent": "agriculture_query",
            "needs_clarification": False,
            "clarification_question": "",
        }

    except Exception as exc:
        logger.warning("[INTENT_CLARIFY] LLM error, skipping check: %s", exc)
        return {
            "intent": "agriculture_query",
            "needs_clarification": False,
            "clarification_question": "",
        }

def clarification_response(state: GraphState) -> dict:
    message = state.get("clarification_question") or (
        "Bạn có thể cho tôi biết thêm về loại cây trồng và vấn đề bạn đang gặp phải không?"
    )
    logger.info("[CLARIFICATION] Sending clarification prompt to user")

    return {
        "generation": message,
        "messages": [AIMessage(content=message)],
        "safety_passed": True,
        "safety_issues": [],
    }
