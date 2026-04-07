"""
Web Search Node (Vietnamese Context)

Uses Tavily API to search for recent agricultural information,
prioritizing Vietnamese government regulations, institutes, and local news.
"""

import os
import logging
from typing import List
from tavily import TavilyClient

from app.agents.rag_state import GraphState

logger = logging.getLogger(__name__)

_LEGAL_TECH_TRIGGER_KEYWORDS = (
    "mrl", "residue", "maximum residue", "phi", "pre-harvest", "cách ly",
    "export", "eu", "usda", "japan", "compliance", "regulation", "reg. 396/2005",
    "banned", "restricted", "ppd", "circular",
)


def _derive_regulatory_focus_terms(safety_issues: List[str]) -> List[str]:
    """Map failed safety issues into extra legal/technical search keywords."""
    joined = " ".join((issue or "") for issue in safety_issues).lower()
    if not joined:
        return []

    focus_terms: List[str] = []

    if any(k in joined for k in ("mrl", "residue", "export", "eu", "usda", "reg. 396/2005")):
        focus_terms.extend([
            "MRL limits leaf rust treatment Vietnam export coffee",
            "EU Reg 396/2005 coffee pesticide residues",
            "USDA JANIS pesticide residue tolerance coffee",
        ])

    if any(k in joined for k in ("phi", "pre-harvest", "cách ly")):
        focus_terms.extend([
            "pre-harvest interval PHI fungicide coffee Vietnam",
            "thời gian cách ly thuốc bảo vệ thực vật cà phê",
        ])

    if any(k in joined for k in ("banned", "restricted", "paraquat", "chlorpyrifos", "fipronil")):
        focus_terms.extend([
            "Vietnam PPD banned pesticide list coffee Circular 03/2023",
            "danh mục thuốc bảo vệ thực vật cấm sử dụng trên cà phê",
        ])

    if any(k in joined for k in ("ppe", "gloves", "mask", "boots", "protective")):
        focus_terms.extend([
            "pesticide PPE requirement Vietnam agriculture",
            "quy định bảo hộ lao động phun thuốc bảo vệ thực vật",
        ])

    # Preserve order while deduplicating
    return list(dict.fromkeys(focus_terms))

# prioritized list of authoritative Vietnamese agricultural sources
APPROVED_DOMAINS = [
    "mard.gov.vn",          # Ministry of Agriculture and Rural Development
    "ppd.gov.vn",           # Plant Protection Department (Essential for Pesticide Law)
    "khuyennongvn.gov.vn",  # National Agricultural Extension Center
    "vaas.org.vn",          # Vietnam Academy of Agricultural Sciences
    "iasvn.org",            # Institute of Agricultural Sciences for Southern Vietnam
    "wasi.org.vn",          # Western Highlands AAFSI (The most critical for Coffee)
    "favri.org.vn",         # Fruit and Vegetable Research Institute
    "irri.org",             # International Rice Research Institute
    "fao.org",              # FAO (General standards)
    "nongnghiepmoitruong.vn", # Vietnam Agriculture Newspaper

    # --- Coffee Specific & Regional Authority ---
    "vicofa.org.vn",        # Vietnam Coffee - Cocoa Association (Market & disease trends)
    "lamdong.gov.vn",       # Lam Dong Portal (Specific "Sở NN&PTNT" for the coffee heartland)
    "daklak.gov.vn",        # Dak Lak Portal (Critical for local pest outbreaks)

    # --- Digital Repositories & Academic Search ---
    "vjst.vn",              # Vietnam Journal of Science and Technology
    "tapchicongthuong.vn",  # Industry and Trade Magazine (Often covers coffee export quality)
    "cesti.gov.vn",         # Center for Statistics and Science & Tech Info (HCM City)
    
    # --- Global Coffee Research (For RAG Context) ---
    "worldcoffeeresearch.org", # World Coffee Research (The best source for leaf rust resistance)
    "ico.org",                 # International Coffee Organization
    "sciencedirect.com",       # Open access papers for YOLO/MobileNet technical benchmarks
]

