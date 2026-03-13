from langchain_core.prompts import ChatPromptTemplate
import logging
from pydantic import BaseModel, Field
from langchain_openai import ChatOpenAI
from langchain_core.output_parsers import StrOutputParser

from app.agents.rag_state import GraphState
from app.core.ai_providers import get_chat_model
from app.services.vector_db import get_vector_service

logger = logging.getLogger(__name__)

# --- Pydantic Models for Structured Output ---

class GradeDocuments(BaseModel):
    """Binary score for relevance check on retrieved documents."""
    binary_score: str = Field(description="Documents are relevant to the question, 'yes' or 'no'")

# --- Nodes ---

def retrieve(state: GraphState):
    """
    Retrieve documents

    Args:
        state (dict): The current graph state

    Returns:
        state (dict): New key added to state, documents, that contains retrieved documents
    """
    print("---RETRIEVE---")
    question = state["question"]
    retry_count = state.get("retry_count", 0)

    vector_service = get_vector_service()
    # Using the vector store attached to the service
    retriever = vector_service.vector_store.as_retriever()
    documents = retriever.invoke(question)
    
    return {"documents": documents, "retry_count": retry_count, "question": question}

def generate(state: GraphState):
    """
    Generate answer

    Args:
        state (dict): The current graph state

    Returns:
        state (dict): New key added to state, generation, that contains LLM generation
    """
    print("---GENERATE---")
    question = state["question"]
    documents = state["documents"]
    language = state.get("language", "English")
    
    # Simple RAG Prompt
    prompt = ChatPromptTemplate.from_template(
        """You are an assistant for question-answering tasks. Use the following pieces of retrieved context to answer the question. If you don't know the answer, just say that you don't know. Use three sentences maximum and keep the answer concise.
        Provide your answer entirely in the following language: {language}.
        
        Question: {question} 
        
        Context: {context} 
        
        Answer:"""
    )
    
    llm = get_chat_model(temperature=0)
    rag_chain = prompt | llm | StrOutputParser()
    
    generation = rag_chain.invoke({"context": documents, "question": question, "language": language})
    return {"documents": documents, "question": question, "generation": generation}

def grade_documents(state: GraphState):
    """
    Determines whether the retrieved documents are relevant to the question.

    Args:
        state (dict): The current graph state

    Returns:
        state (dict): Updates documents key with only relevant documents
    """
    print("---CHECK DOCUMENT RELEVANCE---")
    question = state["question"]
    documents = state["documents"]
    retry_count = state.get("retry_count", 0)
    
    # LLM with structured output for validation
    llm = get_chat_model(temperature=0)
    structured_llm_grader = llm.with_structured_output(GradeDocuments)

    system = """You are a grader assessing relevance of a retrieved document to a user question. \n 
        If the document contains keyword(s) or semantic meaning related to the user question, grade it as relevant. \n
        It does not need to be a stringent test. The goal is to filter out erroneous retrievals. \n
        Give a binary score 'yes' or 'no' to indicate whether the document is relevant to the question."""
        
    grade_prompt = ChatPromptTemplate.from_messages(
        [
            ("system", system),
            ("human", "Retrieved document: \n\n {document} \n\n User question: {question}"),
        ]
    )
    
    retrieval_grader = grade_prompt | structured_llm_grader
    
    filtered_docs = []
    for d in documents:
        try:
            score = retrieval_grader.invoke({"question": question, "document": d.page_content})
            grade = score.binary_score
        except Exception as e:
            logger.warning("[GRADE] Structured output parsing failed for a doc: %s — keeping it", e)
            grade = "yes"
        if grade == "yes":
            logger.debug("[GRADE] Document RELEVANT")
            filtered_docs.append(d)
        else:
            logger.debug("[GRADE] Document NOT RELEVANT")

    logger.info("[GRADE] %d/%d documents passed relevance filter", len(filtered_docs), len(documents))
    return {"documents": filtered_docs, "question": question, "retry_count": retry_count}

def transform_query(state: GraphState):
    """
    Transform the query to produce a better question.

    Args:
        state (dict): The current graph state

    Returns:
        state (dict): Updates question key with a re-phrased question
    """
    print("---TRANSFORM QUERY---")
    question = state["question"]
    documents = state["documents"]
    retry_count = state.get("retry_count", 0)
    
    # If we are here, it means we had low relevance. Increment retry
    retry_count += 1

    llm = get_chat_model(temperature=0)
    
    system = """You are a helper that enhances search queries. \n
    Look at the input and try to reason about the underlying semantic intent / meaning. \n
    Re-write the initial query to be more effective for vector search."""
    
    re_write_prompt = ChatPromptTemplate.from_messages(
        [
            ("system", system),
            ("human", "Here is the initial question: \n\n {question} \n Formulate an improved question."),
        ]
    )
    
    question_rewriter = re_write_prompt | llm | StrOutputParser()
    better_question = question_rewriter.invoke({"question": question})
    
    return {"documents": documents, "question": better_question, "retry_count": retry_count}

