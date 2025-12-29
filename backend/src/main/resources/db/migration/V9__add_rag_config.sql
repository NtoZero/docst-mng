-- Phase 4: Graph RAG & Hybrid RAG 지원을 위한 스키마 추가
-- 작성일: 2025-12-29

-- 1. Project에 RAG 모드 컬럼 추가 (nullable - UI에서 선택)
-- null이면 전역 기본값(auto) 또는 검색 요청의 mode 파라미터 사용
ALTER TABLE dm_project
ADD COLUMN rag_mode VARCHAR(20)
    CHECK (rag_mode IN ('pgvector', 'neo4j', 'hybrid'));

COMMENT ON COLUMN dm_project.rag_mode IS 'RAG 검색 모드 (nullable, UI에서 설정. null이면 자동 선택)';

-- 2. Project에 RAG 설정 컬럼 추가 (JSONB)
ALTER TABLE dm_project
ADD COLUMN rag_config JSONB;

COMMENT ON COLUMN dm_project.rag_config IS 'RAG 모드별 상세 설정 (JSON 형식)';

-- 3. 엔티티 테이블 생성
CREATE TABLE dm_entity (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES dm_project(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    description TEXT,
    source_chunk_id UUID REFERENCES dm_doc_chunk(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, name, type)
);

COMMENT ON TABLE dm_entity IS '문서에서 추출된 엔티티 (Concept, API, Component, Technology)';
COMMENT ON COLUMN dm_entity.type IS '엔티티 유형: Concept, API, Component, Technology';
COMMENT ON COLUMN dm_entity.source_chunk_id IS '엔티티가 처음 발견된 청크';

CREATE INDEX idx_entity_project ON dm_entity(project_id);
CREATE INDEX idx_entity_name ON dm_entity(name);
CREATE INDEX idx_entity_type ON dm_entity(type);

-- 4. 엔티티 관계 테이블 생성
CREATE TABLE dm_entity_relation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_entity_id UUID NOT NULL REFERENCES dm_entity(id) ON DELETE CASCADE,
    target_entity_id UUID NOT NULL REFERENCES dm_entity(id) ON DELETE CASCADE,
    relation_type TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE dm_entity_relation IS '엔티티 간 관계';
COMMENT ON COLUMN dm_entity_relation.relation_type IS '관계 유형: RELATED_TO, DEPENDS_ON, USES, PART_OF';

CREATE INDEX idx_entity_rel_source ON dm_entity_relation(source_entity_id);
CREATE INDEX idx_entity_rel_target ON dm_entity_relation(target_entity_id);
CREATE INDEX idx_entity_rel_type ON dm_entity_relation(relation_type);