def web_search(state: GraphState) -> dict:
    """
    Perform web search using Tavily API focused on Vietnamese Agriculture.
    
    Used in the deep path when internal documents have low confidence
    or completeness. Searches for recent guidelines, permitted substances (PPD),
    and local best practices.
    
    Args:
        state: Current graph state with question
        
    Returns:
        Updated state with web_search_results
    """
    logger.info("[WEB SEARCH] Searching Tavily for: %.80s", state['question'])
    
    question = state["question"]
    safety_issues = state.get("safety_issues") or []
    refinement_count = state.get("refinement_count", 0)
    
    # Initialize Tavily client
    tavily_api_key = os.getenv("TAVILY_API_KEY")
    if not tavily_api_key or tavily_api_key == "your_tavily_api_key_here":
        logger.warning("[WEB SEARCH] Tavily API key not configured — skipping web search")
        return {
            "question": question,
            "web_search_results": [],
        }
    
    client = TavilyClient(api_key=tavily_api_key)
    
    # ── Coffee-domain query enrichment ───────────────────────────────────────
    # If the question does not already mention Vietnam or a Vietnamese keyword,
    # append geographic context so Tavily returns local regulations and outbreak data
    # rather than generic or Brazilian/Colombian results.
    search_query = question
    vn_keywords = ["vietnam", "việt nam", "vn", "tây nguyên", "đắk lắk", "lâm đồng",
                   "robusta", "arabica", "cà phê"]
    if not any(k in question.lower() for k in vn_keywords):
        if any(ord(c) > 127 for c in question):   # has Vietnamese unicode
            search_query = f"{question} cà phê Việt Nam Tây Nguyên"
        else:
            search_query = f"{question} coffee Vietnam Central Highlands"

    # Boost precision for coffee-specific sub-domains when the question is clearly
    # about coffee diseases, pests, or regulations.
    coffee_disease_kw = [
        "leaf rust", "gỉ sắt", "hemileia", "brown eye spot", "cercospora",
        "root rot", "phytophthora", "berry borer", "cbb", "hypothenemus",
        "white stem borer", "xylotrechus", "mealybug", "planococcus",
        "fertilizer", "phân bón", "npk", "potassium", "kali", "nitrogen",
        "pesticide", "fungicide", "thuốc", "chlorpyrifos", "mancozeb",
    ]
    is_coffee_specific = any(kw in question.lower() for kw in coffee_disease_kw)
    if is_coffee_specific and "coffee" not in search_query.lower() and "cà phê" not in search_query.lower():
        search_query = f"coffee {search_query}" if not any(ord(c) > 127 for c in question) else f"cà phê {search_query}"

    # Refinement-aware compliance enrichment:
    # if the previous answer failed the safety audit, force legal/technical terms
    # into the query so Tavily can surface regulatory sources (MRL/PHI/PPD tables).
    if refinement_count > 0 and safety_issues:
        focus_terms = _derive_regulatory_focus_terms(safety_issues)
        if not focus_terms and any(k in " ".join(safety_issues).lower() for k in _LEGAL_TECH_TRIGGER_KEYWORDS):
            focus_terms = [
                "Vietnam coffee pesticide regulation",
                "MRL PHI compliance",
            ]
        if focus_terms:
            search_query = f"{search_query} {' '.join(focus_terms)}"
            logger.info("[WEB SEARCH] Refinement-aware enrichment applied (%d terms)", len(focus_terms))

    logger.debug("[WEB SEARCH] Enriched query: %s", search_query)

    # Perform search
    try:
        search_results = client.search(
            query=search_query,
            search_depth="advanced",
            max_results=7 if refinement_count > 0 else 5,
            include_domains=APPROVED_DOMAINS,  # STRICT FILTERING for authority
            # Optional: exclude commercial marketplaces to avoid "product selling" spam
            # exclude_domains=["shopee.vn", "lazada.vn", "tiki.vn"] 
        )
        
        # Extract relevant information
        results = []
        for result in search_results.get("results", []):
            results.append({
                "title": result.get("title", ""),
                "url": result.get("url", ""),
                "content": result.get("content", ""),
                "score": result.get("score", 0.0),
            })
        
        logger.info("[WEB SEARCH] Found %d results", len(results))
        for i, result in enumerate(results):
            logger.debug("[WEB SEARCH] Result %d: %s (Source: %s)", i + 1, result['title'], result['url'])
        
        return {
            "question": question,
            "web_search_results": results,
        }
        
    except Exception as e:
        logger.error("[WEB SEARCH] Search failed: %s", e, exc_info=True)
        return {
            "question": question,
            "web_search_results": [],
        }