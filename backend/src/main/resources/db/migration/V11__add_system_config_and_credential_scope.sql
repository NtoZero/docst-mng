-- ============================================================
-- V11: 시스템 설정 및 크리덴셜 스코프 확장
-- 작성일: 2025-12-31
-- 목적: 외부 서비스 설정을 DB로 관리, 크리덴셜 스코프 확장
-- ============================================================

-- ============================================================
-- 1. 시스템 설정 테이블
-- ============================================================
CREATE TABLE dm_system_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT,
    config_type VARCHAR(50) NOT NULL DEFAULT 'STRING',
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ
);

CREATE INDEX idx_system_config_key ON dm_system_config(config_key);

COMMENT ON TABLE dm_system_config IS '시스템 전역 설정 (외부 서비스 URL, 기본값 등)';
COMMENT ON COLUMN dm_system_config.config_type IS 'STRING, INTEGER, BOOLEAN, JSON';

-- 초기 설정값 삽입
INSERT INTO dm_system_config (config_key, config_value, config_type, description) VALUES
-- Neo4j
('neo4j.uri', 'bolt://localhost:7697', 'STRING', 'Neo4j Bolt URI'),
('neo4j.enabled', 'false', 'BOOLEAN', 'Neo4j 활성화 여부'),
-- Ollama
('ollama.base-url', 'http://localhost:11434', 'STRING', 'Ollama 서버 URL'),
('ollama.enabled', 'false', 'BOOLEAN', 'Ollama 활성화 여부'),
-- Embedding 기본값
('embedding.default-provider', 'openai', 'STRING', '기본 임베딩 제공자 (openai/ollama)'),
('embedding.default-model', 'text-embedding-3-small', 'STRING', '기본 임베딩 모델'),
('embedding.default-dimensions', '1536', 'INTEGER', '기본 임베딩 차원');

-- ============================================================
-- 2. 크리덴셜 스코프 및 프로젝트 연결 확장
-- ============================================================

-- user_id를 nullable로 변경 (SYSTEM 스코프는 user_id 없음)
ALTER TABLE dm_credential ALTER COLUMN user_id DROP NOT NULL;

-- 스코프 컬럼 추가
ALTER TABLE dm_credential ADD COLUMN scope VARCHAR(20) NOT NULL DEFAULT 'USER';

-- 프로젝트 연결 (PROJECT 스코프용)
ALTER TABLE dm_credential ADD COLUMN project_id UUID REFERENCES dm_project(id) ON DELETE CASCADE;

-- 기존 유니크 제약조건 제거
ALTER TABLE dm_credential DROP CONSTRAINT IF EXISTS uq_credential_user_name;

-- 새로운 유니크 제약조건 (scope, user_id, project_id, name 조합)
-- NULLS NOT DISTINCT: NULL 값도 중복으로 간주
ALTER TABLE dm_credential ADD CONSTRAINT uq_credential_scope_name
    UNIQUE NULLS NOT DISTINCT (scope, user_id, project_id, name);

-- 인덱스
CREATE INDEX idx_credential_scope ON dm_credential(scope);
CREATE INDEX idx_credential_project_id ON dm_credential(project_id) WHERE project_id IS NOT NULL;
CREATE INDEX idx_credential_type_scope ON dm_credential(type, scope);

-- 스코프 체크 제약조건
ALTER TABLE dm_credential ADD CONSTRAINT chk_credential_scope
    CHECK (scope IN ('USER', 'SYSTEM', 'PROJECT'));

-- 스코프별 필수 필드 검증
ALTER TABLE dm_credential ADD CONSTRAINT chk_credential_scope_fields
    CHECK (
        (scope = 'USER' AND user_id IS NOT NULL AND project_id IS NULL) OR
        (scope = 'SYSTEM' AND user_id IS NULL AND project_id IS NULL) OR
        (scope = 'PROJECT' AND project_id IS NOT NULL)
    );

-- 타입 체크 제약조건 업데이트 (새 타입 추가)
ALTER TABLE dm_credential DROP CONSTRAINT IF EXISTS chk_credential_type;
ALTER TABLE dm_credential ADD CONSTRAINT chk_credential_type
    CHECK (type IN (
        'GITHUB_PAT', 'BASIC_AUTH', 'SSH_KEY',
        'OPENAI_API_KEY', 'NEO4J_AUTH', 'ANTHROPIC_API_KEY', 'CUSTOM_API_KEY'
    ));

-- ============================================================
-- 주석
-- ============================================================
COMMENT ON COLUMN dm_credential.scope IS 'USER: 사용자별, SYSTEM: 시스템 전역, PROJECT: 프로젝트별';
COMMENT ON COLUMN dm_credential.project_id IS 'PROJECT 스코프일 때만 사용';
COMMENT ON CONSTRAINT chk_credential_scope_fields ON dm_credential IS '스코프별 필수 필드 검증: USER(user_id 필수), SYSTEM(둘 다 null), PROJECT(project_id 필수)';
