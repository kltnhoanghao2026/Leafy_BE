"""
Summarization Node

Fires before every pipeline run. When the conversation history exceeds
_SUMMARIZE_THRESHOLD messages, it condenses older turns into a single
summary string (stored as `state["summary"]`) and removes those messages
from the `messages` list so the LLM context window doesn't grow unbounded.

LangGraph pattern used:
  - Return RemoveMessage(id=...) objects to delete old messages via add_messages reducer.
  - Store the condensed text in the separate `summary` field (not injected into
    the messages list — generation nodes prepend it as a SystemMessage in-prompt).

Design notes:
  - The LAST 2 messages (current Human turn + most recent AI answer) are NEVER removed
    so the model always has the immediate context.
  - If a previous summary exists it is incorporated into the new one (rolling summary).
"""

import logging
from langchain_core.messages import RemoveMessage, SystemMessage
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder

from app.agents.rag_state import GraphState
from app.core.ai_providers import get_gemini_flash

logger = logging.getLogger(__name__)

# Summarize once the message list grows beyond this many turns.
_SUMMARIZE_THRESHOLD = 10


def maybe_summarize(state: GraphState) -> dict:
    """
    Conditionally summarize old conversation turns.

    If `len(messages) > _SUMMARIZE_THRESHOLD`:
      1. Summarise all messages except the two most recent.
      2. Delete those old messages from the history via RemoveMessage.
      3. Store the condensed text in `state["summary"]`.
    Otherwise this node is a no-op (returns empty dict).
    """
    messages = state.get("messages", [])

    if len(messages) <= _SUMMARIZE_THRESHOLD:
        return {}  # Not enough history — skip

    messages_to_drop = messages[:-2]   # All but the most recent pair
    recent_messages = messages[-2:]     # Keep these intact

    logger.info(
        "[SUMMARIZE] History has %d messages — summarising %d old turns",
        len(messages),
        len(messages_to_drop),
    )

    # Build a rolling summary prompt
    existing_summary = state.get("summary", "")
    if existing_summary:
        system_text = (
            "You are summarising an ongoing **Vietnamese coffee farming advisory** conversation. "
            "An earlier summary already exists — integrate it with the new messages below "
            "to produce a single, concise updated summary.\n\n"
            "Preserve all of the following if present:\n"
            "  • Coffee variety mentioned (Robusta, Arabica, TR4, TN1, Catimor, etc.)\n"
            "  • Disease or pest identified (Leaf Rust, CBB, Brown Eye Spot, etc.)\n"
            "  • Specific chemicals and dosages recommended (product name, active ingredient, rate)\n"
            "  • PHI (pre-harvest interval) or spray schedule agreed\n"
            "  • Any action items the user committed to or asked about\n"
            "  • Farm location or plot context mentioned\n"
            f"\n\nEarlier summary:\n{existing_summary}"
        )
    else:
        system_text = (
            "You are summarising a **Vietnamese coffee farming advisory** conversation. "
            "Produce a concise summary that preserves:\n"
            "  • Coffee variety mentioned (Robusta, Arabica, TR4, TN1, Catimor, etc.)\n"
            "  • Disease or pest identified (Leaf Rust, CBB, Brown Eye Spot, etc.)\n"
            "  • Specific chemicals and dosages mentioned (product name, active ingredient, rate)\n"
            "  • PHI (pre-harvest interval) or spray schedule agreed\n"
            "  • Any open action items the user is tracking\n"
            "  • Farm location or plot context mentioned"
        )

    prompt = ChatPromptTemplate.from_messages([
        ("system", system_text),
        MessagesPlaceholder(variable_name="history"),
        ("human", "Provide a concise summary of the conversation above."),
    ])

    llm = get_gemini_flash(temperature=0)
    chain = prompt | llm | StrOutputParser()

    try:
        new_summary = chain.invoke({"history": messages_to_drop})
    except Exception as exc:
        # Summarisation failure is non-fatal — skip and keep all messages
        logger.warning("[SUMMARIZE] Summarisation failed: %s — keeping full history", exc)
        return {}

    logger.info("[SUMMARIZE] Summary updated (%d chars)", len(new_summary))

    # Remove old messages by ID (add_messages reducer handles RemoveMessage)
    removes = [RemoveMessage(id=m.id) for m in messages_to_drop]

    return {
        "summary": new_summary,
        "messages": removes,   # add_messages reducer: removes messages by ID, appends nothing new
    }
