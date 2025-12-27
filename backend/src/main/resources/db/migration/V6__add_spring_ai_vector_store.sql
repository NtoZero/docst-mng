-- Docst Database Schema v6
-- Add Spring AI VectorStore support

-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Spring AI VectorStore table (default schema)
-- This table is automatically used by Spring AI PgVectorStore
CREATE TABLE IF NOT EXISTS vector_store (
    id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content text,
    metadata jsonb,  -- Use jsonb for GIN indexing support
    embedding vector(1536)  -- OpenAI text-embedding-3-small default (1536 dims), adjustable via config
);

-- HNSW index for cosine similarity search
-- HNSW provides better performance than IVFFlat for most use cases
CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
    ON vector_store
    USING HNSW (embedding vector_cosine_ops);

-- Metadata index for filtering by project_id, repository_id, etc.
-- Using jsonb_path_ops for better performance on containment queries (@> operator)
CREATE INDEX IF NOT EXISTS vector_store_metadata_idx
    ON vector_store
    USING GIN (metadata jsonb_path_ops);

-- Comments for documentation
COMMENT ON TABLE vector_store IS 'Spring AI VectorStore table for document embeddings';
COMMENT ON COLUMN vector_store.id IS 'Unique identifier (UUID)';
COMMENT ON COLUMN vector_store.content IS 'Chunk content (text from dm_doc_chunk)';
COMMENT ON COLUMN vector_store.metadata IS 'JSON metadata including doc_chunk_id, heading_path, document_version_id, project_id, repository_id';
COMMENT ON COLUMN vector_store.embedding IS 'Vector embedding (dimension depends on embedding model)';
COMMENT ON INDEX vector_store_embedding_idx IS 'HNSW index for fast cosine similarity search';
COMMENT ON INDEX vector_store_metadata_idx IS 'GIN index for metadata filtering (e.g., project_id, repository_id)';

-- Note: DocChunk (dm_doc_chunk) is linked via metadata.doc_chunk_id
-- This allows Spring AI VectorStore to remain independent while maintaining our domain model
