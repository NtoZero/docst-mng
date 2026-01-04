-- ============================================================
-- V13: PgVector 동적 관리 설정 키 추가
-- 작성일: 2026-01-03
-- 목적: PgVector DataSource를 Admin UI에서 동적으로 관리하기 위한 설정 추가
-- 참조: docs/plan/phase6/phase6-complement.md
-- ============================================================

-- 1. 기존 postgresql.* 키를 pgvector.*로 rename (V12에서 추가된 키들)
UPDATE dm_system_config SET config_key = 'pgvector.host' WHERE config_key = 'postgresql.host';
UPDATE dm_system_config SET config_key = 'pgvector.port' WHERE config_key = 'postgresql.port';
UPDATE dm_system_config SET config_key = 'pgvector.database' WHERE config_key = 'postgresql.database';
UPDATE dm_system_config SET config_key = 'pgvector.schema' WHERE config_key = 'postgresql.schema';

-- 2. 기존 키 설명 업데이트
UPDATE dm_system_config SET description = 'PostgreSQL 호스트 for vector store' WHERE config_key = 'pgvector.host';
UPDATE dm_system_config SET description = 'PostgreSQL 포트 for vector store' WHERE config_key = 'pgvector.port';
UPDATE dm_system_config SET description = 'Vector store 데이터베이스명' WHERE config_key = 'pgvector.database';
UPDATE dm_system_config SET description = 'Vector store 스키마명' WHERE config_key = 'pgvector.schema';

-- 3. 새로운 PgVector 설정 키 추가
INSERT INTO dm_system_config (config_key, config_value, config_type, description)
VALUES
  ('pgvector.enabled', 'false', 'BOOLEAN', 'PgVector 활성화 여부'),
  ('pgvector.table', 'vector_store', 'STRING', 'Vector store 테이블명'),
  ('pgvector.dimensions', '1536', 'INTEGER', 'Embedding 벡터 차원 (OpenAI: 1536)'),
  ('pgvector.distance-type', 'COSINE_DISTANCE', 'STRING', '거리 측정 방식'),
  ('pgvector.index-type', 'HNSW', 'STRING', '인덱스 타입')
ON CONFLICT (config_key) DO NOTHING;
