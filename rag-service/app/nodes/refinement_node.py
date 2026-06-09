"""
Refinement Node

Handles self-correction loop when safety checks fail.
Injects safety feedback back into generation process.
"""

import os
import logging
from typing import List
from app.agents.rag_state import GraphState

logger = logging.getLogger(__name__)


def _build_refinement_guidance(safety_issues: List[str]) -> str:
    """Turn safety issues into explicit fix instructions for the next generation."""
    if not safety_issues:
        return ""

    instructions = []
    for issue in safety_issues:
        issue_text = (issue or "").strip()
        lowered = issue_text.lower()

        if any(k in lowered for k in ("dosage", "5×", "5x", "upper limit", "l/ha", "exceeds", "mg/l", "ml per", "concentration")):
            instructions.append(
                "The dosage must be expressed as L/ha or kg/ha on the product label scale. "
                "For Vietnamese coffee spray equipment (400-800 L water/ha), typical fungicide rates are: "
                "systemic fungicides 0.5-1.0 L/ha (=12.5-25 ml per 25L water), "
                "contact fungicides 1.5-2.5 kg/ha. "
                "Express all dosages using the official Vietnamese label rate (e.g., '0.5-1.0 L/ha, equivalent to 15-20 ml/25L water'). "
                "Do NOT recommend any dosage exceeding the product label maximum."
            )
            continue

        if any(k in lowered for k in ("mrl", "residue", "export", "eu", "usda", "reg. 396/2005")):
            instructions.append(
                "You are missing MRL information for the target market. "
                "Find or add the relevant MRL limits/residue warning (required only for export-oriented context)."
            )
            continue

        if any(k in lowered for k in ("phi", "pre-harvest", "cach ly")):
            instructions.append(
                "You are missing PHI (pre-harvest interval) details. "
                "Add a specific PHI duration by active ingredient or product group to ensure harvest safety."
            )
            continue

        if any(k in lowered for k in ("ppe", "mask", "glove", "boot", "bao ho")):
            instructions.append(
                "You are missing PPE requirements. "
                "Add the mandatory protective gear list (mask/respirator, gloves, boots, protective clothing)."
            )
            continue

        if "banned" in lowered or "restricted" in lowered:
            instructions.append(
                "You recommended a banned or restricted substance. "
                "Remove it and replace it with an active ingredient allowed by Vietnam regulations (PPD)."
            )
            continue

        instructions.append(
            f"You must fix this issue: {issue_text}. "
            "Find or add the missing information in the revised response."
        )

    numbered = "\n".join(f"{idx + 1}. {text}" for idx, text in enumerate(instructions))
    return (
        "YOU MUST FIX ALL ISSUES BELOW BEFORE ANSWERING AGAIN:\n"
        f"{numbered}\n"
        "Do not repeat the previous response until the missing information has been resolved."
    )


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
    refinement_guidance = _build_refinement_guidance(safety_issues)
    max_attempts = int(os.getenv("MAX_REFINEMENT_ATTEMPTS", "3"))
    
    logger.info("[REFINEMENT] Attempt %d/%d | Issues: %s", refinement_count, max_attempts, safety_issues)
    
    if refinement_count >= max_attempts:
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
            "refinement_guidance": refinement_guidance,
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
        "refinement_guidance": refinement_guidance,
    }

