"""
Hybrid Search Node

Combines dense vector search with sparse BM25 keyword search using
Reciprocal Rank Fusion (RRF) for improved retrieval quality.
"""

import logging
from app.agents.rag_state import GraphState
from app.services.vector_db import get_vector_service

logger = logging.getLogger(__name__)


def hybrid_search(state: GraphState) -> dict:
    """
    Perform hybrid search combining dense and sparse retrieval.
    
    Uses the incoming user question to perform both:
    1. Dense vector search (semantic similarity)
    2. Sparse BM25 search (keyword matching)
    
    Results are combined using Reciprocal Rank Fusion (RRF).
    
    Args:
        state: Current graph state
        
    Returns:
        Updated state with documents for reranking
    """
    logger.info("[HYBRID SEARCH] Running hybrid search")

    question = state["question"]
    user_id = state.get("user_id")

    # ── Retrieval query resolution ──────────────────────────────────────────
    # `search_query` is an optional decoupled retrieval query (e.g. Vietnamese
    # disease keywords supplied by the plan controller) that gives Qdrant a better
    # signal than the English generation prompt stored in `question`.
    explicit_search_query = (state.get("search_query") or "").strip()

    # ── Query enrichment for clarification turns ────────────────────────────
    # When the user first asked something like "kế hoạch trị bệnh rỉ sét" and
    # then replied "cây cà phê" as a clarification, the current `question` is just
    # the short reply.  Combine it with original_question so the vector search
    # embeds the full intent, not just the crop name.
    original_question = (state.get("original_question") or "").strip()
    if explicit_search_query:
        # Dedicated retrieval query takes highest priority
        search_query = explicit_search_query
        logger.info(
            "[HYBRID SEARCH] Using dedicated search_query for retrieval: '%.120s'",
            search_query,
        )
    elif original_question and original_question.lower() != question.lower():
        search_query = f"{original_question} {question}"
        logger.info(
            "[HYBRID SEARCH] Clarification turn detected — enriched query: '%.120s'",
            search_query,
        )
    else:
        search_query = question

    # Get vector service
    vector_service = get_vector_service()

    # ── Dense vector search with metadata-enriched re-scoring ─────────────────
    # Each candidate chunk is re-scored using its own metadata text (section
    # title, category, variety, source filename, etc.) prepended to the query.
    # This surfaces semantically thin chunks whose surrounding context is a
    # strong match for the search intent.
    enriched_results = vector_service.search_with_metadata_enrichment(
        query=search_query,
        k=20,
        user_id=user_id,
    )
    # enriched_results: list of (point_id, Document) — point IDs in metadata["point_id"]
    for point_id, doc in enriched_results:
        doc.metadata["point_id"] = point_id

    # ── Sparse BM25 search (text + metadata keyword scoring) ─────────────────
    # _bm25_search now combines 70 % text BM25 with 30 % metadata-field BM25,
    # so section titles, filenames, and category keywords influence ranking.
    sparse_results = vector_service._bm25_search(
        query=search_query,
        k=20,
        user_id=user_id,
    )

    # ── Combine with RRF ─────────────────────────────────────────────────────
    dense_docs_only = [doc for _, doc in enriched_results]
    documents = vector_service._reciprocal_rank_fusion(
        dense_docs_only,
        sparse_results,
        k=10,
    )

    # Restore point IDs on fused docs from the enriched dense results
    if enriched_results:
        doc_content_map = {doc.page_content: pid for pid, doc in enriched_results}
        for doc in documents:
            if doc.page_content in doc_content_map:
                doc.metadata["point_id"] = doc_content_map[doc.page_content]

    logger.info("[HYBRID SEARCH] Retrieved %d candidate documents with point IDs", len(documents))

    return {
        "question": question,
        "documents": documents,
        "retrieved_point_ids": [pid for pid, _ in enriched_results],
    }



