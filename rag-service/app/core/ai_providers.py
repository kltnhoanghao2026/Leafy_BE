import logging
import os

from langchain_community.embeddings.fastembed import FastEmbedEmbeddings
from langchain_openai import ChatOpenAI
from sentence_transformers import CrossEncoder
from dotenv import load_dotenv

load_dotenv()

logger = logging.getLogger(__name__)


def get_embeddings_model():
    """Returns FastEmbed embeddings (BAAI/bge-small-en-v1.5)."""
    return FastEmbedEmbeddings(model_name="BAAI/bge-small-en-v1.5")


def get_chat_model(temperature: float = 0) -> ChatOpenAI:
    """Returns the OpenAI chat model used for structured-output nodes (router, auditor)."""
    model_name = os.getenv("CHAT_MODEL", "gpt-4o-mini")
    return ChatOpenAI(model=model_name, temperature=temperature)


def get_gemini_flash(temperature: float = 0) -> ChatOpenAI:
    """
    Returns an OpenAI model for the fast generation path.
    Configured via FAST_MODEL env var (default gpt-4o-mini).
    """
    model_name = os.getenv("FAST_MODEL", "gpt-4o-mini")
    logger.debug("[AI_PROVIDERS] Fast model: '%s'", model_name)
    return ChatOpenAI(model=model_name, temperature=temperature)


def get_gemini_pro(temperature: float = 0.3) -> ChatOpenAI:
    """
    Returns an OpenAI model for the deep generation path.
    Configured via DEEP_MODEL env var (default gpt-4o).
    """
    model_name = os.getenv("DEEP_MODEL", "gpt-4o")
    logger.debug("[AI_PROVIDERS] Deep model: '%s'", model_name)
    return ChatOpenAI(model=model_name, temperature=temperature)


def get_reranker_model() -> CrossEncoder:
    """Returns the cross-encoder reranker model."""
    model_name = os.getenv("RERANKER_MODEL", "cross-encoder/ms-marco-MiniLM-L-6-v2")
    return CrossEncoder(model_name)

