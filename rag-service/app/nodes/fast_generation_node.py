"""
Fast Generation Node

Uses Gemini 1.5 Flash for quick, cost-effective generation when
retrieved documents have high confidence and completeness.
"""

import logging
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_core.output_parsers import StrOutputParser
from langchain_core.messages import AIMessage, SystemMessage

from app.agents.rag_state import GraphState
from app.core.ai_providers import get_gemini_flash

logger = logging.getLogger(__name__)

# Maximum number of previous message pairs (human + AI) to keep in history.
# Prevents token bloat for long conversations.
_MAX_HISTORY_PAIRS = 5


def fast_generation(state: GraphState) -> dict:
    """
    Generate answer using Gemini Flash (fast path).
    
    Used when internal documents are sufficient to answer the question
    with high confidence. Optimized for speed and cost-efficiency.
    Injects the last _MAX_HISTORY_PAIRS turns of conversation history
    so the model can resolve pronouns and follow-up questions.
    
    Args:
        state: Current graph state with documents, env_state, and messages
        
    Returns:
        Updated state with generation and appended AIMessage
    """
    logger.info("[FAST GEN] Generating answer with Gemini Flash (%d source docs)", len(state.get('documents', [])))
    
    question = state["question"]
    documents = state.get("documents", [])
    env_state = state.get("env_state", {})
    language = state.get("language", "English")

    # Build history: all messages except the last one (the current HumanMessage).
    # Truncate to the last _MAX_HISTORY_PAIRS * 2 messages to control token budget.
    all_messages = state.get("messages", [])
    history = all_messages[:-1] if len(all_messages) > 1 else []
    history = history[-(_MAX_HISTORY_PAIRS * 2):]

    # Prepend a rolling summary as a SystemMessage when the history was trimmed
    summary = state.get("summary")
    if summary:
        history = [SystemMessage(content=f"[Earlier conversation summary]: {summary}")] + history
    
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
    
    # Fast RAG prompt with conversation history and environmental awareness
    prompt = ChatPromptTemplate.from_messages([
        ("system",
         "You are **Leafy**, an expert agronomist specialising in Vietnamese coffee cultivation "
         "(Robusta and Arabica) in the Central Highlands (Tây Nguyên). "
         "Your knowledge covers:\n"
         "  • Diseases: Coffee Leaf Rust (Hemileia vastatrix), Brown Eye Spot "
         "(Cercospora coffeicola), Phytophthora Root Rot, Wilt\n"
         "  • Pests: Coffee Berry Borer (Hypothenemus hampei), White Stem Borer "
         "(Xylotrechus quadripes), Mealybugs, Nematodes\n"
         "  • Nutrition: NPK fertilisation, micro-nutrient deficiencies (Fe, Mn, Mg, Bo), "
         "soil pH optimisation (5.5–6.5 for coffee)\n"
         "  • Agronomy: shade management, irrigation scheduling, post-harvest processing, "
         "pruning (cưa đốn), Vietnamese GAP, VietGAP, and Rainforest Alliance compliance\n"
         "  • Regulations: PPD-registered pesticides, 4-Đúng principles, '4 Rights', "
         "pre-harvest intervals (PHI), EU / USDA MRL limits for exported coffee\n\n"
         "RULES:\n"
         "  1. Ground every answer in the provided context documents first.\n"
         "  2. If the context contains specific dosages, cultivar names, or local brand "
         "equivalents — use them precisely.\n"
         "  3. When environmental sensor data is available, explicitly tailor the advice "
         "(e.g. defer spray if rain forecast, increase irrigation if soil moisture < 50%).\n"
         "  4. Always include PHI and PPE reminders when you mention any pesticide or fungicide.\n"
         "  5. Reply entirely in: {language}.\n\n"
         "{env_context}"
         "Context documents:\n{context}"),
        MessagesPlaceholder(variable_name="history"),
        ("human", "{question}"),
    ])
    
    # Use Gemini Flash for speed
    llm = get_gemini_flash(temperature=0)
    chain = prompt | llm | StrOutputParser()
    
    # Format documents
    context = "\n\n".join([doc.page_content for doc in documents])
    
    generation = chain.invoke({
        "context": context,
        "question": question,
        "env_context": env_context,
        "language": language,
        "history": history,
    })
    
    logger.info("[FAST GEN] Answer generated (%d chars)", len(generation))
    logger.debug("[FAST GEN] Preview: %.150s", generation)
    
    return {
        "question": question,
        "documents": documents,
        "generation": generation,
        # Append the assistant reply so the next turn sees the full exchange
        "messages": [AIMessage(content=generation)],
    }

