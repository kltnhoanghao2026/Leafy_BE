"""
Standalone Plan Generation Graph Agent

A planning-only subgraph that mirrors the planning path from rag_agent.py
(lines 177–224), with these differences:
  - No intent/clarification/history-summarization overhead
  - Conditional skip of web search when reranked docs are sufficient (score ≥ 0.7)
  - Web search results filtered to keep only score > 0.7
  - Safety audit + refinement loop identical to the main graph

Flow:
    env_state → hybrid_search → reranker
                                       ↓
                                 check_doc_quality
                                   ├─ sufficient → planner
                                   └─ insufficient → web_search_plan → planner
                                                                   ↓
                                                             safety_audit
                                                               ↓
                                                     check_safety_compliance
                                                       ├─ safe  → END
                                                       └─ unsafe → refine
                                                                       ↓
                                                              check_refinement_limit
                                                               ├─ retry_plan_search → web_search_plan (loop)
                                                               └─ complete         → END (fallback)
"""

import logging
import os
from typing import Optional

from langgraph.graph import END, StateGraph

from app.agents.rag_state import GraphState

logger = logging.getLogger(__name__)

# ── Node imports ────────────────────────────────────────────────────────────────

from app.nodes.env_state_node import fetch_env_state
from app.nodes.hybrid_search_node import hybrid_search
from app.nodes.reranker_node import rerank_documents
from app.nodes.web_search_node import web_search
from app.nodes.planner import planner
from app.nodes.safety_auditor_node import safety_auditor
from app.nodes.refinement_node import refinement

# ── Shared conditional edge functions (imported from rag_agent) ────────────────
# check_safety_compliance and check_refinement_limit are defined in rag_agent.py
# to avoid duplication. Both agents share the same safety logic.

from app.agents.rag_agent import check_safety_compliance, check_refinement_limit


# ── Node debug wrapper ──────────────────────────────────────────────────────────
# Wraps every node function with a logger that dumps the incoming state
# before the node runs.  Useful for tracing exactly what data each step receives.

import functools


def _safe_truncate(value, max_len=300):
    s = str(value)
    return s[:max_len] + "..." if len(s) > max_len else s


def _summarise_state(state: dict) -> str:
    """Format the most interesting state fields for the debug log line."""
    parts = []
    for key in (
        "question", "search_query", "path_type", "forced_route",
        "intent", "needs_clarification",
        "documents", "candidate_docs", "reranked_docs",
        "generation", "web_search_results",
        "safety_passed", "safety_issues", "refinement_count",
        "generated_plan", "plant_id",
        "env_state", "confidence_score", "completeness_score",
    ):
        if key not in state:
            continue
        val = state[key]

        if key in ("documents", "candidate_docs", "reranked_docs"):
            parts.append(f"{key}=[{len(val)} docs]")
        elif key in ("web_search_results",):
            parts.append(f"{key}=[{len(val)} results]")
        elif key in ("generation",):
            parts.append(f"{key}={_safe_truncate(val, 120)}")
        elif key in ("safety_issues",):
            parts.append(f"{key}={val!r}")
        elif key in ("env_state",) and isinstance(val, dict):
            parts.append(f"{key}={list(val.keys())}")
        elif key in ("generated_plan",) and isinstance(val, dict):
            parts.append(f"{key}={list(val.keys())}")
        else:
            parts.append(f"{key}={_safe_truncate(val, 80)}")
    return " | ".join(parts)


def _wrap_node(name: str, fn):
    """Return a wrapped version of *fn* that logs the state before each call."""
    @functools.wraps(fn)
    def wrapper(state: GraphState) -> dict:
        logger.debug("[PIPELINE] ▶ entering node=%s | %s", name, _summarise_state(state))
        result = fn(state)
        logger.debug(
            "[PIPELINE] ◀ exiting  node=%s | documents=%s | generation_len=%s",
            name,
            len(result.get("documents", [])),
            len(result.get("generation", "")),
        )
        return result
    return wrapper


def _apply_debug_wrapping(nodes: list[tuple[str, callable]]):
    """
    Replace every (name, fn) pair in *nodes* with a debug-wrapped version.
    Accepts the same list format that StateGraph.add_node accepts.
    """
    return [(name, _wrap_node(name, fn)) for name, fn in nodes]


