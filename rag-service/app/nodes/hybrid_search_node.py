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
    
    Uses the expanded query from HyDE to perform both:
    1. Dense vector search (semantic similarity)
    2. Sparse BM25 search (keyword matching)
    
    Results are combined using Reciprocal Rank Fusion (RRF).
    
    Args:
        state: Current graph state with expanded_query from HyDE
        
    Returns:
        Updated state with candidate_docs for reranking
    """
    logger.info("[HYBRID SEARCH] Running hybrid search")
    
    question = state["question"]
    expanded_query = state.get("expanded_query", question)
    
    # Get vector service
    vector_service = get_vector_service()
    
    # Perform hybrid search
    candidate_docs = vector_service.hybrid_search(
        query=expanded_query,
        dense_k=20,  # Retrieve top-20 from vector search
        sparse_k=20,  # Retrieve top-20 from BM25
        final_k=10   # Combine to top-10 candidates
    )
    
    logger.info("[HYBRID SEARCH] Retrieved %d candidate documents", len(candidate_docs))
    
    return {
        "question": question,
        "candidate_docs": candidate_docs,
    }

