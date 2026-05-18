"""
Advanced RAG Agent with Multi-Phase Pipeline

Phases:
1. [Implicit] Query enters system
2. Advanced Retrieval: Hybrid Search → Reranking
3. Routing: Fast Path (Gemini Flash) or Deep Path (Web Search + Gemini Pro)
4. Safety: Safety Auditor (Dosage & Regulatory) → Refinement Loop
"""

from langgraph.graph import END, StateGraph
import logging
import os

from app.agents.rag_state import GraphState

logger = logging.getLogger(__name__)

# Phase 2: Advanced Retrieval
from app.nodes.hybrid_search_node import hybrid_search
from app.nodes.reranker_node import rerank_documents

# Phase 0: Environment State (IoT sensors)
from app.nodes.env_state_node import fetch_env_state

# Phase 3: Routing
from app.nodes.router_node import route_decision
from app.nodes.fast_generation_node import fast_generation
from app.nodes.web_search_node import web_search
from app.nodes.deep_generation_node import deep_generation

# Phase 4: Safety
from app.nodes.safety_auditor_node import safety_auditor
from app.nodes.refinement_node import refinement

# Phase 5: Planning
from app.nodes.planner import planner

# Phase 6: History summarization
from app.nodes.summarization_node import maybe_summarize

# Phase 1.5: Intent and Clarification classification
from app.nodes.intent_and_clarification_node import intent_and_clarification, clarification_response
from app.nodes.direct_node import direct_response


# === Conditional Edge Functions ===

def route_by_intent_and_clarification(state: GraphState) -> str:
    """Short-circuit direct messages or vague questions, else proceed to full RAG."""
    intent = state.get("intent", "agriculture_query")
    if intent == "direct":
        logger.info("[GRAPH] Direct intent detected → DIRECT path (skip RAG)")
        return "direct"
    
    if state.get("needs_clarification"):
        logger.info("[GRAPH] Question too vague → CLARIFICATION path")
        return "needs_clarification"
        
    logger.info("[GRAPH] Agricultural query sufficient → full RAG pipeline")
    return "proceed"


def route_by_confidence(state: GraphState) -> str:
    """Route to planning, fast, or deep path based on router decision."""
    path_type = state.get("path_type", "deep")
    if path_type == "planning":
        logger.info("[GRAPH] → PLANNING PATH (planner directly)")
        return "planning"
    elif path_type == "fast":
        logger.info("[GRAPH] → FAST PATH (Gemini Flash)")
        return "fast"
    else:
        logger.info("[GRAPH] → DEEP PATH (WebSearch + Gemini Pro)")
        return "deep"


def check_safety_compliance(state: GraphState) -> str:
    """Check if safety and regulatory audit passed."""
    safety_passed = state.get("safety_passed", True)
    if safety_passed:
        logger.info("[GRAPH] Safety & compliance ✓ → END")
        return "safe"
    else:
        logger.warning("[GRAPH] Safety & compliance ✗ → refinement loop")
        return "unsafe"


def check_refinement_limit(state: GraphState) -> str:
    """Check if we can retry or hit max attempts."""
    import os
    refinement_count = state.get("refinement_count", 0)
    max_attempts = int(os.getenv("MAX_REFINEMENT_ATTEMPTS", "3"))
    safety_passed = state.get("safety_passed", False)
    if safety_passed:
        logger.info("[GRAPH] Refinement successful → END")
        return "complete"
    if refinement_count < max_attempts:
        logger.info("[GRAPH] Retry refinement (%d/%d)", refinement_count, max_attempts)
        path_type = state.get("path_type", "deep")
        if path_type == "planning":
            return "retry_plan_search"
        return "retry_gen_search"
    else:
        logger.warning("[GRAPH] Max refinement attempts reached → END with fallback")
        return "complete"


# === Graph Builder ===

