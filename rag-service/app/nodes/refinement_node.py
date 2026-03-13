"""
Refinement Node

Handles self-correction loop when safety checks fail.
Injects safety feedback back into generation process.
"""

import os
import logging
from app.agents.rag_state import GraphState

logger = logging.getLogger(__name__)


def refinement(state: GraphState) -> dict:
    """
    Prepare state for refinement iteration.
    
    When safety checks fail, this node increments the refinement
    counter and prepares the safety feedback to be injected into
    the next deep generation attempt.
    
    Args:
        state: Current graph state with safety_issues
        
    Returns:
        Updated state with incremented refinement_count
    """
    logger.info("[REFINEMENT] Starting self-correction loop")
    
    refinement_count = state.get("refinement_count", 0) + 1
    safety_issues = state.get("safety_issues", [])
    max_attempts = int(os.getenv("MAX_REFINEMENT_ATTEMPTS", "3"))
    
    logger.info("[REFINEMENT] Attempt %d/%d | Issues: %s", refinement_count, max_attempts, safety_issues)
    
    if refinement_count > max_attempts:
        logger.warning("[REFINEMENT] Max attempts (%d) reached — returning safe fallback response", max_attempts)
        
        # Fallback response when max attempts reached
        fallback_generation = """I apologize, but I cannot provide a specific answer to your question at this time that meets all safety and regulatory requirements. 

                For agricultural chemical recommendations, I strongly advise:
                1. Consulting with a licensed agricultural extension officer
                2. Reviewing current regional regulations and guidelines
                3. Following manufacturer instructions for any agricultural inputs
                4. Prioritizing integrated pest management (IPM) and organic alternatives when possible

                Your safety and compliance are paramount."""
        
        fallback_state = {
            "refinement_count": refinement_count,
            "generation": fallback_generation,
            "safety_passed": True,  # Mark as passed to exit loop
        }
        
        # If this was a planning request that failed safety, 
        # add the safety concerns to the treatment plan instead of clearing it,
        # so the user is informed of the unresolved risks.
        if state.get("path_type") == "planning":
            plan = state.get("generated_plan")
            if plan:
                if "safety_warnings" not in plan:
                    plan["safety_warnings"] = []
                plan["safety_warnings"].append("⚠️ UNRESOLVED SAFETY AUDIT ISSUES:")
                plan["safety_warnings"].extend(safety_issues)
                fallback_state["generated_plan"] = plan
                
                # Also restore the original generation text and append warnings
                fallback_state["generation"] = state.get("generation", "") + "\n\n⚠️ UNRESOLVED SAFETY WARNINGS:\n" + "\n".join(f"- {i}" for i in safety_issues)
            else:
                fallback_state["generated_plan"] = None
            
        return fallback_state
    
    return {
        "refinement_count": refinement_count,
        "safety_passed": False,  # Keep failed to retry
    }

