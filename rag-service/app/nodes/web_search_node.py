"""
Web Search Node (Vietnamese Context)

Uses Tavily API to search for recent agricultural information,
prioritizing Vietnamese government regulations, institutes, and local news.
"""

import os
import logging
from tavily import TavilyClient

from app.agents.rag_state import GraphState

logger = logging.getLogger(__name__)

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
    
    # Initialize Tavily client
    tavily_api_key = os.getenv("TAVILY_API_KEY")
    if not tavily_api_key or tavily_api_key == "your_tavily_api_key_here":
        logger.warning("[WEB SEARCH] Tavily API key not configured — skipping web search")
        return {
            "question": question,
            "web_search_results": [],
        }
    
    client = TavilyClient(api_key=tavily_api_key)
    
    # 1. Contextualize Query for Vietnam
    # If the user asks "How to treat Coffee Rust" without mentioning Vietnam,
    # we force the context to ensure we get local regulations, not Brazilian ones.
    search_query = question
    vn_keywords = ["vietnam", "việt nam", "vn", "đồng bằng sông cửu long", "tây nguyên"]
    if not any(k in question.lower() for k in vn_keywords):
        # Append context based on language detection (simple heuristic)
        if any(char for char in question if ord(char) > 127): # Has unicode (likely Vietnamese)
            search_query = f"{question} tại Việt Nam"
        else:
            search_query = f"{question} in Vietnam agriculture"
            
    logger.debug(f"[WEB SEARCH] Optimized Query: {search_query}")

    # Perform search
    try:
        search_results = client.search(
            query=search_query,
            search_depth="advanced",
            max_results=5,
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