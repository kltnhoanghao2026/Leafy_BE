"""
Direct Node

Handles non-agricultural conversational messages (greetings, small talk, identity
questions) with a brief, friendly Gemini Flash response.

Bypasses the full RAG pipeline — no retrieval, no router, no safety audit.
"""

import logging

from langchain_core.messages import AIMessage
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder

from app.agents.rag_state import GraphState
from app.core.ai_providers import get_gemini_flash

logger = logging.getLogger(__name__)

# Keep the same history window as the fast-generation node.
_MAX_HISTORY_PAIRS = 5


def direct_response(state: GraphState) -> dict:
    """
    Generate a brief, warm conversational reply for direct messages.

    Uses recent conversation history for pronoun / context resolution but does
    NOT perform retrieval or safety auditing.

    Args:
        state: Current graph state with 'question', 'messages', and 'language'

    Returns:
        Dict with 'generation', appended 'messages' AIMessage, and 'safety_passed' = True
        (so the graph can route cleanly to END without an extra audit step).
    """
    question = state.get("question", "")
    language = state.get("language", "English")
    logger.info("[DIRECT] Generating conversational response")

    # Inject recent history for follow-up awareness (exclude current HumanMessage)
    all_messages = state.get("messages", [])
    history = all_messages[:-1] if len(all_messages) > 1 else []
    history = history[-(_MAX_HISTORY_PAIRS * 2):]

    prompt = ChatPromptTemplate.from_messages([
        (
            "system",
            (
                "You are Leafy, a friendly AI assistant specialised in coffee farming and "
                "sustainable agriculture for Vietnamese coffee growers.\n"
                "When users greet you or engage in small talk, respond briefly and warmly "
                "(1–3 sentences), then gently invite them to ask about their coffee plants, "
                "farm, or crops.\n"
                "Never mention that you are an AI language model such as Gemini or GPT.\n"
                "Always respond in {language}."
            ),
        ),
        MessagesPlaceholder("history"),
        ("human", "{question}"),
    ])

    llm = get_gemini_flash(temperature=0.7)
    chain = prompt | llm | StrOutputParser()

    response = chain.invoke({
        "question": question,
        "language": language,
        "history": history,
    })

    logger.info("[DIRECT] Response generated (len=%d)", len(response))
    return {
        "generation": response,
        "messages": [AIMessage(content=response)],
        # Pre-mark as safe — direct responses never contain pesticide dosages.
        "safety_passed": True,
        "safety_issues": [],
    }


# Backward-compatible alias during rename rollout.
chit_chat_response = direct_response
