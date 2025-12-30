package com.docst.rag.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Project.ragConfig JSONB 컬럼에 저장되는 설정 DTO.
 *
 * JSON 스키마:
 * {
 *   "version": "1.0",
 *   "embedding": { "provider": "openai", "model": "text-embedding-3-small", "dimensions": 1536 },
 *   "pgvector": { "enabled": true, "similarityThreshold": 0.5 },
 *   "neo4j": { "enabled": false, "maxHop": 2, "entityExtractionModel": "gpt-4o-mini" },
 *   "hybrid": { "fusionStrategy": "rrf", "rrfK": 60, "vectorWeight": 0.6, "graphWeight": 0.4 }
 * }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record RagConfigDto(
    String version,
    EmbeddingConfig embedding,
    PgVectorConfig pgvector,
    Neo4jConfig neo4j,
    HybridConfig hybrid
) {
    public static final String CURRENT_VERSION = "1.0";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmbeddingConfig(
        String provider,
        String model,
        Integer dimensions
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PgVectorConfig(
        Boolean enabled,
        Double similarityThreshold
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Neo4jConfig(
        Boolean enabled,
        Integer maxHop,
        String entityExtractionModel
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HybridConfig(
        String fusionStrategy,
        Integer rrfK,
        Double vectorWeight,
        Double graphWeight
    ) {}

    /**
     * 기본 설정 생성 (version만 포함).
     */
    public static RagConfigDto empty() {
        return new RagConfigDto(CURRENT_VERSION, null, null, null, null);
    }

    /**
     * 기본값을 사용하는 설정 생성.
     */
    public static RagConfigDto withDefaults() {
        return new RagConfigDto(
            CURRENT_VERSION,
            new EmbeddingConfig("openai", "text-embedding-3-small", 1536),
            new PgVectorConfig(true, 0.5),
            new Neo4jConfig(false, 2, "gpt-4o-mini"),
            new HybridConfig("rrf", 60, 0.6, 0.4)
        );
    }
}
