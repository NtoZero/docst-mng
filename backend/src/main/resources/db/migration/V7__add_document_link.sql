-- V7: Add document link table for graph analysis
-- Created: 2024-01-15

-- Create document_link table
CREATE TABLE dm_document_link (
    id UUID PRIMARY KEY,
    source_document_id UUID NOT NULL REFERENCES dm_document(id) ON DELETE CASCADE,
    target_document_id UUID REFERENCES dm_document(id) ON DELETE CASCADE,
    link_text VARCHAR(1000) NOT NULL,
    link_type VARCHAR(20) NOT NULL,
    is_broken BOOLEAN NOT NULL DEFAULT TRUE,
    line_number INTEGER,
    anchor_text VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for efficient queries
CREATE INDEX idx_document_link_source ON dm_document_link(source_document_id);
CREATE INDEX idx_document_link_target ON dm_document_link(target_document_id);
CREATE INDEX idx_document_link_type ON dm_document_link(link_type);
CREATE INDEX idx_document_link_broken ON dm_document_link(is_broken);

-- Comment
COMMENT ON TABLE dm_document_link IS 'Document link relationships (source -> target)';
COMMENT ON COLUMN dm_document_link.source_document_id IS 'Source document (where the link is from)';
COMMENT ON COLUMN dm_document_link.target_document_id IS 'Target document (where the link points to, NULL if broken)';
COMMENT ON COLUMN dm_document_link.link_text IS 'Original link text (e.g., ./docs/api.md, [[page]])';
COMMENT ON COLUMN dm_document_link.link_type IS 'Link type: INTERNAL, WIKI, EXTERNAL, ANCHOR';
COMMENT ON COLUMN dm_document_link.is_broken IS 'Whether the link is broken (target does not exist)';
COMMENT ON COLUMN dm_document_link.line_number IS 'Line number where the link was found';
COMMENT ON COLUMN dm_document_link.anchor_text IS 'Anchor text (display text of the link)';
