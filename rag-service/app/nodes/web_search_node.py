"""
Web Search Node (Vietnamese Context)

Uses Tavily API to search for recent agricultural information,
prioritizing Vietnamese government regulations, institutes, and local news.
"""

import os
import logging
from datetime import datetime
from typing import List
from tavily import TavilyClient
from langchain_core.messages import HumanMessage, SystemMessage

from app.agents.rag_state import GraphState
from app.core.ai_providers import get_gemini_flash

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

    if any(k in joined for k in ("banned", "restricted", "paraquat", "chlorpyrifos", "fipronil",
                                  "copper oxychloride", "apollo")):
        focus_terms.extend([
            "Vietnam PPD approved fungicide coffee leaf rust Circular 03/2023",
            "thuốc trừ nấm được phép dùng trên cà phê bệnh gỉ sắt danh mục PPD",
            "approved alternative fungicide coffee Hemileia vastatrix Vietnam",
        ])

    if any(k in joined for k in ("ppe", "gloves", "mask", "boots", "protective")):
        focus_terms.extend([
            "pesticide PPE requirement Vietnam agriculture",
            "quy định bảo hộ lao động phun thuốc bảo vệ thực vật",
        ])

    # Preserve order while deduplicating
    return list(dict.fromkeys(focus_terms))

_QUERY_REWRITE_SYSTEM = """\
You are an expert agricultural search query optimizer for Vietnamese coffee farming.
Given a user's conversational question, rewrite it as a concise, keyword-rich web \
search query (max 15 words) that will return authoritative technical results.

Current year: {current_year}

Rules:
- Remove filler words and politeness (e.g. "cho tôi", "bạn có thể", "please").
- Keep the core agronomic topic: disease, pest, treatment, fertilizer, regulation, etc.
- If the question asks for current statistics, production (sản lượng), prices (giá), or recent news, MUST append the current year ({current_year}) to the query.
- If the user asks for a PLAN, SCHEDULE, or TREATMENT, add terms like "quy trình", "kỹ thuật", "phác đồ điều trị", or "lịch chăm sóc" to find actionable step-by-step guides.
- MUST keep the query in Vietnamese to ensure we get local Vietnamese sources. Do not translate to English.
- Do NOT add fabricated facts or chemicals.
- Output ONLY the search query — no explanation, no quotes, no punctuation at the end.
"""

