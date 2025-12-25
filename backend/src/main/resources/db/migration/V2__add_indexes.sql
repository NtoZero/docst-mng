-- Docst Database Schema v2
-- Add indexes for query optimization

-- Project member indexes
CREATE INDEX idx_project_member_project_id ON dm_project_member(project_id);
CREATE INDEX idx_project_member_user_id ON dm_project_member(user_id);

-- Repository indexes
CREATE INDEX idx_repository_project_id ON dm_repository(project_id);
CREATE INDEX idx_repository_provider_external_id ON dm_repository(provider, external_id) WHERE external_id IS NOT NULL;

-- Document indexes
CREATE INDEX idx_document_repository_id ON dm_document(repository_id);
CREATE INDEX idx_document_path ON dm_document(path);
CREATE INDEX idx_document_doc_type ON dm_document(doc_type);
CREATE INDEX idx_document_not_deleted ON dm_document(repository_id) WHERE deleted = false;

-- Document version indexes
CREATE INDEX idx_document_version_document_id ON dm_document_version(document_id);
CREATE INDEX idx_document_version_commit_sha ON dm_document_version(commit_sha);
CREATE INDEX idx_document_version_committed_at ON dm_document_version(committed_at DESC);

-- Sync job indexes
CREATE INDEX idx_sync_job_repository_id ON dm_sync_job(repository_id);
CREATE INDEX idx_sync_job_status ON dm_sync_job(status);
CREATE INDEX idx_sync_job_created_at ON dm_sync_job(created_at DESC);
