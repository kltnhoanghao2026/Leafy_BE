"""
HyDE (Hypothetical Document Embeddings) Node

Generates a hypothetical ideal answer to improve retrieval quality.
The synthetic document is used to expand the query semantically.
"""

import logging
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import StrOutputParser

from app.agents.rag_state import GraphState
from app.core.ai_providers import get_chat_model

logger = logging.getLogger(__name__)


def hyde_expansion(state: GraphState) -> dict:
    """
    Generate a hypothetical document to improve retrieval.
    
    HyDE creates a synthetic "ideal answer" that captures the semantic
    intent of the query better than the raw question alone.
    
    Args:
        state: Current graph state containing the question and env_state
        
    Returns:
        Updated state with expanded_query and hyde_document
    """
    question = state["question"]
    env_state = state.get("env_state", {})
    logger.info("[HYDE] Generating hypothetical document for query: %.80s", question)
    
    # Format environmental context if available
    env_context = ""
    if env_state:
        soil = env_state.get("soil", {})
        weather = env_state.get("weather", {})
        env_context = f"""
Current environmental conditions:
- Soil: pH {soil.get('ph', 'N/A')}, moisture {soil.get('moisture_pct', 'N/A')}%, temp {soil.get('temperature_c', 'N/A')}°C
- Weather: {weather.get('air_temp_c', 'N/A')}°C, humidity {weather.get('humidity_pct', 'N/A')}%, rain forecast: {weather.get('forecast_rain_24h', 'N/A')}
"""
    
    # Create HyDE prompt with environmental awareness
    hyde_prompt = ChatPromptTemplate.from_template(
        """You are an expert agricultural assistant. Write a detailed, informative passage that would 
        perfectly answer the following question. The passage should be factual, comprehensive, 
        and contain relevant technical details. Consider the current environmental conditions when applicable.
        {env_context}
        Question: {question}
        
        Hypothetical ideal passage:"""
    )
    
    # Generate hypothetical document
    llm = get_chat_model(temperature=0.3)
    hyde_chain = hyde_prompt | llm | StrOutputParser()
    
    hyde_doc = hyde_chain.invoke({
        "question": question,
        "env_context": env_context
    })
    
    # Combine original question with hypothetical document for expanded query
    expanded_query = f"{question}\n\n{hyde_doc}"
    
    logger.info("[HYDE] Hypothetical document generated (%d chars)", len(hyde_doc))
    logger.debug("[HYDE] Preview: %.120s", hyde_doc)
    
    return {
        "question": question,
        "expanded_query": expanded_query,
        "hyde_document": hyde_doc,
        "retry_count": state.get("retry_count", 0)
    }

