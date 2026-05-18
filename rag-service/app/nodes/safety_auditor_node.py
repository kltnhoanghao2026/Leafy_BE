"""
Safety Auditor Node

Validates chemical concentrations, dosages, and regulatory compliance in generated 
responses to prevent hallucinations, unsafe recommendations, and regulatory violations.
"""

import logging
import re
from langchain_core.prompts import ChatPromptTemplate
from pydantic import BaseModel, ConfigDict, Field, ValidationError, field_validator
from typing import Any, List

from app.agents.rag_state import GraphState
from app.core.ai_providers import get_chat_model

logger = logging.getLogger(__name__)


class SafetyAudit(BaseModel):
    """Model for combined safety and regulatory audit results."""
    model_config = ConfigDict(extra="ignore")

    is_safe_and_compliant: bool = Field(
        default=True,
        description=(
            "True ONLY if there are zero real violations. "
            "Set this to True even if some checks were borderline, as long as no actual rule is broken. "
            "Do NOT set to False just because a check was ambiguous or a non-critical detail is missing."
        )
    )
    issues_found: List[str] = Field(
        default_factory=list,
        description=(
            "List ONLY actual violations — substances, dosages, or omissions that break a specific rule. "
            "Do NOT include items that passed their check. "
            "Do NOT include observations, narratives, or descriptions of compliant items. "
            "An empty list means the response is fully compliant."
        )
    )
    reasoning: str = Field(default="", description="One-sentence summary of the overall audit decision")

    @field_validator("is_safe_and_compliant", mode="before")
    @classmethod
    def _coerce_bool(cls, value: Any) -> bool:
        if isinstance(value, bool):
            return value
        if value is None:
            return True
        if isinstance(value, (int, float)):
            return bool(value)
        if isinstance(value, str):
            lowered = value.strip().lower()
            if lowered in {"true", "yes", "y", "pass", "safe", "compliant"}:
                return True
            if lowered in {"false", "no", "n", "fail", "unsafe", "violation"}:
                return False
        return True

    @field_validator("issues_found", mode="before")
    @classmethod
    def _coerce_issues(cls, value: Any) -> List[str]:
        if value is None:
            return []
        if isinstance(value, str):
            chunks = [part.strip(" -\n\t") for part in value.replace(";", "\n").splitlines()]
            return [c for c in chunks if c]
        if isinstance(value, dict):
            flattened = [f"{k}: {v}" for k, v in value.items()]
            return [item.strip() for item in flattened if str(item).strip()]
        if isinstance(value, (list, tuple, set)):
            return [str(item).strip() for item in value if str(item).strip()]
        return [str(value).strip()] if str(value).strip() else []

    @field_validator("reasoning", mode="before")
    @classmethod
    def _coerce_reasoning(cls, value: Any) -> str:
        if value is None:
            return ""
        if isinstance(value, str):
            return value.strip()
        return str(value).strip()