def _rewrite_query_for_search(question: str) -> str:
    """
    Use a fast LLM to convert a conversational user question into a concise,
    search-optimised query string.  Falls back to the raw question on any error.
    """
    try:
        llm = get_gemini_flash(temperature=0)
        system_prompt = _QUERY_REWRITE_SYSTEM.format(current_year=datetime.now().year)
        messages = [
            SystemMessage(content=system_prompt),
            HumanMessage(content=question),
        ]
        result = llm.invoke(messages)
        rewritten = result.content.strip()
        if rewritten:
            logger.info("[WEB SEARCH] Query rewritten: '%s' → '%s'", question[:60], rewritten)
            return rewritten
    except Exception as exc:
        logger.warning("[WEB SEARCH] Query rewrite failed (%s) — using raw question", exc)
    return question


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

    # ── Refinement-aware search override ─────────────────────────────────────
    # On refinement passes we skip LLM rewriting and build a purpose-specific
    # query targeting approved alternatives and regulatory approval lists so the
    # LLM receives different, compliant source material.
    if refinement_count > 0 and safety_issues:
        focus_terms = _derive_regulatory_focus_terms(safety_issues)
        if not focus_terms:
            focus_terms = [
                "Vietnam coffee approved pesticide alternative PPD regulation",
                "thuốc trừ nấm được phép sử dụng trên cà phê Việt Nam danh mục PPD",
            ]
        search_query = " ".join(focus_terms)
        logger.info(
            "[WEB SEARCH] Refinement pass %d — using solution-focused query (%d terms)",
            refinement_count,
            len(focus_terms),
        )
    else:
        # ── Plan-mode fast-path: use the pre-built Vietnamese disease keywords ─
        # When the plan controller sets `search_query` (Vietnamese disease
        # keywords), use it directly — it is already a compact, high-signal query
        # tuned for Qdrant retrieval AND short enough for Tavily (<400 chars).
        # This avoids feeding Tavily the full 8-section English plan directive
        # which causes BadRequestError (query too long).
        explicit_search_query = (state.get("search_query") or "").strip()
        if explicit_search_query:
            search_query = explicit_search_query[:390]  # hard safety cap
            logger.info("[WEB SEARCH] Plan mode — using dedicated search_query for Tavily")
        else:
            # ── LLM query rewriting (general chat path) ───────────────────────
            # When a clarification round-trip happened, the current `question` may
            # be just a short crop name. Combine with original_question so we
            # don't lose disease/treatment context.
            original_question = (state.get("original_question") or "").strip()
            if original_question and original_question.lower() != question.lower():
                rewrite_input = f"{original_question} {question}"
                logger.info("[WEB SEARCH] Clarification turn — enriching rewrite input with original_question")
            else:
                rewrite_input = question
            search_query = _rewrite_query_for_search(rewrite_input)
            # Hard cap — Tavily rejects queries longer than 400 chars
            if len(search_query) > 390:
                search_query = search_query[:390]
                logger.warning("[WEB SEARCH] Rewritten query truncated to 390 chars to respect Tavily limit")


        # ── Coffee-domain geographic enrichment ──────────────────────────────
        # Ensure results are scoped to Vietnam / Central Highlands context.
        vn_keywords = ["vietnam", "việt nam", "vn", "tây nguyên", "đắk lắk", "lâm đồng",
                       "robusta", "arabica", "cà phê"]
        if not any(k in search_query.lower() for k in vn_keywords):
            search_query = f"{search_query} cà phê Việt Nam Tây Nguyên"

        # Boost precision when the rewritten query is clearly about coffee
        # diseases, pests, or chemicals but lacks the word "coffee"/"cà phê".
        coffee_disease_kw = [
            "leaf rust", "gỉ sắt", "hemileia", "brown eye spot", "cercospora",
            "root rot", "phytophthora", "berry borer", "cbb", "hypothenemus",
            "white stem borer", "xylotrechus", "mealybug", "planococcus",
            "fertilizer", "phân bón", "npk", "potassium", "kali", "nitrogen",
            "pesticide", "fungicide", "thuốc", "chlorpyrifos", "mancozeb",
        ]
        is_coffee_specific = any(kw in search_query.lower() for kw in coffee_disease_kw)
        if is_coffee_specific and "coffee" not in search_query.lower() and "cà phê" not in search_query.lower():
            search_query = f"cà phê {search_query}"

    logger.debug("[WEB SEARCH] Final query: %s", search_query)

    # Perform search
    try:
        search_results = client.search(
            query=search_query,
            search_depth="advanced",
            max_results=7 if refinement_count > 0 else 5,
            include_raw_content=True,
            # Block e-commerce and social media spam:
            exclude_domains=["shopee.vn", "lazada.vn", "tiki.vn", "sendo.vn", "facebook.com", "youtube.com"] 
        )
        
        # Extract relevant information
        # raw_content is available because include_raw_content=True was requested.
        # We truncate it to keep the context window manageable for downstream LLMs.
        _RAW_CONTENT_MAX_CHARS = 3000
        results = []
        for result in search_results.get("results", []):
            raw = (result.get("raw_content") or "").strip()
            content_snippet = result.get("content", "")
            
            raw_extracted = ""
            if raw:
                # Attempt to find the relevant content snippet in the raw text
                # to avoid just grabbing the navigation/header boilerplate at the top
                idx = -1
                if content_snippet:
                    search_chunk = content_snippet[:40]
                    idx = raw.find(search_chunk)
                    if idx == -1 and len(content_snippet) > 20:
                        idx = raw.find(content_snippet[:20])
                        
                if idx != -1:
                    # Start a bit before the snippet to capture context
                    start_idx = max(0, idx - 500)
                    raw_extracted = raw[start_idx:start_idx + _RAW_CONTENT_MAX_CHARS]
                else:
                    # Fallback: skip the first 1000 chars (likely menus/headers) if document is long
                    if len(raw) > 2500:
                        start_idx = 1000
                        raw_extracted = raw[start_idx:start_idx + _RAW_CONTENT_MAX_CHARS]
                    else:
                        raw_extracted = raw[:_RAW_CONTENT_MAX_CHARS]

            results.append({
                "title": result.get("title", ""),
                "url": result.get("url", ""),
                "content": content_snippet,
                "raw_content": raw_extracted,
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