import os
import shutil
from pathlib import Path
from typing import Dict, Any

from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_core.documents import Document
# Parsers
from pypdf import PdfReader
from docx import Document as DocxDocument
import aiofiles

from app.services.vector_db import get_vector_service
from app.services.task_manager import get_task_manager, TaskStatus
import re

def clean_content(text: str) -> str:
    """
    Clean text content:
    - Remove multiple spaces/newlines
    - Remove non-printable characters
    - Normalize whitespace
    """
    # Normalize horizontal whitespace (spaces, tabs)
    text = re.sub(r'[ \t]+', ' ', text)
    # Normalize vertical whitespace (newlines)
    text = re.sub(r'\n+', '\n', text)
    # Remove non-printable characters (except standard whitespace)
    text = "".join(char for char in text if char.isprintable() or char in "\n\t")
    return text.strip()

def parse_pdf(file_path: Path) -> str:
    reader = PdfReader(str(file_path))
    text = ""
    for page in reader.pages:
        text += page.extract_text() + "\n"
    return text

def parse_docx(file_path: Path) -> str:
    doc = DocxDocument(str(file_path))
    text = "\n".join([para.text for para in doc.paragraphs])
    return text

async def parse_text(file_path: Path) -> str:
    async with aiofiles.open(file_path, mode='r', encoding='utf-8') as f:
        return await f.read()

async def process_document(file_path: Path, metadata: Dict[str, Any], file_hash: str, task_id: str):
    """
    Background task to process document:
    1. Parse content based on extension
    2. Split into chunks
    3. Embed and Index
    4. Cleanup
    """
    task_manager = get_task_manager()
    task_manager.update_task(task_id, TaskStatus.PROCESSING, message="Starting processing")
    
    try:
        # 1. Parse
        task_manager.update_task(task_id, TaskStatus.PROCESSING, message="Parsing document")
        content = ""
        ext = file_path.suffix.lower()
        
        if ext == ".pdf":
            content = parse_pdf(file_path)
        elif ext == ".docx":
            content = parse_docx(file_path)
        elif ext == ".txt":
            content = await parse_text(file_path)
        else:
            print(f"Unsupported file type in worker: {ext}") 
            return # Should be caught by validation, but good safety

        if not content:
            print(f"Empty content for file: {file_path}")
            task_manager.update_task(task_id, TaskStatus.FAILED, message="Extracted content was empty")
            return

        # 1.1 Clean
        task_manager.update_task(task_id, TaskStatus.PROCESSING, message="Cleaning text")
        content = clean_content(content)

        # 2. Split
        task_manager.update_task(task_id, TaskStatus.PROCESSING, message="Splitting text")
        text_splitter = RecursiveCharacterTextSplitter(
            chunk_size=1000,
            chunk_overlap=200,
            separators=["\n\n", "\n", ".", " ", ""],
            length_function=len,
        )
        
        chunks = text_splitter.split_text(content)
        
        # Create Language Documents
        docs = []
        for chunk in chunks:
            # Merge original metadata with file_hash
            doc_metadata = metadata.copy()
            doc_metadata["file_hash"] = file_hash
            doc_metadata["source"] = str(file_path.name)
            
            docs.append(Document(page_content=chunk, metadata=doc_metadata))

        # 3. Index
        task_manager.update_task(task_id, TaskStatus.PROCESSING, message="Indexing vectors")
        vector_service = get_vector_service()
        vector_service.add_documents(docs)
        print(f"Successfully indexed {len(docs)} chunks for {file_path.name}")
        
        task_manager.update_task(task_id, TaskStatus.COMPLETED, message=f"Indexed {len(docs)} chunks")

    except Exception as e:
        print(f"Error processing document {file_path}: {e}")
        task_manager.update_task(task_id, TaskStatus.FAILED, error=str(e), message="Processing failed")
    finally:
        # 4. Cleanup
        if file_path.exists():
            os.remove(file_path)
            print(f"Cleaned up temp file: {file_path}")
