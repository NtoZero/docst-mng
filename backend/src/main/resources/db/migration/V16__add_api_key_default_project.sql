-- API Key에 기본 프로젝트 연결
-- MCP 호출 시 projectId가 없으면 이 프로젝트를 사용

ALTER TABLE dm_api_key
ADD COLUMN default_project_id UUID REFERENCES dm_project(id);

-- 인덱스 추가
CREATE INDEX idx_api_key_default_project ON dm_api_key(default_project_id);

COMMENT ON COLUMN dm_api_key.default_project_id IS 'MCP 호출 시 projectId가 없으면 사용되는 기본 프로젝트';
