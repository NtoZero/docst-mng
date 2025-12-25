-- Docst Database Schema v1
-- Initial schema creation

-- Users table
CREATE TABLE dm_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(20) NOT NULL CHECK (provider IN ('GITHUB', 'LOCAL')),
    provider_user_id VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    display_name VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_user_id)
);

-- Projects table
CREATE TABLE dm_project (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Project members (many-to-many between project and user)
CREATE TABLE dm_project_member (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES dm_project(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES dm_user(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'EDITOR', 'VIEWER')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, user_id)
);

-- Repositories table
CREATE TABLE dm_repository (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES dm_project(id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL CHECK (provider IN ('GITHUB', 'LOCAL')),
    external_id VARCHAR(255),
    owner VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    clone_url VARCHAR(500),
    default_branch VARCHAR(100) DEFAULT 'main',
    local_mirror_path VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, provider, owner, name)
);

-- Documents table
CREATE TABLE dm_document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id UUID NOT NULL REFERENCES dm_repository(id) ON DELETE CASCADE,
    path VARCHAR(500) NOT NULL,
    title VARCHAR(255) NOT NULL,
    doc_type VARCHAR(20) NOT NULL CHECK (doc_type IN ('MD', 'ADOC', 'OPENAPI', 'ADR', 'OTHER')),
    latest_commit_sha VARCHAR(40),
    deleted BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (repository_id, path)
);

-- Document versions table
CREATE TABLE dm_document_version (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES dm_document(id) ON DELETE CASCADE,
    commit_sha VARCHAR(40) NOT NULL,
    author_name VARCHAR(255),
    author_email VARCHAR(255),
    committed_at TIMESTAMPTZ,
    message TEXT,
    content_hash VARCHAR(64),
    content TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (document_id, commit_sha)
);

-- Sync jobs table
CREATE TABLE dm_sync_job (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id UUID NOT NULL REFERENCES dm_repository(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    target_branch VARCHAR(100),
    last_synced_commit VARCHAR(40),
    error_message TEXT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Comments for documentation
COMMENT ON TABLE dm_user IS 'Users authenticated via GitHub or local login';
COMMENT ON TABLE dm_project IS 'Projects that group multiple repositories';
COMMENT ON TABLE dm_project_member IS 'Project membership with role-based access';
COMMENT ON TABLE dm_repository IS 'Git repositories connected to projects';
COMMENT ON TABLE dm_document IS 'Document metadata extracted from repositories';
COMMENT ON TABLE dm_document_version IS 'Document content at specific Git commits';
COMMENT ON TABLE dm_sync_job IS 'Repository synchronization job tracking';
