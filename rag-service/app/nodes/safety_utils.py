import logging
import re
from typing import List

from app.core.constants import PASS_PHRASES, EXPORT_CONTEXT_KEYWORDS, MRL_ISSUE_KEYWORDS

logger = logging.getLogger(__name__)

def filter_real_violations(issues: List[str]) -> List[str]:
    """
    Remove items from `issues_found` that are actually passing statements.
    Also deterministically filters dosage issues where ml/25L is within valid range.
    """
    real = []
    for issue in issues:
        lower = issue.lower()

        # 1. Pass-phrase filter
        if any(phrase in lower for phrase in PASS_PHRASES):
            logger.debug("[SAFETY AUDIT] Discarding false-positive issue: %s", issue[:80])
            continue

        # 2. Deterministic dosage unit filter
        ml_match = re.search(r'(\d+(?:\.\d+)?)\s*(?:-\s*(\d+(?:\.\d+)?))?\s*ml\s*(?:per|/)\s*25\s*[lL]', issue)
        if ml_match:
            ml_low = float(ml_match.group(1))
            ml_high = float(ml_match.group(2)) if ml_match.group(2) else ml_low
            lha_high = (ml_high / 25.0) * 800.0 / 1000.0
            if lha_high <= 5.0:  # within 5x upper limit
                logger.info(
                    "[SAFETY AUDIT] Discarding false dosage issue: %.1fml/25L = %.2f L/ha (compliant). Issue: %s",
                    ml_high, lha_high, issue[:100]
                )
                continue

        real.append(issue)
    return real

def is_export_context(question: str, generation: str) -> bool:
    combined = question.lower()
    return any(keyword in combined for keyword in EXPORT_CONTEXT_KEYWORDS)

def drop_non_blocking_mrl_issues(issues: List[str], export_context: bool) -> List[str]:
    if export_context:
        return issues

    filtered = []
    for issue in issues:
        lowered = issue.lower()
        if any(keyword in lowered for keyword in MRL_ISSUE_KEYWORDS):
            logger.info("[SAFETY AUDIT] Ignoring MRL-only issue in non-export context: %s", issue)
            continue
        filtered.append(issue)
    return filtered
