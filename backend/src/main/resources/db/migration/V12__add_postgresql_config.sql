-- ============================================================
-- V12: PostgreSQL 연결 정보 추가
-- 작성일: 2025-01-01
-- 목적: PostgreSQL/PgVector 연결 정보를 시스템 설정에 추가
-- ============================================================

-- PostgreSQL 연결 정보 삽입
INSERT INTO dm_system_config (config_key, config_value, config_type, description) VALUES
('postgresql.host', 'localhost', 'STRING', 'PostgreSQL 호스트'),
('postgresql.port', '5434', 'INTEGER', 'PostgreSQL 포트'),
('postgresql.database', 'docst', 'STRING', 'PostgreSQL 데이터베이스명'),
('postgresql.schema', 'public', 'STRING', 'Vector Store 스키마명');
