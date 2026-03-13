from typing import Any, List, TypedDict, Dict, Optional
from langchain_core.documents import Document

class GraphState(TypedDict, total=False):
    """
    Represents the state of our graph.

    Attributes:
        # Original fields
        question: question
        generation: LLM generation
        documents: list of documents
        retry_count: number of retries for retrieval/generation
        
        # Phase 2: Advanced Retrieval
        expanded_query: HyDE expanded query
        hyde_document: Hypothetical document for improved retrieval
        candidate_docs: Pre-reranking candidates from hybrid search
        reranked_docs: Post-reranking top-k documents
        
        # Phase 3: Routing
        path_type: "fast" or "deep" path selection
        confidence_score: Router confidence (0-1)
        completeness_score: Content gap detection score (0-1)
        web_search_results: Tavily search results
        
        # Phase 4: Safety
        safety_passed: Whether safety checks passed
        safety_issues: List of detected safety issues
        refinement_count: Track refinement loop attempts
        regulatory_flags: Compliance violation flags
        
        # Phase 5: Treatment Planning
        generated_plan: Serialized TreatmentPlan dict (with calculated dates)
        plant_id: Plant ID extracted by the planner from the user query
    """
    # Original fields (required)
    question: str
    generation: str
    documents: List[Document]
    retry_count: int
    language: Optional[str]
    
    # Phase 2: Advanced Retrieval
    expanded_query: Optional[str]
    hyde_document: Optional[str]
    candidate_docs: Optional[List[Document]]
    reranked_docs: Optional[List[Document]]
    
    # Phase 3: Routing
    path_type: Optional[str]
    confidence_score: Optional[float]
    completeness_score: Optional[float]
    web_search_results: Optional[List[Dict]]
    
    # Phase 4: Safety
    safety_passed: Optional[bool]
    safety_issues: Optional[List[str]]
    refinement_count: Optional[int]
    regulatory_flags: Optional[List[str]]

    # Phase 5: Treatment Planning
    generated_plan: Optional[Dict[str, Any]]
    plant_id: Optional[str]

    # Phase 0: Environment State (IoT sensors)
    env_state: Optional[Dict[str, Any]]  # Soil, GPS, weather readings
