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
from langchain_core.prompts import ChatPromptTemplate
from pydantic import BaseModel, Field

from app.agents.rag_state import GraphState
from app.core.ai_providers import get_chat_model

logger = logging.getLogger(__name__)


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
        logger.warning("[ROUTER] No documents retrieved — forcing DEEP path")
        return {
            "question": question,
            "path_type": "deep",
            "confidence_score": 0.0,
            "completeness_score": 0.0,
        }

    # Use LLM to detect intent + score confidence and completeness
    llm = get_chat_model(temperature=0)
    structured_llm = llm.with_structured_output(RouterDecision)

    system = """You are a routing expert for an agricultural RAG system.

Your job is to classify the user question and evaluate the retrieved documents and current environmental conditions.

Step 1 — Detect planning intent (is_planning_request):
  Set TRUE if the user is asking for ANY of:
  - A treatment plan / recovery plan / action plan
  - A step-by-step disease management or pest control schedule
  - A chronological list of events/tasks to perform on a specific plant
  - Keywords like: "create a plan", "schedule", "what steps", "treatment plan", "kế hoạch", "lịch trình"
  Set FALSE for general informational questions, explanations, or advice.

Step 2 — Score documents (only matters if is_planning_request is False):
  1. Confidence (0-1): How relevant and accurate are the docs for this question?
  2. Completeness (0-1): Do the docs cover all aspects needed to answer?

Consider:
  - Are the documents on-topic?
  - Do they cover all aspects needed to answer the question?
  - Is the information recent enough (if time-sensitive)?
  - Do environmental conditions (weather, soil) require additional research or immediate attention?
  - If weather is critical (rain forecast, extreme conditions), consider routing to deep path for comprehensive guidance.
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
