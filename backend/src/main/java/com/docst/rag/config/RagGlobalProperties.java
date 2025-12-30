package com.docst.rag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 전역 RAG 설정 (application.yml).
 *
 * 설정 예시:
 * docst:
 *   rag:
 *     embedding:
 *       provider: openai
 *       model: text-embedding-3-small
 *       dimensions: 1536
 *     pgvector:
 *       enabled: true
 *       similarity-threshold: 0.5
 *     neo4j:
 *       enabled: false
 *       max-hop: 2
 *       entity-extraction-model: gpt-4o-mini
 *     hybrid:
 *       fusion-strategy: rrf
 *       rrf-k: 60
 *       vector-weight: 0.6
 *       graph-weight: 0.4
 */
@Component
@ConfigurationProperties(prefix = "docst.rag")
@Getter
@Setter
public class RagGlobalProperties {

    private Embedding embedding = new Embedding();
    private PgVector pgvector = new PgVector();
    private Neo4j neo4j = new Neo4j();
    private Hybrid hybrid = new Hybrid();

    @Getter
    @Setter
    public static class Embedding {
        private String provider = "openai";
        private String model = "text-embedding-3-small";
        private int dimensions = 1536;
    }

    @Getter
    @Setter
    public static class PgVector {
        private boolean enabled = true;
        private double similarityThreshold = 0.5;
    }

    @Getter
    @Setter
    public static class Neo4j {
        private boolean enabled = false;
        private int maxHop = 2;
        private String entityExtractionModel = "gpt-4o-mini";
    }

    @Getter
    @Setter
    public static class Hybrid {
        private String fusionStrategy = "rrf";
        private int rrfK = 60;
        private double vectorWeight = 0.6;
        private double graphWeight = 0.4;
    }
}
