-- Phase 12: 레포지토리별 동기화 설정 추가
-- 파일 확장자, 경로 필터링, 커스텀 패턴을 동적으로 설정 가능

-- 1. dm_repository에 sync_config JSONB 컬럼 추가
ALTER TABLE dm_repository
ADD COLUMN sync_config JSONB DEFAULT '{
  "fileExtensions": ["md", "adoc"],
  "includePaths": [],
  "excludePaths": [".git", "node_modules", "target", "build", ".gradle", "dist", "out"],
  "scanOpenApi": true,
  "scanSwagger": true,
  "customPatterns": []
}'::jsonb;

-- 2. 컬럼 설명 추가
COMMENT ON COLUMN dm_repository.sync_config IS 'Repository synchronization configuration (file extensions, paths, patterns)';

-- 3. JSONB 검색 성능을 위한 GIN 인덱스
CREATE INDEX idx_dm_repository_sync_config ON dm_repository USING gin (sync_config);