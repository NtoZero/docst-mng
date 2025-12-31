package com.docst.rag.config;

import com.docst.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 전역 RAG 설정 (Phase 4-E: DB 기반).
 * 이전에는 application.yml에서 로드했지만, 이제는 dm_system_config 테이블에서 로드한다.
 *
 * 설정 키:
 * - embedding.default-provider: openai, ollama
 * - embedding.default-model: text-embedding-3-small, nomic-embed-text
 * - embedding.default-dimensions: 1536, 768
 * - neo4j.enabled: true, false
 * - neo4j.max-hop: 2
 * - neo4j.entity-extraction-model: gpt-4o-mini
 * - rag.pgvector.enabled: true
 * - rag.pgvector.similarity-threshold: 0.5
 * - rag.hybrid.fusion-strategy: rrf, weighted_sum
 * - rag.hybrid.rrf-k: 60
 * - rag.hybrid.vector-weight: 0.6
 * - rag.hybrid.graph-weight: 0.4
 */
@Component
@RequiredArgsConstructor
public class RagGlobalProperties {

    private final SystemConfigService systemConfigService;

    public Embedding getEmbedding() {
        return new Embedding(
            systemConfigService.getString(SystemConfigService.EMBEDDING_DEFAULT_PROVIDER, "openai"),
            systemConfigService.getString(SystemConfigService.EMBEDDING_DEFAULT_MODEL, "text-embedding-3-small"),
            systemConfigService.getInt(SystemConfigService.EMBEDDING_DEFAULT_DIMENSIONS, 1536)
        );
    }

    public PgVector getPgvector() {
        return new PgVector(
            systemConfigService.getBoolean(SystemConfigService.RAG_PGVECTOR_ENABLED, true),
            systemConfigService.getDouble(SystemConfigService.RAG_PGVECTOR_SIMILARITY_THRESHOLD, 0.5)
        );
    }

    public Neo4j getNeo4j() {
        return new Neo4j(
            systemConfigService.getBoolean(SystemConfigService.NEO4J_ENABLED, false),
            systemConfigService.getInt(SystemConfigService.NEO4J_MAX_HOP, 2),
            systemConfigService.getString(SystemConfigService.NEO4J_ENTITY_EXTRACTION_MODEL, "gpt-4o-mini")
        );
    }

    public Hybrid getHybrid() {
        return new Hybrid(
            systemConfigService.getString(SystemConfigService.RAG_HYBRID_FUSION_STRATEGY, "rrf"),
            systemConfigService.getInt(SystemConfigService.RAG_HYBRID_RRF_K, 60),
            systemConfigService.getDouble(SystemConfigService.RAG_HYBRID_VECTOR_WEIGHT, 0.6),
            systemConfigService.getDouble(SystemConfigService.RAG_HYBRID_GRAPH_WEIGHT, 0.4)
        );
    }

    /**
     * 임베딩 설정.
     */
    public record Embedding(String provider, String model, int dimensions) {}

    /**
     * PgVector 설정.
     */
    public record PgVector(boolean enabled, double similarityThreshold) {}

    /**
     * Neo4j 설정.
     */
    public record Neo4j(boolean enabled, int maxHop, String entityExtractionModel) {}

    /**
     * Hybrid 설정.
     */
    public record Hybrid(String fusionStrategy, int rrfK, double vectorWeight, double graphWeight) {}
}