def _log_conditional_edge(node_name: str, fn, label: str = ""):
    """Wrap a conditional-edge function so its return value is also logged."""
    @functools.wraps(fn)
    def wrapper(state: GraphState) -> str:
        result = fn(state)
        logger.debug(
            "[PIPELINE] ? edge from node=%s → %s | %s",
            node_name, result, _summarise_state(state),
        )
        return result
    return wrapper


# ── Conditional edge debug helpers ────────────────────────────────────────────

def log_conditional_edge(node_name: str, fn, label: str):
    """Wrap a conditional-edge function so its return value is also logged."""
    @functools.wraps(fn)
    def wrapper(state: GraphState) -> str:
        result = fn(state)
        logger.debug(
            "[PIPELINE] ? edge from node=%s → %s | state_summary=%s",
            node_name, result, _summarise_state(state),
        )
        return result
    return wrapper


# ── Constants ──────────────────────────────────────────────────────────────────

_DOC_SCORE_THRESHOLD = 0.7
_WEB_SCORE_THRESHOLD = 0.7


# ── Conditional Edge Functions ───────────────────────────────────────────────

def check_doc_quality(state: GraphState) -> str:
    """
    Route based on the best rerank score after hybrid search.

    - score >= 0.7  → sufficient; skip web search, go straight to planner
    - score < 0.7   → insufficient; invoke web_search_plan first
    - no docs       → insufficient
    """
    documents = state.get("documents", [])
    if not documents:
        logger.info("[PLAN AGENT] No documents retrieved → invoking web search")
        return "insufficient"

    best_score = max(
        (doc.metadata.get("rerank_score", 0.0) for doc in documents),
        default=0.0,
    )
    if best_score >= _DOC_SCORE_THRESHOLD:
        logger.info(
            "[PLAN AGENT] Doc quality sufficient (best_score=%.2f) → skipping web search",
            best_score,
        )
        return "sufficient"
    logger.info(
        "[PLAN AGENT] Doc quality insufficient (best_score=%.2f) → invoking web search",
        best_score,
    )
    return "insufficient"


def web_search_plan(state: GraphState) -> dict:
    """
    Thin wrapper around the standard web_search node that additionally
    filters results to keep only those with score > 0.7.
    """
    raw = web_search(state)
    results = raw.get("web_search_results") or []
    filtered = [r for r in results if r.get("score", 0.0) > _WEB_SCORE_THRESHOLD]
    if len(results) != len(filtered):
        logger.info(
            "[PLAN AGENT] Web search: %d → %d results after score > %.2f filter",
            len(results), len(filtered), _WEB_SCORE_THRESHOLD,
        )
    return {**raw, "web_search_results": filtered}


# ── Disease Profile Table (single source of truth) ─────────────────────────────
# Mirrors plan_controller._run_plan_generation. All plan generation logic
# (graph, service, controller) should import from here — NOT from plan_controller.

# ── Disease Profile Table (mirrors plan_controller._run_plan_generation) ───────────
# All plan generation logic (graph, service, controller) imports from here.
# Consolidated to the 4 ML-supported diseases: phoma, rust, miner, red_spider_mite

