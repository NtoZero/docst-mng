package com.docst.rag;

/**
 * RAG 검색 모드.
 * Phase 4: Graph RAG & Hybrid RAG 지원
 */
public enum RagMode {
    /**
     * Mode 1: PgVector 벡터 검색만 사용
     */
    PGVECTOR,

    /**
     * Mode 2: Neo4j 그래프 검색만 사용
     */
    NEO4J,

    /**
     * Mode 3: PgVector + Neo4j 하이브리드 검색
     */
    HYBRID
}