def build_graph(checkpointer=None):
    """
    Build the advanced RAG pipeline graph.
    
    Args:
        checkpointer: An initialised AsyncSqliteSaver (or any BaseCheckpointSaver)
                      for persistent multi-turn memory. Pass None to disable persistence.

    Flow:
    1. Hybrid search (dense + sparse)
    2. Rerank documents
    3. Route (fast vs deep vs planning)
    4. Generate (fast, deep, or treatment plan)
    5. Safety checks (dosage + regulatory combined)
    6. Refinement loop if needed
    """
    workflow = StateGraph(GraphState)

    # === Phase 6: History summarization (entry point) ===
    workflow.add_node("maybe_summarize", maybe_summarize)

    # === Phase 1.5: Intent and Clarification ===
    workflow.add_node("intent_and_clarification", intent_and_clarification)
    workflow.add_node("direct", direct_response)
    workflow.add_node("clarification", clarification_response)

    # === Phase 0: Environment State ===
    workflow.add_node("env_state", fetch_env_state)

    # === Phase 2: Advanced Retrieval ===
    workflow.add_node("hybrid_search", hybrid_search)
    workflow.add_node("reranker", rerank_documents)
    
    # === Phase 3: Routing ===
    workflow.add_node("router", route_decision)
    workflow.add_node("fast_gen", fast_generation)
    workflow.add_node("web_search", web_search)       # deep Q&A path
    workflow.add_node("web_search_plan", web_search)  # planning path — same fn, separate node
    workflow.add_node("deep_gen", deep_generation)
    
    # === Phase 4: Safety ===
    workflow.add_node("safety_audit", safety_auditor)
    workflow.add_node("refine", refinement)

    # === Phase 5: Planning ===
    workflow.add_node("planner", planner)
    
    # === Entry Point ===
    workflow.set_entry_point("maybe_summarize")

    # === Phase 6 → Phase 1.5 ===
    workflow.add_edge("maybe_summarize", "intent_and_clarification")

    # === Phase 1.5: Intent and Clarification routing ===
    workflow.add_conditional_edges(
        "intent_and_clarification",
        route_by_intent_and_clarification,
        {
            "direct": "direct",                        # direct fast path → END
            "needs_clarification": "clarification",    # ask user for more info → END
            "proceed": "env_state",                    # full RAG pipeline
        }
    )
    workflow.add_edge("direct", END)
    workflow.add_edge("clarification", END)

    # === Phase 0 → Phase 2 ===
    workflow.add_edge("env_state", "hybrid_search")

    # === Phase 2 Flow (Linear) ===
    workflow.add_edge("hybrid_search", "reranker")
    workflow.add_edge("reranker", "router")
    
    # === Phase 3 Routing (Conditional) ===
    workflow.add_conditional_edges(
        "router",
        route_by_confidence,
        {
            "planning": "web_search_plan",  # planning path: web search THEN planner
            "fast": "fast_gen",
            "deep": "web_search"
        }
    )
    workflow.add_edge("web_search_plan", "planner")  # planning path
    workflow.add_edge("web_search", "deep_gen")                # deep Q&A path
    
    # === Phase 4 Safety Flow (Conditional) ===
    # All generation nodes converge to safety audit
    workflow.add_edge("fast_gen", "safety_audit")
    workflow.add_edge("deep_gen", "safety_audit")
    workflow.add_edge("planner", "safety_audit")
    
    # Safety audit decision
    workflow.add_conditional_edges(
        "safety_audit",
        check_safety_compliance,
        {
            "safe": END,
            "unsafe": "refine"
        }
    )
    
    # Refinement loop decision
    workflow.add_conditional_edges(
        "refine",
        check_refinement_limit,
        {
            # Retry through web search first to fetch missing compliance context
            # (MRL/PHI/legal updates), then regenerate.
            "retry_gen_search": "web_search",            # Q&A path
            "retry_plan_search": "web_search_plan",      # planning path
            "complete":  END,                  # Max attempts reached → END with fallback
        }
    )

    
    # Compile the graph. The checkpointer is passed in from the FastAPI lifespan
    # so that the AsyncSqliteSaver context manager stays open for the app's lifetime.
    app = workflow.compile(checkpointer=checkpointer)
    
    return app


# Placeholder — replaced with a live graph during FastAPI startup (see main.py lifespan).
# Using None here avoids running async code at import time.
rag_app = None
