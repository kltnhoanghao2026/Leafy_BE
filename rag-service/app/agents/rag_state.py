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
        generated_plan: Serialized Plan dict (with calculated dates)
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
    # Incoming bearer token propagated from request for internal gateway lookups
    authorization: Optional[str]
    
    # ── Phase 1.5: Intent Classification ──
    # "direct" → short-circuits to direct node → END
    # "agriculture_query" → full RAG pipeline
    intent: Optional[str]

    # ── Phase 1.6: Clarification Check ──
    # True when the question is too vague to answer usefully.
    # The pipeline short-circuits to clarification_response → END.
    needs_clarification: Optional[bool]
    
    # The follow-up question sent back to the user (empty string when not needed).
    clarification_question: Optional[str]
    
    # The original intent-bearing question saved when clarification is triggered.
    # E.g. "Tạo kế hoạch trị bệnh rỉ sét cho tôi" is stored here so the planner
    # can reconstruct the true planning objective even when the current `question`
    # is just a clarification reply like "cây cà phê".
    original_question: Optional[str]

    # ── Phase 3: Routing ──
    path_type: Optional[str]
    confidence_score: Optional[float]
    completeness_score: Optional[float]
    web_search_results: Optional[List[Dict]]
    
    # ── Phase 4: Safety ──
    safety_passed: Optional[bool]
    safety_issues: Optional[List[str]]
    refinement_count: Optional[int]
    refinement_guidance: Optional[str]
    regulatory_flags: Optional[List[str]]

    # ── Phase 5: Treatment Planning ──
    generated_plan: Optional[Dict[str, Any]]
    plant_id: Optional[str]

    # Phase 0: Environment State (IoT sensors)
    env_state: Optional[Dict[str, Any]]  # Soil, GPS, weather readings
    # Caller-supplied context to seed env resolution without requiring plant_id in question
    farm_plot_id: Optional[str]
    farm_zone_id: Optional[str]

    # Client-supplied route override — set from ChatRequest.route by ChatService.
    # Values: "fast" | "deep" | "planning" | None (None means auto-route).
    # When set, router_node short-circuits ALL automatic routing logic (Priority 0).
    forced_route: Optional[str]

    # Optional dedicated Qdrant retrieval query — decoupled from `question`.
    # When set (e.g. by the plan controller using Vietnamese disease keywords),
    # hybrid_search_node uses this for vector/BM25 search instead of `question`.
    # Falls back to `question` if not provided.
    search_query: Optional[str]

    # Qdrant point IDs for all dense search results, in order.
    # Set by hybrid_search_node so downstream nodes (reranker, planner, serialiser)
    # can include raw point IDs in the saved sourceDocuments for the chunk viewer.
    retrieved_point_ids: Optional[List[str]]