DISEASE_PROFILES = {
    "phoma": {
        "vn": "bệnh khô cành khô quả nấm Phoma Phoma costarricensis chết ngọn rụng quả",
        "pathogen": "Phoma costarricensis (syn. Colletotrichum)",
        "type": "fungal",
        "web_kw": "bệnh khô cành khô quả Phoma cà phê Việt Nam phòng trừ",
    },
    "rust": {
        "vn": "bệnh gỉ sắt rỉ sắt nấm Hemileia vastatrix đốm vàng mặt dưới lá",
        "pathogen": "Hemileia vastatrix (Basidiomycota: Pucciniales)",
        "type": "fungal",
        "web_kw": "bệnh gỉ sắt rỉ sắt Hemileia vastatrix cà phê Tây Nguyên phòng trừ",
    },
    "miner": {
        "vn": "sâu vẽ bùa sâu đục lá cà phê Leucoptera coffeella sâu đào lá",
        "pathogen": "Leucoptera coffeella (Lepidoptera: Lyonetiidae)",
        "type": "insect",
        "web_kw": "sâu vẽ bùa sâu đục lá cà phê Leucoptera coffeella phòng trừ thuốc",
    },
    "leaf miner": {
        "vn": "sâu vẽ bùa sâu đục lá cà phê Leucoptera coffeella sâu đào lá",
        "pathogen": "Leucoptera coffeella (Lepidoptera: Lyonetiidae)",
        "type": "insect",
        "web_kw": "sâu vẽ bùa sâu đục lá cà phê Leucoptera coffeella phòng trừ thuốc",
    },
    "leaf_rust": {
        "vn": "bệnh gỉ sắt rỉ sắt nấm Hemileia vastatrix đốm vàng mặt dưới lá",
        "pathogen": "Hemileia vastatrix (Basidiomycota: Pucciniales)",
        "type": "fungal",
        "web_kw": "bệnh gỉ sắt rỉ sắt Hemileia vastatrix cà phê Tây Nguyên phòng trừ",
    },
    "coffee leaf rust": {
        "vn": "bệnh gỉ sắt rỉ sắt nấm Hemileia vastatrix đốm vàng mặt dưới lá",
        "pathogen": "Hemileia vastatrix (Basidiomycota: Pucciniales)",
        "type": "fungal",
        "web_kw": "bệnh gỉ sắt rỉ sắt Hemileia vastatrix cà phê Tây Nguyên phòng trừ",
    },
    "red spider mite": {
        "vn": "nhện đỏ nhện đỏ hai chấm Tetranychus urticae nhện hại cà phê",
        "pathogen": "Tetranychus urticae / Oligonychus coffeae (Acari: Tetranychidae)",
        "type": "mite",
        "web_kw": "nhện đỏ Tetranychus urticae nhện hại cà phê phòng trừ thuốc diệt nhện",
    },
    "spider mite": {
        "vn": "nhện đỏ nhện đỏ hai chấm Tetranychus urticae nhện hại cà phê",
        "pathogen": "Tetranychus urticae / Oligonychus coffeae (Acari: Tetranychidae)",
        "type": "mite",
        "web_kw": "nhện đỏ Tetranychus urticae nhện hại cà phê phòng trừ thuốc diệt nhện",
    },
}

_TYPE_RETRIEVAL_KW = {
    "fungal": "điều trị phòng trừ nấm thuốc trừ nấm liều lượng quy trình phun phác đồ",
    "insect": "phòng trừ sâu thuốc trừ sâu mật độ ngưỡng kinh tế lịch phun",
    "mite": "phòng trừ nhện acaricide thuốc diệt nhện phun mặt dưới lá luân phiên",
}

# ── Disease Management Policies (focused on the 4 supported diseases) ─────────
# This is the single source of truth for all disease-specific rules.
# plan_agent.build_question() injects policy context into the LLM prompt.
# plan_generation_service.validate_supported_disease() enforces this list at the API layer.

