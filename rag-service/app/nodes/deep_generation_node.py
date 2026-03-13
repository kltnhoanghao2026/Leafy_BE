"""
Deep Generation Node

Uses Gemini 1.5 Pro for complex reasoning and research when
internal documents are insufficient. Combines internal docs with
web search results for comprehensive answers.
"""

import logging
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import StrOutputParser

from app.agents.rag_state import GraphState
from app.core.ai_providers import get_gemini_pro

logger = logging.getLogger(__name__)


def deep_generation(state: GraphState) -> dict:
    """
    Generate comprehensive answer using Gemini Pro (deep path).
    
    Used when internal documents have low confidence/completeness.
    Combines internal knowledge with web search results and environmental
    data for thorough, research-backed responses.
    
    Args:
        state: Current graph state with documents, web_search_results, and env_state
        
    Returns:
        Updated state with generation
    """
    logger.info("[DEEP GEN] Generating answer with Gemini Pro (%d internal docs, %d web results)",
                len(state.get('documents', [])), len(state.get('web_search_results', [])))
    
    question = state["question"]
    documents = state.get("documents", [])
    web_results = state.get("web_search_results", [])
    safety_issues = state.get("safety_issues", [])
    refinement_count = state.get("refinement_count", 0)
    env_state = state.get("env_state", {})
    language = state.get("language", "English")
    
    # Build comprehensive context
    context_parts = []
    
    # Add environmental state first (from IoT sensors)
    if env_state:
        soil = env_state.get("soil", {})
        weather = env_state.get("weather", {})
        gps = env_state.get("gps", {})
        env_context = f"""=== Current Environmental State (IoT Sensors - {env_state.get('reading_timestamp', 'N/A')}) ===
            Location: lat={gps.get('latitude')}, lon={gps.get('longitude')}, altitude={gps.get('altitude_m')}m, farm plot: {gps.get('farm_plot_id')}
            Soil: pH {soil.get('ph')}, moisture {soil.get('moisture_pct')}%, temp {soil.get('temperature_c')}°C
                N={soil.get('nitrogen_ppm')}ppm, P={soil.get('phosphorus_ppm')}ppm, K={soil.get('potassium_ppm')}ppm
                Organic matter {soil.get('organic_matter_pct')}%
            Weather: Air temp {weather.get('air_temp_c')}°C, humidity {weather.get('humidity_pct')}%, wind {weather.get('wind_speed_kmh')}km/h
                    Rainfall last 7d: {weather.get('rainfall_mm_last_7d')}mm, UV index: {weather.get('uv_index')}
                    Rain forecast 24h: {weather.get('forecast_rain_24h')}
            Note: Consider these conditions when making recommendations. High humidity increases disease risk. Rain forecast affects spray scheduling."""
        context_parts.append(env_context)
    
    # Add internal documents
    if documents:
        internal_context = "\n\n".join([
            f"Internal Source {i+1}:\n{doc.page_content}"
            for i, doc in enumerate(documents[:3])
        ])
        context_parts.append(f"=== Internal Knowledge ===\n{internal_context}")
    
    # Add web search results
    if web_results:
        web_context = "\n\n".join([
            f"Web Source {i+1} ({result['url']}):\nTitle: {result['title']}\n{result['content']}"
            for i, result in enumerate(web_results[:3])
        ])
        context_parts.append(f"\n=== External Sources (2026) ===\n{web_context}")
    
    full_context = "\n\n".join(context_parts)
    
    # Deep research prompt
    base_prompt = """You are an expert agricultural advisor with access to both internal knowledge and recent external sources. Provide a comprehensive, well-researched answer.

                    {context}

                    Question: {question}

                    Instructions:
                    - Synthesize information from all available sources
                    - Cite your sources when making specific claims
                    - Prioritize recent (2026) guidelines when applicable
                    - Be thorough but clear
                    - Provide your answer entirely in the following language: {language}
                    {safety_constraints}

                    Comprehensive Answer:"""
    
    # Add safety constraints if refining
    safety_constraints = ""
    if refinement_count > 0 and safety_issues:
        issues_text = "\n".join([f"- {issue}" for issue in safety_issues])
        safety_constraints = f"\n\nIMPORTANT SAFETY REQUIREMENTS:\nThe previous response had these issues:\n{issues_text}\n\nYou MUST address these issues in your response. Be conservative with dosages and avoid banned substances."
    
    prompt = ChatPromptTemplate.from_template(base_prompt)
    
    # Use Gemini Pro for deep reasoning
    llm = get_gemini_pro(temperature=0.3)
    chain = prompt | llm | StrOutputParser()
    
    generation = chain.invoke({
        "context": full_context,
        "question": question,
        "safety_constraints": safety_constraints,
        "language": language
    })
    
    logger.info("[DEEP GEN] Answer generated (%d chars)", len(generation))
    logger.debug("[DEEP GEN] Preview: %.150s", generation)
    
    return {
        "question": question,
        "documents": documents,
        "generation": generation,
    }

