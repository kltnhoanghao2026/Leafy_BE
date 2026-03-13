import os
from typing import List, Optional
from langchain_qdrant import QdrantVectorStore
from qdrant_client import QdrantClient, models as qdrant_models
from dotenv import load_dotenv
from app.core.ai_providers import get_embeddings_model

load_dotenv()

class VectorStoreService:
    _instance = None
    
    def __new__(cls):
        if cls._instance is None:
            instance = super(VectorStoreService, cls).__new__(cls)
            try:
                instance._initialize()
                cls._instance = instance  # Only cache after successful init
            except Exception as e:
                cls._instance = None  # Don't cache a broken instance
                raise RuntimeError(
                    f"VectorStoreService failed to initialize. "
                    f"Is Qdrant running at the configured URL? Details: {e}"
                ) from e
        return cls._instance
    
    def _initialize(self):
        self.qdrant_url = os.getenv("QDRANT_URL", "http://localhost:6333")
        self.qdrant_api_key = os.getenv("QDRANT_API_KEY", None)
        self.collection_name = "coffee_research"
        
        # Initialize Qdrant Client
        self.client = QdrantClient(
            url=self.qdrant_url,
            api_key=self.qdrant_api_key
        )
        
        # Initialize Embeddings
        self.embeddings = get_embeddings_model()
        
        # Determine embedding dimension
        self.embedding_dimension = len(self.embeddings.embed_query("test"))

        # Ensure Collection Exists
        self._ensure_collection_exists()
        
        # Initialize Vector Store
        self.vector_store = QdrantVectorStore(
            client=self.client,
            collection_name=self.collection_name,
            embedding=self.embeddings,
        )
        
        # Initialize BM25 for hybrid search (lazy-loaded on first use)
        self._bm25_index = None
        self._cached_documents = None
        
    def _ensure_collection_exists(self):
        """Check if collection exists, if not create it."""
        if not self.client.collection_exists(self.collection_name):
            self.client.create_collection(
                collection_name=self.collection_name,
                vectors_config=qdrant_models.VectorParams(
                    size=self.embedding_dimension,
                    distance=qdrant_models.Distance.COSINE
                )
            )

    def add_documents(self, documents: List):
        """Add documents to the vector store."""
        return self.vector_store.add_documents(documents)
        
    def check_existing_hash(self, file_hash: str) -> bool:
        """Check if a document with this hash already exists."""
        # We assume strict deduplication where we query by payload hash
        # This requires that we store the hash in the payload when ingesting
        scroll_result = self.client.scroll(
            collection_name=self.collection_name,
            scroll_filter=qdrant_models.Filter(
                must=[
                    qdrant_models.FieldCondition(
                        key="metadata.file_hash",
                        match=qdrant_models.MatchValue(value=file_hash)
                    )
                ]
            ),
            limit=1
        )
        # scroll_result is expected to be a tuple (points, next_page_offset)
        # or an object depending on version. QdrantClient sync scroll returns (points, next_page_offset)
        points, _ = scroll_result
        return len(points) > 0
    
    def _load_all_documents(self):
        """Load all documents from Qdrant for BM25 indexing."""
        from langchain_core.documents import Document
        
        # Scroll through all points in the collection
        all_points = []
        offset = None
        
        while True:
            scroll_result = self.client.scroll(
                collection_name=self.collection_name,
                limit=100,
                offset=offset,
                with_payload=True,
                with_vectors=False
            )
            points, offset = scroll_result
            all_points.extend(points)
            
            if offset is None:
                break
        
        # Convert to LangChain documents
        documents = []
        for point in all_points:
            content = point.payload.get("page_content", "")
            metadata = point.payload.get("metadata", {})
            documents.append(Document(page_content=content, metadata=metadata))
        
        return documents
    
    def _initialize_bm25(self):
        """Initialize BM25 index from cached documents."""
        from rank_bm25 import BM25Okapi
        
        if self._cached_documents is None:
            self._cached_documents = self._load_all_documents()
        
        if not self._cached_documents:
            # Empty collection — BM25Okapi crashes with ZeroDivisionError on an empty corpus
            self._bm25_index = None
            return None
        
        # Tokenize documents for BM25
        tokenized_corpus = [doc.page_content.lower().split() for doc in self._cached_documents]
        self._bm25_index = BM25Okapi(tokenized_corpus)
        
        return self._bm25_index
    
    def _bm25_search(self, query: str, k: int = 10) -> List:
        """Perform BM25 sparse keyword search."""
        from langchain_core.documents import Document
        
        # Lazy-load BM25 index
        if self._bm25_index is None:
            self._initialize_bm25()
        
        # Guard: no documents in collection yet
        if self._bm25_index is None or not self._cached_documents:
            return []
        
        # Tokenize query
        tokenized_query = query.lower().split()
        
        # Get BM25 scores
        scores = self._bm25_index.get_scores(tokenized_query)
        
        # Get top-k indices
        top_indices = sorted(range(len(scores)), key=lambda i: scores[i], reverse=True)[:k]
        
        # Return top documents
        return [self._cached_documents[i] for i in top_indices]
    
    def _reciprocal_rank_fusion(self, dense_results: List, sparse_results: List, k: int = 10) -> List:
        """
        Combine dense and sparse results using Reciprocal Rank Fusion.
        
        RRF formula: score(doc) = sum(1 / (rank + k)) for all rankings containing doc
        Using k=60 as per original RRF paper.
        """
        from langchain_core.documents import Document
        
        rrf_k = 60
        doc_scores = {}
        
        # Score dense results
        for rank, doc in enumerate(dense_results):
            doc_id = id(doc) if not hasattr(doc, 'id') else doc.id
            # Use page_content as unique identifier
            doc_key = doc.page_content
            doc_scores[doc_key] = doc_scores.get(doc_key, 0) + 1 / (rank + rrf_k)
        
        # Score sparse results
        for rank, doc in enumerate(sparse_results):
            doc_key = doc.page_content
            doc_scores[doc_key] = doc_scores.get(doc_key, 0) + 1 / (rank + rrf_k)
        
        # Sort by combined score
        sorted_docs = sorted(doc_scores.items(), key=lambda x: x[1], reverse=True)
        
        # Map back to document objects (use dense_results as primary source)
        doc_map = {doc.page_content: doc for doc in dense_results + sparse_results}
        
        # Return top-k
        return [doc_map[doc_key] for doc_key, score in sorted_docs[:k]]
    
    def hybrid_search(self, query: str, dense_k: int = 20, sparse_k: int = 20, final_k: int = 10) -> List:
        """
        Perform hybrid search combining dense vector search and sparse BM25 search.
        
        Args:
            query: Search query
            dense_k: Number of results from vector search
            sparse_k: Number of results from BM25 search
            final_k: Number of final combined results
            
        Returns:
            List of top-k documents after RRF fusion
        """
        # Dense vector search
        retriever = self.vector_store.as_retriever(search_kwargs={"k": dense_k})
        dense_results = retriever.invoke(query)
        
        # Sparse BM25 search
        sparse_results = self._bm25_search(query, k=sparse_k)
        
        # Combine with RRF
        hybrid_results = self._reciprocal_rank_fusion(dense_results, sparse_results, k=final_k)
        
        return hybrid_results

# Global instance getter
def get_vector_service():
    return VectorStoreService()