DISEASE_POLICIES: dict[str, dict] = {
    "phoma": {
        "vietnamese": "Bệnh Khô Cành / Khô Quả",
        "scientific": "Phoma costarricensis (syn. Colletotrichum)",
        "type": "fungal",
        "pathogen": "Phoma costarricensis",
        "avoid_overhead_irrigation": True,
        "requires_sanitation": True,
        "preferred_spray_interval_days": [7, 14],
        "frac_rotation_required": True,
        "humidity_sensitive": True,
        "organic_options": ["Copper hydroxide", "Mancozeb"],
        "ml_keywords": ["phoma"],
        "vn_keywords": ["khô cành", "khô quả", "phoma", "chết ngọn", "rụng quả"],
    },
    "rust": {
        "vietnamese": "Bệnh Rỉ Sắt",
        "scientific": "Hemileia vastatrix",
        "type": "fungal",
        "pathogen": "Hemileia vastatrix",
        "avoid_overhead_irrigation": True,
        "requires_sanitation": True,
        "preferred_spray_interval_days": [10, 14],
        "frac_rotation_required": True,
        "humidity_sensitive": True,
        "organic_options": ["Bordeaux mixture", "Copper-based fungicides"],
        "ml_keywords": ["rust"],
        "vn_keywords": ["gỉ sắt", "rỉ sắt", "rỉ lá", "gỉ lá", "leaf rust", "coffee leaf rust"],
    },
    "miner": {
        "vietnamese": "Sâu Vẽ Bùa",
        "scientific": "Leucoptera coffeella",
        "type": "insect",
        "pathogen": "Leucoptera coffeella (Lepidoptera: Lyonetiidae)",
        "avoid_overhead_irrigation": False,
        "requires_sanitation": True,
        "preferred_spray_interval_days": [7, 14],
        "frac_rotation_required": False,
        "humidity_sensitive": False,
        "organic_options": ["Neem oil", "Spinosad", "Bacillus thuringiensis"],
        "ml_keywords": ["leaf_miner", "leaf miner", "miner"],
        "vn_keywords": ["sâu vẽ bùa", "sâu đục lá", "leaf miner"],
    },
    "red_spider_mite": {
        "vietnamese": "Nhện Đỏ",
        "scientific": "Tetranychus urticae / Oligonychus coffeae",
        "type": "mite",
        "pathogen": "Tetranychus urticae",
        "avoid_overhead_irrigation": False,
        "requires_sanitation": True,
        "preferred_spray_interval_days": [7, 10],
        "frac_rotation_required": False,
        "humidity_sensitive": False,
        "organic_options": ["Neem oil", "Sulfur", "Predatory mite release (Phytoseiidae)"],
        "ml_keywords": ["red spider mite", "spider_mite", "spider mite"],
        "vn_keywords": ["nhện đỏ", "nhen do", "nhện hại"],
    },
}

SUPPORTED_DISEASES = list(DISEASE_POLICIES.keys())


def get_policy(disease_name: str) -> dict | None:
    """
    Return the DISEASE_POLICIES entry for a disease, or None if not supported.
    Uses the same bidirectional matching as resolve_profile.
    """
    profile = resolve_profile(disease_name)
    if profile.get("type") == "unknown":
        return None
    disease_key = _find_policy_key(disease_name)
    if disease_key:
        return DISEASE_POLICIES.get(disease_key)
    return None


def _find_policy_key(disease_name: str) -> str | None:
    """
    Find the DISEASE_POLICIES key that matches the given disease name.
    Matches against policy keys, ml_keywords, and vn_keywords.
    """
    import unicodedata

    def strip_diacritics(text: str) -> str:
        return "".join(
            c for c in unicodedata.normalize("NFD", text)
            if unicodedata.category(c) != "Mn"
        )

    disease_lower = disease_name.lower().replace("_", " ").strip()
    disease_nd = strip_diacritics(disease_lower)

    for key, policy in DISEASE_POLICIES.items():
        # Match exact key
        if key in disease_lower or key in disease_nd:
            return key
        # Match ml_keywords
        for kw in policy.get("ml_keywords", []):
            if kw in disease_lower or kw in disease_nd:
                return key
        # Match vn_keywords
        for kw in policy.get("vn_keywords", []):
            kw_nd = strip_diacritics(kw)
            if kw in disease_lower or kw_nd in disease_nd or disease_nd in kw_nd:
                return key

    return None


