-- Glossary Term table
-- Phase 17: 프로젝트별 용어 사전
CREATE TABLE dm_glossary_term (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES dm_project(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    definition TEXT NOT NULL,
    synonyms JSONB DEFAULT '[]',
    category VARCHAR(100),
    abbreviation VARCHAR(50),
    related_terms JSONB DEFAULT '[]',
    created_by UUID REFERENCES dm_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, name)
);

-- Full-text search index for keyword search
CREATE INDEX idx_glossary_term_fts ON dm_glossary_term
    USING GIN (to_tsvector('english', name || ' ' || COALESCE(definition, '')));

-- Filter indexes
CREATE INDEX idx_glossary_term_project ON dm_glossary_term(project_id);
CREATE INDEX idx_glossary_term_category ON dm_glossary_term(project_id, category);
CREATE INDEX idx_glossary_term_name ON dm_glossary_term(project_id, LOWER(name));

-- Comments
COMMENT ON TABLE dm_glossary_term IS 'Project glossary terms for consistent documentation';
COMMENT ON COLUMN dm_glossary_term.synonyms IS 'Array of synonym strings in JSONB format';
COMMENT ON COLUMN dm_glossary_term.related_terms IS 'Array of related term UUIDs in JSONB format';
