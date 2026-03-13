"""
Cross-Encoder Reranker Node

Uses a cross-encoder model to rerank retrieved documents for improved precision.
Cross-encoders score each (query, document) pair directly, providing more
accurate relevance scores than simple vector similarity.
"""

import logging
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
        state: Current graph state with candidate_docs from hybrid search
        
    Returns:
        Updated state with reranked_docs (top-k most relevant)
    """
    logger.info("[RERANKER] Reranking %d candidate documents", len(state.get('candidate_docs', [])))
    
    question = state["question"]
    candidate_docs = state.get("candidate_docs", [])
    
    if not candidate_docs:
        logger.info("[RERANKER] No candidate documents to rerank — passing empty list")
        return {
            "question": question,
            "reranked_docs": [],
            "documents": [],  # Update main documents field
        }
    
    # Load reranker model
    reranker = get_reranker_model()
    
    # Prepare (query, doc) pairs for scoring
    pairs = [(question, doc.page_content) for doc in candidate_docs]
    
    # Get relevance scores
    scores = reranker.predict(pairs)
    
    # Combine docs with scores and sort by relevance
    doc_score_pairs = list(zip(candidate_docs, scores))
    doc_score_pairs.sort(key=lambda x: x[1], reverse=True)
    
    # Get top-k documents (configurable, default to top 5)
    top_k = 5
    reranked_docs = [doc for doc, score in doc_score_pairs[:top_k]]
    
    logger.info("[RERANKER] %d candidates → top %d after reranking", len(candidate_docs), len(reranked_docs))
    for i, (doc, score) in enumerate(doc_score_pairs[:top_k]):
        logger.debug("[RERANKER] Rank %d | score=%.4f | %.80s", i + 1, score, doc.page_content)
    
    return {
        "question": question,
        "reranked_docs": reranked_docs,
        "documents": reranked_docs,  # Set as main documents for downstream nodes
    }

