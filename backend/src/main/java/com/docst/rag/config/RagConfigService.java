package com.docst.rag.config;

import com.docst.domain.Project;
import com.docst.repository.ProjectRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * RAG 설정 해결 서비스.
 *
 * 설정 우선순위:
 * 1. 요청 파라미터 (검색 API 호출 시)
 * 2. 프로젝트 설정 (Project.ragConfig JSONB)
 * 3. 전역 기본값 (application.yml)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagConfigService {

    private final RagGlobalProperties globalProps;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;

    /**
     * 프로젝트 RAG 설정을 해결한다.
     *
     * @param project 프로젝트
     * @param requestParams 요청 파라미터 (nullable)
     * @return 해결된 설정
     */
    public ResolvedRagConfig resolve(Project project, @Nullable SearchRequestParams requestParams) {
        RagConfigDto projectConfig = parseProjectConfig(project);

        return ResolvedRagConfig.builder()
            // Embedding
            .embeddingProvider(firstNonNull(
                requestParams != null ? requestParams.embeddingProvider() : null,
                projectConfig != null && projectConfig.embedding() != null ?
                    projectConfig.embedding().provider() : null,
                globalProps.getEmbedding().provider()
            ))
            .embeddingModel(firstNonNull(
                requestParams != null ? requestParams.embeddingModel() : null,
                projectConfig != null && projectConfig.embedding() != null ?
                    projectConfig.embedding().model() : null,
                globalProps.getEmbedding().model()
            ))
            .embeddingDimensions(firstNonNullInt(
                requestParams != null ? requestParams.embeddingDimensions() : null,
                projectConfig != null && projectConfig.embedding() != null ?
                    projectConfig.embedding().dimensions() : null,
                globalProps.getEmbedding().dimensions()
            ))
            // PgVector
            .pgvectorEnabled(firstNonNullBool(
                requestParams != null ? requestParams.pgvectorEnabled() : null,
                projectConfig != null && projectConfig.pgvector() != null ?
                    projectConfig.pgvector().enabled() : null,
                globalProps.getPgvector().enabled()
            ))
            .similarityThreshold(firstNonNullDouble(
                requestParams != null ? requestParams.similarityThreshold() : null,
                projectConfig != null && projectConfig.pgvector() != null ?
                    projectConfig.pgvector().similarityThreshold() : null,
                globalProps.getPgvector().similarityThreshold()
            ))
            // Neo4j
            .neo4jEnabled(firstNonNullBool(
                requestParams != null ? requestParams.neo4jEnabled() : null,
                projectConfig != null && projectConfig.neo4j() != null ?
                    projectConfig.neo4j().enabled() : null,
                globalProps.getNeo4j().enabled()
            ))
            .maxHop(firstNonNullInt(
                requestParams != null ? requestParams.maxHop() : null,
                projectConfig != null && projectConfig.neo4j() != null ?
                    projectConfig.neo4j().maxHop() : null,
                globalProps.getNeo4j().maxHop()
            ))
            .entityExtractionModel(firstNonNull(
                requestParams != null ? requestParams.entityExtractionModel() : null,
                projectConfig != null && projectConfig.neo4j() != null ?
                    projectConfig.neo4j().entityExtractionModel() : null,
                globalProps.getNeo4j().entityExtractionModel()
            ))
            // Hybrid
            .fusionStrategy(firstNonNull(
                requestParams != null ? requestParams.fusionStrategy() : null,
                projectConfig != null && projectConfig.hybrid() != null ?
                    projectConfig.hybrid().fusionStrategy() : null,
                globalProps.getHybrid().fusionStrategy()
            ))
            .rrfK(firstNonNullInt(
                requestParams != null ? requestParams.rrfK() : null,
                projectConfig != null && projectConfig.hybrid() != null ?
                    projectConfig.hybrid().rrfK() : null,
                globalProps.getHybrid().rrfK()
            ))
            .vectorWeight(firstNonNullDouble(
                requestParams != null ? requestParams.vectorWeight() : null,
                projectConfig != null && projectConfig.hybrid() != null ?
                    projectConfig.hybrid().vectorWeight() : null,
                globalProps.getHybrid().vectorWeight()
            ))
            .graphWeight(firstNonNullDouble(
                requestParams != null ? requestParams.graphWeight() : null,
                projectConfig != null && projectConfig.hybrid() != null ?
                    projectConfig.hybrid().graphWeight() : null,
                globalProps.getHybrid().graphWeight()
            ))
            .build();
    }

    /**
     * 프로젝트 ID로 설정을 해결한다.
     */
    public ResolvedRagConfig resolve(UUID projectId, @Nullable SearchRequestParams requestParams) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        return resolve(project, requestParams);
    }

    /**
     * 요청 파라미터 없이 프로젝트 설정만으로 해결한다.
     */
    public ResolvedRagConfig resolve(Project project) {
        return resolve(project, null);
    }

    /**
     * 프로젝트 설정을 파싱한다.
     */
    @Nullable
    public RagConfigDto parseProjectConfig(Project project) {
        if (project.getRagConfig() == null || project.getRagConfig().isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(project.getRagConfig(), RagConfigDto.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse ragConfig for project {}: {}", project.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * 프로젝트에 RAG 설정을 저장한다.
     */
    public void saveProjectConfig(Project project, RagConfigDto config) {
        try {
            String json = objectMapper.writeValueAsString(config);
            project.setRagConfig(json);
            projectRepository.save(project);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize ragConfig", e);
        }
    }

    /**
     * RagConfigDto를 JSON 문자열로 변환한다.
     */
    public String toJson(RagConfigDto config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize ragConfig", e);
        }
    }

    /**
     * 전역 기본 설정을 RagConfigDto로 반환한다.
     */
    public RagConfigDto getGlobalDefaults() {
        return new RagConfigDto(
            RagConfigDto.CURRENT_VERSION,
            new RagConfigDto.EmbeddingConfig(
                globalProps.getEmbedding().provider(),
                globalProps.getEmbedding().model(),
                globalProps.getEmbedding().dimensions(),
                null  // No default credentialId
            ),
            new RagConfigDto.PgVectorConfig(
                globalProps.getPgvector().enabled(),
                globalProps.getPgvector().similarityThreshold()
            ),
            new RagConfigDto.Neo4jConfig(
                globalProps.getNeo4j().enabled(),
                globalProps.getNeo4j().maxHop(),
                globalProps.getNeo4j().entityExtractionModel(),
                null  // No default credentialId
            ),
            new RagConfigDto.HybridConfig(
                globalProps.getHybrid().fusionStrategy(),
                globalProps.getHybrid().rrfK(),
                globalProps.getHybrid().vectorWeight(),
                globalProps.getHybrid().graphWeight()
            )
        );
    }

    // Helper methods for null-safe first non-null value

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        throw new IllegalArgumentException("All values are null");
    }

    private static int firstNonNullInt(Integer... values) {
        for (Integer value : values) {
            if (value != null) {
                return value;
            }
        }
        throw new IllegalArgumentException("All integer values are null");
    }

    private static double firstNonNullDouble(Double... values) {
        for (Double value : values) {
            if (value != null) {
                return value;
            }
        }
        throw new IllegalArgumentException("All double values are null");
    }

    private static boolean firstNonNullBool(Boolean... values) {
        for (Boolean value : values) {
            if (value != null) {
                return value;
            }
        }
        throw new IllegalArgumentException("All boolean values are null");
    }

    /**
     * 검색 API 요청 파라미터.
     * 설정 우선순위 1순위로 적용됨.
     */
    public record SearchRequestParams(
        // Embedding
        @Nullable String embeddingProvider,
        @Nullable String embeddingModel,
        @Nullable Integer embeddingDimensions,
        // PgVector
        @Nullable Boolean pgvectorEnabled,
        @Nullable Double similarityThreshold,
        // Neo4j
        @Nullable Boolean neo4jEnabled,
        @Nullable Integer maxHop,
        @Nullable String entityExtractionModel,
        // Hybrid
        @Nullable String fusionStrategy,
        @Nullable Integer rrfK,
        @Nullable Double vectorWeight,
        @Nullable Double graphWeight
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String embeddingProvider;
            private String embeddingModel;
            private Integer embeddingDimensions;
            private Boolean pgvectorEnabled;
            private Double similarityThreshold;
            private Boolean neo4jEnabled;
            private Integer maxHop;
            private String entityExtractionModel;
            private String fusionStrategy;
            private Integer rrfK;
            private Double vectorWeight;
            private Double graphWeight;

            public Builder embeddingProvider(String value) { this.embeddingProvider = value; return this; }
            public Builder embeddingModel(String value) { this.embeddingModel = value; return this; }
            public Builder embeddingDimensions(Integer value) { this.embeddingDimensions = value; return this; }
            public Builder pgvectorEnabled(Boolean value) { this.pgvectorEnabled = value; return this; }
            public Builder similarityThreshold(Double value) { this.similarityThreshold = value; return this; }
            public Builder neo4jEnabled(Boolean value) { this.neo4jEnabled = value; return this; }
            public Builder maxHop(Integer value) { this.maxHop = value; return this; }
            public Builder entityExtractionModel(String value) { this.entityExtractionModel = value; return this; }
            public Builder fusionStrategy(String value) { this.fusionStrategy = value; return this; }
            public Builder rrfK(Integer value) { this.rrfK = value; return this; }
            public Builder vectorWeight(Double value) { this.vectorWeight = value; return this; }
            public Builder graphWeight(Double value) { this.graphWeight = value; return this; }

            public SearchRequestParams build() {
                return new SearchRequestParams(
                    embeddingProvider, embeddingModel, embeddingDimensions,
                    pgvectorEnabled, similarityThreshold,
                    neo4jEnabled, maxHop, entityExtractionModel,
                    fusionStrategy, rrfK, vectorWeight, graphWeight
                );
            }
        }
    }
}
