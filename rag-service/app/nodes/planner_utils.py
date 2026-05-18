import re

# ── Plan horizon extraction ────────────────────────────────────────────────────

_DURATION_PATTERNS = [
    # Vietnamese
    (r'(\d+)\s*tháng',      30),   # n tháng → n * 30 days
    (r'(\d+)\s*tuần',        7),   # n tuần  → n * 7 days
    (r'(\d+)\s*ngày',         1),  # n ngày  → n days
    # English
    (r'(\d+)\s*month',       30),
    (r'(\d+)\s*week',         7),
    (r'(\d+)\s*day',          1),
]

def extract_plan_horizon_days(question: str) -> int:
    """
    Scan the question for an explicit duration request (e.g. '1 tháng', '2 tuần').
    Returns the horizon in days, or 0 if not found.
    """
    q = question.lower()
    for pattern, multiplier in _DURATION_PATTERNS:
        m = re.search(pattern, q)
        if m:
            return int(m.group(1)) * multiplier
    return 0


def build_event_density_guidance(horizon_days: int) -> str:
    """
    Return a tailored event-density block for the system prompt based on
    how many days the requested plan should cover.
    """
    if horizon_days <= 0:
        return ""

    # Derive recommended counts from the horizon
    irrigation_count   = max(2, horizon_days // 7)          # ~weekly
    nutrition_count    = max(1, horizon_days // 14)          # bi-weekly
    scouting_count     = max(2, horizon_days // 7)           # weekly
    weed_count         = max(1, horizon_days // 14)          # bi-weekly
    pruning_count      = 1 if horizon_days >= 14 else 0
    phenology_count    = max(1, horizon_days // 14)          # bi-weekly milestones

    # Compute the ideal interval between occurrences of each event type
    irr_interval   = max(1, horizon_days // irrigation_count)
    nutr_interval  = max(1, horizon_days // nutrition_count)
    scout_interval = max(1, horizon_days // scouting_count)
    weed_interval  = max(1, horizon_days // weed_count)

    lines = [
        f"PLAN HORIZON: {horizon_days} days.",
        "Distribute events EVENLY across the full period — do NOT cluster them at the start.",
        f"Minimum event counts for a {horizon_days}-day plan:",
        f"  • IRRIGATION       : ≥{irrigation_count} events, spaced ~{irr_interval} days apart",
        f"  • NUTRITION        : ≥{nutrition_count} events, spaced ~{nutr_interval} days apart",
        f"  • SCOUTING         : ≥{scouting_count} events, spaced ~{scout_interval} days apart",
        f"  • WEED_CONTROL     : ≥{weed_count} events, spaced ~{weed_interval} days apart",
    ]
    if pruning_count:
        lines.append(f"  • PRUNING          : ≥{pruning_count} event (schedule toward the end of the period)")
    lines += [
        f"  • PHENOLOGY        : ≥{phenology_count} milestone(s) (record growth-stage changes)",
        f"Total events expected: ≥{irrigation_count + nutrition_count + scouting_count + weed_count + pruning_count + phenology_count}",
        "",
        "durationDays rules:",
        "  • IRRIGATION, NUTRITION, SCOUTING, PHENOLOGY : set durationDays = 1",
        "  • WEED_CONTROL : 1–2 days depending on plot size",
        "  • PRUNING      : 1–3 days depending on canopy complexity",
        "",
        "Spacing formula: daysFromStart for occurrence N of an event with interval I = (N-1) * I",
        "Choose offsets that feel natural for the agronomic context — do not copy any fixed list.",
    ]
    return "\n".join(lines)
