-- Docst Database Schema v5
-- Add document chunking support

-- Document chunks table
-- Stores document content split into chunks for embedding and semantic search
CREATE TABLE dm_doc_chunk (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_version_id UUID NOT NULL REFERENCES dm_document_version(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    heading_path TEXT,
    content TEXT NOT NULL,
    token_count INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (document_version_id, chunk_index)
);

-- Index for efficient chunk retrieval by document version
CREATE INDEX idx_doc_chunk_document_version ON dm_doc_chunk(document_version_id);

-- Index for ordering chunks by index
CREATE INDEX idx_doc_chunk_document_version_index ON dm_doc_chunk(document_version_id, chunk_index);

-- Comments for documentation
COMMENT ON TABLE dm_doc_chunk IS 'Document chunks created from document versions for semantic search';
COMMENT ON COLUMN dm_doc_chunk.document_version_id IS 'Reference to the document version this chunk belongs to';
COMMENT ON COLUMN dm_doc_chunk.chunk_index IS 'Sequential index of this chunk within the document version (0-based)';
COMMENT ON COLUMN dm_doc_chunk.heading_path IS 'Hierarchical heading path (e.g., "# Title > ## Section > ### Subsection")';
COMMENT ON COLUMN dm_doc_chunk.content IS 'Chunk content (markdown text)';
COMMENT ON COLUMN dm_doc_chunk.token_count IS 'Number of tokens in this chunk (cl100k_base encoding)';
