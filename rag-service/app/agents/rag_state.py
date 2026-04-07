from typing import Annotated, Any, List, TypedDict, Dict, Optional
from langchain_core.documents import Document
from langchain_core.messages import BaseMessage
from langgraph.graph.message import add_messages

class GraphState(TypedDict, total=False):
    """
    Represents the state of our graph.

    Attributes:
        # Conversation history (multi-turn memory)
        messages: Full conversation history — LangGraph appends to this list via the
                  add_messages reducer instead of overwriting it. Each invocation
                  should push a HumanMessage in; generation nodes push back an AIMessage.
        summary: Condensed summary of older conversation turns.  Written by the
                 summarization node when the history exceeds the truncation threshold.

        # Original fields
        question: question
        generation: LLM generation
        documents: list of documents
        retry_count: number of retries for retrieval/generation
        language: desired output language
        user_id: authenticated user id — used for per-user vector scoping

        # Phase 2: Advanced Retrieval
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
        refinement_guidance: Actionable correction instructions for retry
        regulatory_flags: Compliance violation flags
        
        # Phase 5: Treatment Planning
        generated_plan: Serialized TreatmentPlan dict (with calculated dates)
        plant_id: Plant ID extracted by the planner from the user query
    """
    # Conversation history — add_messages reducer APPENDS rather than overwrites
    messages: Annotated[List[BaseMessage], add_messages]
    # Condensed summary of turns that were trimmed from messages
    summary: Optional[str]

    # Original fields (required)
    question: str
    generation: str
    documents: List[Document]
    retry_count: int
    language: Optional[str]
    # Authenticated user id for per-user vector search scoping
    user_id: Optional[str]
    
    # Phase 2: Advanced Retrieval
    candidate_docs: Optional[List[Document]]
    reranked_docs: Optional[List[Document]]
    
    # Phase 1.5: Intent Classification (pre-retrieval)
    # "chit_chat" → short-circuits to chit_chat node → END
    # "agricultural_query" → full RAG pipeline
    intent: Optional[str]

    # Phase 3: Routing
    path_type: Optional[str]
    confidence_score: Optional[float]
    completeness_score: Optional[float]
    web_search_results: Optional[List[Dict]]
    
    # Phase 4: Safety
    safety_passed: Optional[bool]
    safety_issues: Optional[List[str]]
    refinement_count: Optional[int]
    refinement_guidance: Optional[str]
    regulatory_flags: Optional[List[str]]

    # Phase 5: Treatment Planning
    generated_plan: Optional[Dict[str, Any]]
    plant_id: Optional[str]

    # Phase 0: Environment State (IoT sensors)
    env_state: Optional[Dict[str, Any]]  # Soil, GPS, weather readings
