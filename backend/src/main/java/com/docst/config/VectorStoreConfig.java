package com.docst.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * VectorStore 설정.
 * Phase 4-E: OpenAI/Ollama 자동 설정을 비활성화하고 수동으로 VectorStore를 구성한다.
 */
@Configuration
public class VectorStoreConfig {

    @Value("${spring.ai.vectorstore.pgvector.dimensions:1536}")
    private int dimensions;

    @Value("${spring.ai.vectorstore.pgvector.distance-type:COSINE_DISTANCE}")
    private PgVectorStore.PgDistanceType distanceType;

    @Value("${spring.ai.vectorstore.pgvector.index-type:HNSW}")
    private PgVectorStore.PgIndexType indexType;

    @Value("${spring.ai.vectorstore.pgvector.remove-existing-vector-store-table:false}")
    private boolean removeExistingVectorStoreTable;

    @Value("${spring.ai.vectorstore.pgvector.initialize-schema:true}")
    private boolean initializeSchema;

    @Value("${spring.ai.vectorstore.pgvector.schema-name:public}")
    private String schemaName;

    @Value("${spring.ai.vectorstore.pgvector.table-name:vector_store}")
    private String tableName;

    /**
     * Placeholder EmbeddingModel.
     * 실제 임베딩은 DynamicEmbeddingClientFactory를 통해 프로젝트별로 수행된다.
     * VectorStore 생성을 위한 placeholder일 뿐, 실제로는 사용되지 않는다.
     */
    @Bean
    public EmbeddingModel placeholderEmbeddingModel() {
        return new EmbeddingModel() {
            @Override
            public float[] embed(String text) {
                throw new UnsupportedOperationException(
                    "Placeholder EmbeddingModel should not be used directly. " +
                    "Use DynamicEmbeddingClientFactory to create project-specific embedding models."
                );
            }

            @Override
            public float[] embed(org.springframework.ai.document.Document document) {
                throw new UnsupportedOperationException(
                    "Placeholder EmbeddingModel should not be used directly. " +
                    "Use DynamicEmbeddingClientFactory to create project-specific embedding models."
                );
            }

            @Override
            public org.springframework.ai.embedding.EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request) {
                throw new UnsupportedOperationException(
                    "Placeholder EmbeddingModel should not be used directly. " +
                    "Use DynamicEmbeddingClientFactory to create project-specific embedding models."
                );
            }
        };
    }

    /**
     * PgVector VectorStore 빈 생성.
     * Phase 4-E에서는 임베딩을 미리 생성하여 전달하므로, placeholder EmbeddingModel을 사용한다.
     */
    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel placeholderEmbeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, placeholderEmbeddingModel)
            .dimensions(dimensions)
            .distanceType(distanceType)
            .indexType(indexType)
            .removeExistingVectorStoreTable(removeExistingVectorStoreTable)
            .initializeSchema(initializeSchema)
            .schemaName(schemaName)
            .vectorTableName(tableName)
            .build();
    }
}
