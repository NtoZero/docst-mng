CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE dm_user (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  provider text NOT NULL CHECK (provider IN ('GITHUB', 'LOCAL')),
  provider_user_id text NOT NULL,
  email text,
  display_name text,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (provider, provider_user_id)
);

CREATE TABLE dm_project (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  description text,
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE dm_project_member (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id uuid NOT NULL REFERENCES dm_project(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES dm_user(id) ON DELETE CASCADE,
  role text NOT NULL CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER')),
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (project_id, user_id)
);

CREATE TABLE dm_repository (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id uuid NOT NULL REFERENCES dm_project(id) ON DELETE CASCADE,
  provider text NOT NULL CHECK (provider IN ('GITHUB', 'LOCAL')),
  external_id text,
  owner text NOT NULL,
  name text NOT NULL,
  clone_url text,
  default_branch text,
  local_mirror_path text,
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (project_id, provider, owner, name)
);

CREATE TABLE dm_document (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  repository_id uuid NOT NULL REFERENCES dm_repository(id) ON DELETE CASCADE,
  path text NOT NULL,
  title text NOT NULL,
  doc_type text NOT NULL CHECK (doc_type IN ('MD','ADOC','OPENAPI','ADR','OTHER')),
  latest_commit_sha text,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (repository_id, path)
);

CREATE TABLE dm_document_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  document_id uuid NOT NULL REFERENCES dm_document(id) ON DELETE CASCADE,
  commit_sha text NOT NULL,
  author_name text,
  author_email text,
  committed_at timestamptz,
  message text,
  content_hash text,
  UNIQUE (document_id, commit_sha)
);

CREATE TABLE dm_doc_chunk (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  document_version_id uuid NOT NULL REFERENCES dm_document_version(id) ON DELETE CASCADE,
  chunk_index integer NOT NULL,
  heading_path text,
  content text NOT NULL,
  token_count integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (document_version_id, chunk_index)
);

CREATE TABLE dm_doc_embedding (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  doc_chunk_id uuid NOT NULL REFERENCES dm_doc_chunk(id) ON DELETE CASCADE,
  model text NOT NULL,
  embedding vector(1536) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (doc_chunk_id, model)
);

CREATE TABLE dm_sync_job (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  repository_id uuid NOT NULL REFERENCES dm_repository(id) ON DELETE CASCADE,
  status text NOT NULL CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','FAILED')),
  target_branch text,
  last_synced_commit text,
  error_message text,
  started_at timestamptz,
  finished_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_repo_project_id ON dm_repository(project_id);
CREATE INDEX idx_doc_repo_id ON dm_document(repository_id);
CREATE INDEX idx_docver_doc_id ON dm_document_version(document_id);
CREATE INDEX idx_chunk_docver_id ON dm_doc_chunk(document_version_id);
CREATE INDEX idx_job_repo_id ON dm_sync_job(repository_id);

ALTER TABLE dm_doc_chunk
  ADD COLUMN content_tsv tsvector GENERATED ALWAYS AS (to_tsvector('english', coalesce(content,''))) STORED;

CREATE INDEX idx_chunk_content_tsv_gin ON dm_doc_chunk USING GIN (content_tsv);

CREATE INDEX idx_embedding_ivfflat
  ON dm_doc_embedding
  USING ivfflat (embedding vector_cosine_ops)
  WITH (lists = 100);