def resolve_profile(disease_name: str) -> dict:
    """
    Match a disease name against the profile table using bidirectional keyword matching.

    Supports:
      - English keys:   "rust", "leaf rust", "coffee leaf rust", "phoma", "miner", etc.
      - Vietnamese:     "gỉ sắt", "rỉ sắt", "rỉ lá", "khô cành", "sâu vẽ bùa", "nhện đỏ", etc.
      - Normalised form: "gia_sat" → "gỉ sắt" (underscore → diacritics)
    """
    # Normalise: replace underscores and strip diacritics for broad matching
    import unicodedata

    def strip_diacritics(text: str) -> str:
        return "".join(
            c for c in unicodedata.normalize("NFD", text)
            if unicodedata.category(c) != "Mn"
        )

    disease_lower = disease_name.lower().replace("_", " ").strip()
    disease_nd = strip_diacritics(disease_lower)  # no-diacritics version

    # Bidirectional matching: check key in disease_lower OR vn text in disease_lower
    for key, prof in DISEASE_PROFILES.items():
        if key in disease_lower:
            return prof
        # Also match Vietnamese keywords embedded in the vn field
        vn_text = prof.get("vn", "")
        vn_nd = strip_diacritics(vn_text.lower())
        # Match if the profile key is a substring of the disease name, OR
        # the disease name is a substring of the profile's diacritic-free
        # Vietnamese text (handles Vietnamese input like "sau duc la" which is
        # contained within the "miner" profile vn text "sau ve bua sau duc la...").
        if key in disease_nd or disease_nd in vn_nd:
            return prof

    # Vietnamese keyword → profile key map (focused on the 4 supported diseases)
    _VIETNAMESE_PROFILE_MAP = {
        "gỉ sắt": "rust", "gi sat": "rust", "ri sat": "rust",
        "rỉ sắt": "rust", "rỉ lá": "rust", "ri la": "rust",
        "gỉ lá": "rust",
        "khô cành": "phoma", "kho cành": "phoma", "kho canh": "phoma",
        "khô quả": "phoma", "kho qua": "phoma",
        "phoma": "phoma",
        "sâu vẽ bùa": "miner", "sau ve bua": "miner",
        "sâu đục lá": "miner", "sau duc la": "miner",
        "leaf miner": "miner", "coffee leaf miner": "miner",
        "nhện đỏ": "spider mite", "nhen do": "spider mite", "nhện hại": "spider mite",
        "bọ phấn": "insect", "rệp": "insect",
    }
    for viet_key, profile_key in _VIETNAMESE_PROFILE_MAP.items():
        if viet_key in disease_lower or viet_key in disease_nd:
            if profile_key in DISEASE_PROFILES:
                return DISEASE_PROFILES[profile_key]

    return {
        "vn": f"bệnh {disease_name} cà phê",
        "pathogen": disease_name,
        "actives": "thuốc bảo vệ thực vật theo khuyến cáo của PPD Việt Nam",
        "phi": "14 ngày",
        "type": "unknown",
        "web_kw": f"bệnh {disease_name} cà phê Việt Nam phòng trừ điều trị",
    }


# ── Question Builder ────────────────────────────────────────────────────────────
# Mirrors plan_controller._run_plan_generation question construction.

