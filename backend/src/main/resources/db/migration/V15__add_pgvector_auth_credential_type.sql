-- ============================================================
-- V15: PGVECTOR_AUTH 크리덴셜 타입 추가
-- 작성일: 2026-01-07
-- 목적: chk_credential_type 제약조건에 PGVECTOR_AUTH 타입 추가
-- ============================================================

-- 기존 제약조건 삭제 후 재생성 (PGVECTOR_AUTH 포함)
ALTER TABLE dm_credential DROP CONSTRAINT IF EXISTS chk_credential_type;
ALTER TABLE dm_credential ADD CONSTRAINT chk_credential_type
    CHECK (type IN (
        'GITHUB_PAT', 'BASIC_AUTH', 'SSH_KEY',
        'OPENAI_API_KEY', 'NEO4J_AUTH', 'PGVECTOR_AUTH',
        'ANTHROPIC_API_KEY', 'CUSTOM_API_KEY'
    ));

COMMENT ON CONSTRAINT chk_credential_type ON dm_credential IS '크리덴셜 타입 제한: Git(GITHUB_PAT, BASIC_AUTH, SSH_KEY), API(OPENAI_API_KEY, ANTHROPIC_API_KEY, CUSTOM_API_KEY), DB(NEO4J_AUTH, PGVECTOR_AUTH)';