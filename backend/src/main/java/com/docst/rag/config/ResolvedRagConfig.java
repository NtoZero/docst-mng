package com.docst.rag.config;

import lombok.Builder;
import lombok.Getter;

/**
 * 최종 해결된 RAG 설정.
 *
 * 설정 우선순위:
 * 1. 요청 파라미터 (API 호출 시)
 * 2. 프로젝트 설정 (Project.ragConfig JSONB)
 * 3. 전역 기본값 (application.yml)
 */
@Getter
@Builder
public class ResolvedRagConfig {

    // Embedding 설정
    private final String embeddingProvider;
    private final String embeddingModel;
    private final int embeddingDimensions;

    // PgVector 설정
    private final boolean pgvectorEnabled;
    private final double similarityThreshold;

    // Neo4j 설정
    private final boolean neo4jEnabled;
    private final int maxHop;
    private final String entityExtractionModel;

    // Hybrid 설정
    private final String fusionStrategy;
    private final int rrfK;
    private final double vectorWeight;
    private final double graphWeight;

    /**
     * 기본 설정 생성 (테스트용).
     */
    public static ResolvedRagConfig defaults() {
        return ResolvedRagConfig.builder()
            .embeddingProvider("openai")
            .embeddingModel("text-embedding-3-small")
            .embeddingDimensions(1536)
            .pgvectorEnabled(true)
            .similarityThreshold(0.5)
            .neo4jEnabled(false)
            .maxHop(2)
            .entityExtractionModel("gpt-4o-mini")
            .fusionStrategy("rrf")
            .rrfK(60)
            .vectorWeight(0.6)
            .graphWeight(0.4)
            .build();
    }

    /**
     * FusionStrategy 이름 반환 (소문자).
     */
    public String getFusionStrategyName() {
        return fusionStrategy != null ? fusionStrategy.toLowerCase() : "rrf";
    }

    /**
     * Hybrid 모드 사용 가능 여부.
     * PgVector와 Neo4j 모두 활성화되어 있어야 함.
     */
    public boolean isHybridAvailable() {
        return pgvectorEnabled && neo4jEnabled;
    }
}
