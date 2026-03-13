"""
Safety Auditor Node

Validates chemical concentrations, dosages, and regulatory compliance in generated 
responses to prevent hallucinations, unsafe recommendations, and regulatory violations.
"""

import logging
from langchain_core.prompts import ChatPromptTemplate
from pydantic import BaseModel, Field
from typing import List

from app.agents.rag_state import GraphState
from app.core.ai_providers import get_chat_model

logger = logging.getLogger(__name__)


class SafetyAudit(BaseModel):
    """Model for combined safety and regulatory audit results."""
    is_safe_and_compliant: bool = Field(description="Whether the response is entirely safe and regulatory compliant")
    issues_found: List[str] = Field(description="List of specific safety or regulatory issues detected")
    reasoning: str = Field(description="Explanation of the audit decision")


# Configurable list of banned substances (can be expanded)
BANNED_SUBSTANCES = [
    "DDT",
    "Chlordane",
    "Heptachlor",
    "Paraquat",  # Banned in many countries
    "Endosulfan",
    "Lindane",
    "Aldrin",
    "Dieldrin",
]


def safety_auditor(state: GraphState) -> dict:
    """
    Audit generated response for dosage safety, environmental appropriateness, 
    and regulatory compliance.
    
    Checks for:
    - Banned substances
    - Unrealistic chemical concentrations or unsafe application rates
    - Potential hallucinations in dosage recommendations
    - Environmental conditions that affect application safety (rain, temperature, wind, etc.)
    - Proper safety warnings for restricted chemicals
    
    Args:
        state: Current graph state with generation and env_state
        
    Returns:
        Updated state with safety_passed and safety_issues
    """
    logger.info("[SAFETY AUDIT] Checking generation for safety and compliance")
    
    generation = state.get("generation", "")
    question = state["question"]
    env_state = state.get("env_state", {})
    
    # 1. Quick checks for obvious violations or missing keywords
    issues = []
    
    # Quick scan for banned substances
    for substance in BANNED_SUBSTANCES:
        if substance.lower() in generation.lower():
            issues.append(f"Mentions banned substance: {substance}")
            
    # Environmental compliance checks (hard rules)
    if env_state:
        weather = env_state.get("weather", {})
        
        # Check if chemical application is recommended during rain
        if weather.get("forecast_rain_24h") and any(keyword in generation.lower() for keyword in ["spray", "apply", "application", "fungicide", "pesticide", "herbicide"]):
            issues.append("Chemical application recommended with rain forecast in next 24h (runoff risk - regulatory violation)")
        
        # Check wind speed for spray applications
        wind_speed = weather.get("wind_speed_kmh", 0)
        if wind_speed > 15 and any(keyword in generation.lower() for keyword in ["spray", "foliar application"]):
            issues.append(f"Spray application with high wind speed ({wind_speed} km/h) - drift risk exceeds regulatory limits")
        
        # Check temperature extremes for chemical applications
        temp = weather.get("air_temp_c", 25)
        if temp > 35 and any(keyword in generation.lower() for keyword in ["herbicide", "pesticide"]):
            issues.append(f"Chemical application at extreme temperature ({temp}°C) violates safe application guidelines")
    
    # Check if there's any chemical/dosage information that needs LLM review
    dosage_keywords = ["ppm", "mg/l", "concentration", "dosage", "application rate", "ml", "grams", "kg"]
    has_dosage_info = any(keyword in generation.lower() for keyword in dosage_keywords)
    
    # If there are hard violations, we can immediately fail without LLM call
    if issues and not has_dosage_info: 
        logger.warning("[SAFETY AUDIT] FAILED (Hard checks) — violations: %s", issues)
        return {
            "safety_passed": False,
            "safety_issues": issues,
        }

    # If no dosage info and no hard violations, pass by default (save LLM call)
    if not has_dosage_info and not issues:
        logger.info("[SAFETY AUDIT] No safety/regulatory issues detected — PASS")
        return {
            "safety_passed": True,
            "safety_issues": [],
        }
        
    # 2. Use LLM for deeper safety & compliance check
    llm = get_chat_model(temperature=0)
    structured_llm = llm.with_structured_output(SafetyAudit)
    
    banned_list = ", ".join(BANNED_SUBSTANCES)
    
    # Format environmental context for safety considerations
    env_context = ""
    if env_state:
        weather = env_state.get("weather", {})
        env_warnings = []
        
        if weather.get("forecast_rain_24h"):
            env_warnings.append("Rain forecast: Chemical applications must be delayed to prevent runoff")
        
        if weather.get("wind_speed_kmh", 0) > 15:
            env_warnings.append(f"High wind ({weather.get('wind_speed_kmh')}km/h): Spray applications prohibited (drift risk)")
            
        temp = weather.get("air_temp_c", 25)
        if temp > 35:
            env_warnings.append(f"Extreme heat ({temp}°C): Restricted chemical use, potential phytotoxicity")
        elif temp < 10:
            env_warnings.append(f"Low temperature ({temp}°C) - reduced chemical efficacy expected")
        
        humidity = weather.get("humidity_pct", 50)
        if humidity > 85:
            env_warnings.append(f"Very high humidity ({humidity}%) - increased disease pressure, verify fungicide rates are appropriate")
        
        if env_warnings:
            env_context = "\n\nEnvironmental Safety & Compliance Rules:\n" + "\n".join(f"  - {w}" for w in env_warnings)
    
    system = f"""You are a safety and regulatory compliance auditor for agricultural recommendations.
    
    Verify that the response meets ALL of these requirements:
    1. Does not recommend banned or restricted substances (known list: {banned_list})
    2. Chemical concentrations, dosages, and application rates are realistic and within safe ranges
    3. Proper safety context and warnings are provided for chemical use
    4. Complies with current environmental conditions and regulations
    {env_context}
    
    Common issues to flag:
    - Extremely high or low concentrations that don't make sense
    - Missing units or unclear measurement
    - Applications scheduled during bad weather (rain/wind)
    - Recommending chemical use without PPE warnings if toxic
    
    Be conservative - if something seems unsafe, potentially non-compliant, or unclear, flag it."""
    
    audit_prompt = ChatPromptTemplate.from_messages([
        ("system", system),
        ("human", """Question: {question}

        Generated Response:
        {generation}

        Audit this response for safety and regulatory compliance. Is it safe and compliant?""")
    ])
    
    audit_chain = audit_prompt | structured_llm
    
    try:
        audit_result = audit_chain.invoke({
            "question": question,
            "generation": generation
        })
    except Exception as e:
        logger.warning("[SAFETY AUDIT] Structured output parsing failed: %s — defaulting to PASS", e)
        # If LLM fails but we had hard issues, fail it
        if issues:
            return {"safety_passed": False, "safety_issues": issues}
        return {"safety_passed": True, "safety_issues": []}
    
    # Combine hard check issues with LLM audit issues
    all_issues = issues + audit_result.issues_found
    
    if audit_result.is_safe_and_compliant and not issues:
        logger.info("[SAFETY AUDIT] PASSED")
        return {
            "safety_passed": True,
            "safety_issues": [],
        }
    else:
        logger.warning("[SAFETY AUDIT] FAILED — issues: %s", all_issues)
        logger.debug("[SAFETY AUDIT] Reasoning: %s", audit_result.reasoning)
        return {
            "safety_passed": False,
            "safety_issues": all_issues,
        }