def build_question(
    disease_name: str,
    profile: dict,
    severity_note: str,
    severity_level: Optional[str],
    language: str,
) -> str:
    """
    Build a severity-aware, RAG-first treatment-plan question.

    Design principles:
      - Disease identity and severity are provided as intent framing only.
      - NO specific chemical names, dosages, or PHI values are hard-coded.
        The model MUST extract all product recommendations from the retrieved
        agronomic documents and web sources.
      - The prompt tells the model exactly what schema to produce and what
        constraints every TREATMENT_APPLICATION event must satisfy.
      - DISEASE_POLICIES are injected as mandatory rules the model must follow.
    """
    # ── Normalise severity ─────────────────────────────────────────────────────
    sev = (severity_level or "LOW").upper()
    if sev not in ("LOW", "MEDIUM", "HIGH"):
        sev = "LOW"

    disease_type = profile["type"]
    policy_key = _find_policy_key(disease_name)
    policy = DISEASE_POLICIES.get(policy_key) if policy_key else None

    # ── Policy rules section (inject into prompt if available) ───────────────
    policy_rules_section = ""
    if policy:
        spray_days = (
            f"{policy['preferred_spray_interval_days'][0]}"
            f"–{policy['preferred_spray_interval_days'][1]}"
        )
        frac_note = (
            " FRAC rotation IS required — alternate between different fungicide "
            "classes (e.g. triazoles + strobilurins) to prevent resistance."
            if policy["frac_rotation_required"]
            else ""
        )
        organic_list = ", ".join(f"'{o}'" for o in policy["organic_options"])
        humidity_note = (
            " HIGH HUMIDITY alert — increase spray frequency during rainy/humid periods. "
            "Do NOT use overhead irrigation within 24h of any spray event."
            if policy["humidity_sensitive"]
            else ""
        )
        policy_rules_section = f"""\

MANDATORY DISEASE-SPECIFIC RULES (enforce all of the following):
  • Disease: {policy["vietnamese"]} ({policy["scientific"]})
  • Spray interval: every {spray_days} days{frac_note}.
  • Avoid overhead irrigation within 24h of any spray event: {'YES' if policy["avoid_overhead_irrigation"] else 'not required for this pest type'}.
  • Sanitation (remove and destroy infected plant material): REQUIRED.
  • Organic/bio options: {organic_list}.{humidity_note}
"""

    # ── Severity framing (intent only — no protocol specifics) ─────────────────
    if sev == "HIGH":
        urgency_note = (
            "Severity: HIGH — rapid spread expected. "
            "Schedule the first treatment within 1–2 days. "
            "Include a QUARANTINE event for heavily infected plants. "
            "Recommend stumping / canopy reset if >30% canopy is affected."
        )
        horizon_note = "Plan horizon: 28–45 days."
    elif sev == "MEDIUM":
        urgency_note = (
            "Severity: MEDIUM — visible symptoms, limited spread. "
            "Schedule the first treatment within 3 days. "
            "Consider targeted PRUNING to remove the most affected shoots."
        )
        horizon_note = "Plan horizon: 21–30 days."
    else:
        urgency_note = (
            "Severity: LOW — early-stage, isolated symptoms. "
            "First treatment within the first week. "
            "Emphasise prevention and plant immunity alongside curative action."
        )
        horizon_note = "Plan horizon: 14–21 days."

    # ── Disease-type event guidance (policy-aware spray intervals) ─────────────────
    spray_days_str = ""
    if policy:
        interval = policy["preferred_spray_interval_days"]
        spray_days_str = f"Apply sprays every {interval[0]}–{interval[1]} days apart."

    if disease_type == "fungal":
        event_guidance = (
            f"Disease type: FUNGAL. {spray_days_str}\n"
            "Fungal protocols must cover: fungicide spray schedule (minimum 2 applications, "
            "rotating active ingredients across FRAC classes), scouting for humidity-driven spread, "
            "and NUTRITION to rebuild plant immunity. "
            "Do NOT use overhead irrigation within 24h of any spray event."
        )
    elif disease_type == "insect":
        event_guidance = (
            f"Disease type: INSECT PEST. {spray_days_str}\n"
            "Insect protocols must cover: insecticide spray schedule (minimum 2 applications, "
            "rotating mode-of-action classes), economic threshold monitoring at each SCOUTING, "
            "and NUTRITION to repair leaf damage. "
            "Apply sprays early morning or late afternoon to minimise bee impact."
        )
    else:
        event_guidance = (
            f"Disease type: MITE / ACARINA. {spray_days_str}\n"
            "Mite protocols must cover: acaricide spray schedule (minimum 2 applications, "
            "rotating mode-of-action), monitoring live mite density per leaf at each SCOUTING, "
            "and thorough spray coverage on the UNDER-SIDE of leaves. "
            "Do NOT apply during peak sunlight — mites drop off leaves in direct sun."
        )

    if severity_note:
        severity_note = f"\nVisual severity note: {severity_note}"

    # ── Chemical / dosage constraints (inform the model, no specifics) ──────────
    chemical_constraints = """\
TREATMENT_APPLICATION events — MANDATORY CONSTRAINTS:
  • Extract product name(s), active ingredient(s), and dosage (L/ha or kg/ha) from the
    retrieved agronomic documents and web sources below. Do NOT invent products or dosages.
  • Also express every dosage as the field-mix equivalent for a 25L knapsack sprayer:
    ml/25L = (L/ha × 25000) / 800
  • State the Pre-Harvest Interval (PHI) in days for every product.
  • List full PPE requirements (respirator, chemical-resistant gloves, rubber boots,
    long-sleeved coveralls, eye protection).
  • Do NOT recommend products that are not present in the retrieved context.
  • Only recommend products on Vietnam's approved PPD (Plant Protection Department) list."""

    # ── General plan structure (severity-adaptive event count) ─────────────────
    if sev == "HIGH":
        event_count_note = (
            "Include at minimum: DISEASE_DETECTED, QUARANTINE, 3× TREATMENT_APPLICATION "
            "(days 0–1, 7–10, 14–17), 2× SCOUTING between sprays, PRUNING if >30% canopy, "
            "2× NUTRITION, 4× IRRIGATION, WEED_CONTROL, and HEALTH_RECOVERY."
        )
    elif sev == "MEDIUM":
        event_count_note = (
            "Include at minimum: DISEASE_DETECTED, 2× TREATMENT_APPLICATION "
            "(days 0–3, 10–14), 4× SCOUTING, 2× NUTRITION, 4× IRRIGATION, "
            "WEED_CONTROL, optional PRUNING, and HEALTH_RECOVERY."
        )
    else:
        event_count_note = (
            "Include at minimum: DISEASE_DETECTED, 2× TREATMENT_APPLICATION "
            "(days 0–7, 14–21), 2× SCOUTING, 2× NUTRITION, 3× IRRIGATION, "
            "and HEALTH_RECOVERY."
        )

    parts = [
        f"Generate a treatment plan for coffee (Coffea canephora / arabica) in Vietnam "
        f"for the disease: {disease_name}.{severity_note}",
        f"\nSEVERITY: {sev}. {urgency_note} {horizon_note}",
        f"\n{event_guidance}",
        f"\n{event_count_note}",
        f"\n{chemical_constraints}",
        policy_rules_section,  # injects disease-specific mandatory rules if policy is known
        "\nMANDATORY SCHEMA FIELDS for every event:",
        "  • eventType, daysFromStart (integer, 0-based), durationDays, targetType, note, description.",
        "  • For TREATMENT_APPLICATION: phiDays (integer), ppeRequired (string), estimatedCost.",
        "  • For NUTRITION, IRRIGATION, SCOUTING: include tasks (never null).",
        "  • For TREATMENT_APPLICATION: include tasks [prepare tank → apply → clean equipment].",
        f"\nOutput language: {language} — all notes, descriptions, and task titles in {language}.",
    ]
    return "\n".join(parts)


