import os
from langchain_community.embeddings.fastembed import FastEmbedEmbeddings
from langchain_openai import ChatOpenAI, OpenAIEmbeddings
from langchain_google_genai import ChatGoogleGenerativeAI
from sentence_transformers import CrossEncoder
from dotenv import load_dotenv

load_dotenv()

def get_embeddings_model():
    """
    Returns the configured Embeddings model.
    Using FastEmbed (BAAI/bge-small-en-v1.5).
    """
    return FastEmbedEmbeddings(
        model_name="BAAI/bge-small-en-v1.5"
    )

def get_chat_model(temperature: float = 0) -> ChatOpenAI:
    """
    Returns the configured Chat LLM model.
    Defaults to gpt-3.5-turbo
    """
    return ChatOpenAI(
        model="gpt-3.5-turbo",
        temperature=temperature
    )

def get_gemini_flash(temperature: float = 0) -> ChatGoogleGenerativeAI:
    """
    Returns Gemini Flash model for fast path generation.
    Optimized for low latency and cost-effectiveness.
    """
    model_name = os.getenv("FAST_MODEL", "gemini-2.5-flash")
    return ChatGoogleGenerativeAI(
        model=model_name,
        temperature=temperature,
    )

def get_gemini_pro(temperature: float = 0.3) -> ChatGoogleGenerativeAI:
    """
    Returns Gemini Pro model for deep path generation.
    Optimized for complex reasoning and research tasks.
    """
    model_name = os.getenv("DEEP_MODEL", "gemini-2.5-flash")
    return ChatGoogleGenerativeAI(
        model=model_name,
        temperature=temperature,
    )

def get_reranker_model() -> CrossEncoder:
    """
    Returns Cross-Encoder model for reranking documents.
    Uses sentence-transformers cross-encoder architecture.
    """
    model_name = os.getenv("RERANKER_MODEL", "cross-encoder/ms-marco-MiniLM-L-6-v2")
    return CrossEncoder(model_name)
