"""
Cross-Encoder Reranker Node

Uses a cross-encoder model to rerank retrieved documents for improved precision.
Cross-encoders score each (query, document) pair directly, providing more
accurate relevance scores than simple vector similarity.
"""

import logging
import math
from typing import List
from langchain_core.documents import Document

from app.agents.rag_state import GraphState
from app.core.ai_providers import get_reranker_model

logger = logging.getLogger(__name__)


def rerank_documents(state: GraphState) -> dict:
    """
    Rerank retrieved documents using a cross-encoder model.
    
    Cross-encoders provide more accurate relevance scoring by directly
    encoding the (query, document) pair together, rather than computing
    similarity between independent embeddings.
    
    Args:
        state: Current graph state with documents from hybrid search
        
    Returns:
        Updated state with documents (top-k most relevant)
    """
    logger.info("[RERANKER] Reranking %d candidate documents", len(state.get('documents', [])))
    
    question = state["question"]
    documents = state.get("documents", [])
    
    if not documents:
        logger.info("[RERANKER] No candidate documents to rerank — passing empty list")
        return {
            "question": question,
            "documents": [],  # Update main documents field
        }
    
    # ── Retrieval query resolution (mirroring hybrid_search_node) ───────────
    explicit_search_query = (state.get("search_query") or "").strip()
    original_question = (state.get("original_question") or "").strip()
    
    if explicit_search_query:
        search_query = explicit_search_query
    elif original_question and original_question.lower() != question.lower():
        search_query = f"{original_question} {question}"
    else:
        search_query = question
    
    # Load reranker model
    reranker = get_reranker_model()
    
    # Prepare (query, doc) pairs for scoring using the semantic search query
    pairs = [(search_query, doc.page_content) for doc in documents]
    
    # Get relevance scores
    scores = reranker.predict(pairs)
    
    # Combine docs with scores and sort by relevance
    doc_score_pairs = list(zip(documents, scores))
    doc_score_pairs.sort(key=lambda x: x[1], reverse=True)
    
    # Get top-k documents (configurable, default to top 5)
    top_k = 5
    reranked_docs = []
    for doc, score in doc_score_pairs[:top_k]:
        # Cross-encoder outputs raw logits (range: -inf to +inf).
        # Apply sigmoid to convert to a 0-1 probability so the planner's
        # 0.7 confidence threshold is meaningful.
        normalized_score = 1.0 / (1.0 + math.exp(-float(score)))
        doc.metadata["rerank_score"] = normalized_score
        reranked_docs.append(doc)
    
    logger.info("[RERANKER] %d candidates → top %d after reranking", len(documents), len(reranked_docs))
    for i, (doc, score) in enumerate(doc_score_pairs[:top_k]):
        logger.debug("[RERANKER] Rank %d | score=%.4f | %.80s", i + 1, score, doc.page_content)
    
    return {
        "question": question,
        "documents": reranked_docs,
    }