# ── Graph Builder ──────────────────────────────────────────────────────────────

def build_graph(checkpointer=None):
    """
    Build the planning-only subgraph.

    Args:
        checkpointer: An initialised AsyncSqliteSaver (or any BaseCheckpointSaver)
                      for persistent multi-turn memory. Pass None to disable persistence.
    """
    workflow = StateGraph(GraphState)

    # Add nodes with debug-wrapped wrappers
    for name, fn in _apply_debug_wrapping([
        ("env_state",     fetch_env_state),
        ("hybrid_search",  hybrid_search),
        ("reranker",      rerank_documents),
        ("web_search_plan", web_search_plan),
        ("planner",       planner),
        ("safety_audit",  safety_auditor),
        ("refine",       refinement),
    ]):
        workflow.add_node(name, fn)

    workflow.set_entry_point("env_state")

    # Linear flow
    workflow.add_edge("env_state", "hybrid_search")
    workflow.add_edge("hybrid_search", "reranker")

    # Doc-quality conditional split — wrap edge fn with debug logging
    workflow.add_conditional_edges(
        "reranker",
        _log_conditional_edge("reranker", check_doc_quality),
        {
            "sufficient": "planner",
            "insufficient": "web_search_plan",
        },
    )

    # web_search_plan always feeds into planner
    workflow.add_edge("web_search_plan", "planner")

    # Planner feeds into safety audit
    workflow.add_edge("planner", "safety_audit")

    # Safety decision — wrap edge fn with debug logging
    workflow.add_conditional_edges(
        "safety_audit",
        _log_conditional_edge("safety_audit", check_safety_compliance),
        {
            "safe": END,
            "unsafe": "refine",
        },
    )

    # Refinement loop — wrap edge fn with debug logging
    workflow.add_conditional_edges(
        "refine",
        _log_conditional_edge("refine", check_refinement_limit),
        {
            "retry_plan_search": "web_search_plan",
            "complete": END,
        },
    )

    return workflow.compile(checkpointer=checkpointer)


# Placeholder — replaced with a live graph during FastAPI startup.
# Matches rag_agent.py pattern: None at import time, populated in lifespan.
plan_app = None