from app.core.constants import BANNED_SUBSTANCES
from app.nodes.safety_utils import filter_real_violations, is_export_context, drop_non_blocking_mrl_issues


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

    # ── Fast-pass: direct responses never contain pesticide/dosage content ──
    intent = state.get("intent", "agriculture_query")
    if intent == "direct":
        logger.info("[SAFETY AUDIT] Direct intent — skipping audit (PASS)")
        return {"safety_passed": True, "safety_issues": []}

    generation = state.get("generation", "")
    question = state["question"]
    env_state = state.get("env_state", {})
    export_context = is_export_context(question, generation)
    
    # 1. Quick checks for obvious violations or missing keywords
    issues = []
    
    # Quick scan for banned substances
    for substance in BANNED_SUBSTANCES:
        if substance.lower() in generation.lower():
            issues.append(f"Mentions banned substance: {substance}")
            
    # Environmental compliance checks (hard rules)
    if env_state:
        weather = env_state.get("weather", {})
        
        # Check if chemical application is recommended during rain — coffee-specific keywords
        _spray_kw = ["spray", "apply", "application", "fungicide", "pesticide", "herbicide",
                     "phun", "xịt", "bón", "tưới thuốc", "rải thuốc"]
        if weather.get("forecast_rain_24h") and any(kw in generation.lower() for kw in _spray_kw):
            issues.append("Chemical application recommended with rain forecast in next 24h (runoff risk — PPD regulatory violation)")
        
        # Check wind speed for spray applications
        wind_speed = weather.get("wind_speed_kmh", 0)
        if wind_speed > 15 and any(kw in generation.lower() for kw in ["spray", "foliar application", "phun", "xịt"]):
            issues.append(f"Spray application with high wind speed ({wind_speed} km/h) — drift risk exceeds regulatory limits (PPD)")
        
        # Check temperature extremes for chemical applications
        temp = weather.get("air_temp_c", 25)
        if temp > 35 and any(kw in generation.lower() for kw in ["herbicide", "pesticide", "phun thuốc"]):
            issues.append(f"Chemical application at extreme temperature ({temp}°C) — volatilisation risk, violates safe application guidelines")
        
        # Coffee-specific: high humidity is exactly when preventive fungicide IS needed —
        # instead of failing, require the generation to mention a shortened spray interval.
        humidity = weather.get("humidity_pct", 50)
        if humidity > 85 and any(kw in generation.lower() for kw in ["fungicide", "rust", "gỉ sắt", "nấm"]):
            mentions_interval = (
                "7 ngày" in generation
                or "7 days" in generation.lower()
                or "interval" in generation.lower()
                or "shortened" in generation.lower()
            )
            if not mentions_interval:
                issues.append(
                    f"High humidity ({humidity}%): fungicide recommendation must specify a "
                    "shortened spray interval (7 days / 7 ngày) per PPD Leaf Rust protocol"
                )
    
    # Check if there's any chemical/dosage information that needs LLM review
    # Extended with coffee-specific dosage keywords (Vietnamese + English)
    dosage_keywords = [
        "ppm", "mg/l", "mg/kg", "concentration", "dosage", "application rate",
        "ml", "grams", "kg", "l/ha", "kg/ha", "g/ha",
        "liều lượng", "nồng độ", "pha", "phun", "hòa tan",
        "EC", "WP", "WG", "SC", "SL",   # formulation codes on Vietnamese labels
    ]
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
    # Use JSON-schema output (dict) then validate ourselves to avoid runtime
    # serializer warnings when tracing/checkpointing pydantic objects.
    structured_llm = llm.with_structured_output(SafetyAudit.model_json_schema())
    
    banned_list = ", ".join(BANNED_SUBSTANCES)
    
    # Format environmental context for safety considerations
    env_context = ""
    if env_state:
        weather = env_state.get("weather", {})
        env_warnings = []
        
        if weather.get("forecast_rain_24h"):
            env_warnings.append("Rain forecast within 24 h — all pesticide/fungicide spraying MUST be postponed (runoff & efficacy)")
        
        if weather.get("wind_speed_kmh", 0) > 15:
            env_warnings.append(f"High wind ({weather.get('wind_speed_kmh')} km/h) — foliar spray prohibited (drift, off-target contamination)")
            
        temp = weather.get("air_temp_c", 25)
        if temp > 35:
            env_warnings.append(f"Extreme heat ({temp} °C) — volatile pesticides (e.g. sulphur-based) banned; risk of phytotoxicity on coffee leaves")
        elif temp < 15:
            env_warnings.append(f"Low temperature ({temp} °C) — systemic fungicide uptake impaired; consider contact alternatives")
        
        humidity = weather.get("humidity_pct", 50)
        if humidity > 85:
            env_warnings.append(
                f"Very high humidity ({humidity} %) — peak Leaf Rust (Hemileia vastatrix) sporulation conditions. "
                "IF a fungicide is recommended, REQUIRE a shortened spray interval of 7 days (7 ngày) per WASI protocol. "
                "Fail the audit ONLY if the response recommends a fungicide WITHOUT specifying a 7-day interval."
            )
        elif humidity > 80:
            env_warnings.append(f"Elevated humidity ({humidity} %) — elevated fungal disease pressure on coffee; confirm fungicide schedule is current.")
        
        if env_warnings:
            env_context = "\n\nEnvironmental Safety & Compliance Rules:\n" + "\n".join(f"  - {w}" for w in env_warnings)
    
    system = f"""You are a pesticide safety and regulatory compliance auditor for **Vietnamese coffee production**.
Your role is to flag ONLY real violations — do not narrate passing checks.

IMPORTANT OUTPUT RULES:
- `issues_found` must contain ONLY items that actually violate a rule.
- If a check passes, do NOT include it in `issues_found`.
- If all checks pass, return `is_safe_and_compliant=true` and an empty `issues_found` list.
- Do NOT include section headers, pass confirmations, or audit narration in `issues_found`.

BANNED / RESTRICTED SUBSTANCES (PPD List + Stockholm POPs): {banned_list}

Audit the response against these requirements and flag ONLY actual violations:

1. BANNED SUBSTANCES
   Flag if and only if a banned substance from the list above is clearly recommended.
   Do NOT flag if the substance is merely mentioned in a warning context.

2. DOSAGE REALISM — UNIT-AWARE CHECK (CRITICAL)
   Vietnamese coffee spray equipment uses 400–800 L water/ha.
   Label dosages in ml/L or ml/tank MUST be converted to L/ha before comparing:
     Formula: (ml_per_25L ÷ 25) × 800 = ml_per_ha ÷ 1000 = L/ha
     Example: 15 ml per 25 L water → (15/25)×800 = 480 ml/ha = 0.48 L/ha ✓ COMPLIANT
     Example: 20 ml per 25 L water → (20/25)×800 = 640 ml/ha = 0.64 L/ha ✓ COMPLIANT

   Typical upper limits (L/ha or kg/ha):
     • Contact fungicides (Mancozeb 80WP, Copper Hydroxide 77WP): 1.5–2.5 kg/ha
     • Systemic fungicides (Propiconazole 25EC, Azoxystrobin 25SC, Amistar Top 325SC): 0.5–1.0 L/ha
     • Insecticides for CBB (Abamectin 1.8EC): 0.5–1.0 L/ha at 1:400 dilution
     • Foliar fertiliser: 0.3–0.5% solution

   ONLY flag if the CONVERTED L/ha value is ≥ 5× the typical upper limit.
   If the dosage unit is ml/L or ml/25L and you cannot convert it exactly,
   assume compliance unless the concentration clearly exceeds 5ml/L (=4 L/ha at 800L/ha).
   Do NOT flag dosages that fall within or near the typical range.
   Do NOT flag if you cannot determine the exact L/ha value from the given units.

3. PHI COMPLIANCE
   Flag ONLY if PHI is completely absent from a pesticide recommendation.
   Correct PHI values by fungicide type (do NOT flag these):
     • CONTACT fungicides (Copper Hydroxide, Mancozeb, Bordeaux mixture): 7–10 d ✓
     • SYSTEMIC fungicides (Azoxystrobin, Propiconazole, Tebuconazole): 14–21 d ✓
     • BIOLOGICAL agents (Bacillus subtilis, Trichoderma): 0 d ✓
   Flag ONLY if PHI is omitted entirely or is clearly shorter than the above ranges.

4. MRL / EXPORT COMPLIANCE
   Flag ONLY if the response explicitly mentions export, EU market, premium retail,
   or international certification AND fails to note relevant MRL limits.
    Do NOT flag MRL for general domestic-use recommendations.

    Runtime context gate from caller:
      • `EXPORT_CONTEXT_DETECTED={export_context}`
      • If false, missing MRL is advisory only and MUST NOT be a blocking violation.

5. ENVIRONMENTAL CONDITIONS
{env_context}
   Flag chemical spray recommendations ONLY under these hard conditions:
     • Rain forecast within 24 h (runoff risk)
     • Wind speed > 15 km/h (drift risk)
     • Temperature > 35 °C (volatilisation risk)
   High humidity (> 80–85 %) is NOT a spray ban — it is when fungicide IS needed.
   Do NOT flag fungicide recommendations under high humidity alone.

6. PPE WARNING
   Flag ONLY if the response recommends a chemical with NO mention of any protective
   equipment whatsoever (gloves, mask, or boots). A brief mention is sufficient.

7. '4 ĐÚNG' COMPLETENESS
   Flag ONLY if the response recommends a treatment but omits BOTH the chemical AND
   the dosage. 'Right Time' means disease stage timing (not time of day) — do not flag
   if the response says to apply at early infection signs or during dry weather.

Remember: return ONLY real violations in `issues_found`. Passing checks must NOT appear."""
    
    audit_prompt = ChatPromptTemplate.from_messages([
        ("system", system),
        ("human", """Question: {question}

        Generated Response:
        {generation}

        Audit this response for safety and regulatory compliance. Is it safe and compliant?""")
    ])
    
    audit_chain = audit_prompt | structured_llm
    
    try:
        audit_payload = audit_chain.invoke({
            "question": question,
            "generation": generation
        })
        audit_result = SafetyAudit.model_validate(audit_payload)
    except ValidationError as e:
        logger.warning("[SAFETY AUDIT] Validation failed for structured output: %s", e)
        if issues:
            return {"safety_passed": False, "safety_issues": issues}
        return {"safety_passed": True, "safety_issues": []}
    except Exception as e:
        logger.warning("[SAFETY AUDIT] Structured output call failed: %s — defaulting to PASS", e)
        # If LLM fails but we had hard issues, fail it
        if issues:
            return {"safety_passed": False, "safety_issues": issues}
        return {"safety_passed": True, "safety_issues": []}

    # ── Post-process: strip passing narration from issues_found ──────────────
    llm_violations = filter_real_violations(audit_result.issues_found)
    llm_violations = drop_non_blocking_mrl_issues(llm_violations, export_context)
    issues = drop_non_blocking_mrl_issues(issues, export_context)

    # If the LLM listed only passing statements, the response is actually compliant
    # even if is_safe_and_compliant was set to False by the model.
    actually_compliant = audit_result.is_safe_and_compliant or (not llm_violations)

    all_issues = issues + llm_violations

    if actually_compliant and not issues:
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
