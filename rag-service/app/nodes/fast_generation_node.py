"""
Fast Generation Node

Uses Gemini 1.5 Flash for quick, cost-effective generation when
retrieved documents have high confidence and completeness.
"""

import logging
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import StrOutputParser

from app.agents.rag_state import GraphState
from app.core.ai_providers import get_gemini_flash

logger = logging.getLogger(__name__)


def fast_generation(state: GraphState) -> dict:
    """
    Generate answer using Gemini Flash (fast path).
    
    Used when internal documents are sufficient to answer the question
    with high confidence. Optimized for speed and cost-efficiency.
    
    Args:
        state: Current graph state with documents and env_state
        
    Returns:
        Updated state with generation
    """
    logger.info("[FAST GEN] Generating answer with Gemini Flash (%d source docs)", len(state.get('documents', [])))
    
    question = state["question"]
    documents = state.get("documents", [])
    env_state = state.get("env_state", {})
    language = state.get("language", "English")
    
    # Format environmental context if available
    env_context = ""
    if env_state:
        soil = env_state.get("soil", {})
        weather = env_state.get("weather", {})
        gps = env_state.get("gps", {})
        env_context = f"""
            Environmental Context (IoT sensors):
            Location: lat={gps.get('latitude')}, lon={gps.get('longitude')}, altitude={gps.get('altitude_m')}m
            Soil: pH {soil.get('ph')}, moisture {soil.get('moisture_pct')}%, temp {soil.get('temperature_c')}°C, N={soil.get('nitrogen_ppm')}ppm, P={soil.get('phosphorus_ppm')}ppm, K={soil.get('potassium_ppm')}ppm
            Weather: {weather.get('air_temp_c')}°C, humidity {weather.get('humidity_pct')}%, wind {weather.get('wind_speed_kmh')}km/h, rain last 7d={weather.get('rainfall_mm_last_7d')}mm, rain forecast 24h={weather.get('forecast_rain_24h')}
            """
    
    # Fast RAG prompt with environmental awareness
    prompt = ChatPromptTemplate.from_template(
        """You are a helpful assistant for agricultural questions. Use the provided context and current environmental conditions to answer the question accurately and concisely.
            Provide your answer entirely in the following language: {language}.
            
            {env_context}
            Context:
            {context}

            Question: {question}

            Answer:"""
    )
    
    # Use Gemini Flash for speed
    llm = get_gemini_flash(temperature=0)
    chain = prompt | llm | StrOutputParser()
    
    # Format documents
    context = "\n\n".join([doc.page_content for doc in documents])
    
    generation = chain.invoke({
        "context": context,
        "question": question,
        "env_context": env_context,
        "language": language
    })
    
    logger.info("[FAST GEN] Answer generated (%d chars)", len(generation))
    logger.debug("[FAST GEN] Preview: %.150s", generation)
    
    return {
        "question": question,
        "documents": documents,
        "generation": generation,
    }

