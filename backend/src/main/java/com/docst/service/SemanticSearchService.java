package com.docst.service;

import com.docst.domain.DocChunk;
import com.docst.domain.Document;
import com.docst.embedding.DynamicEmbeddingClientFactory;
import com.docst.rag.config.RagConfigService;
import com.docst.rag.config.ResolvedRagConfig;
import com.docst.repository.DocChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 의미 검색 서비스 (Phase 2-C).
 * Spring AI VectorStore를 활용한 벡터 유사도 검색을 제공한다.
 *
 * Phase 6 리팩토링:
 * - 프로젝트별 Credential 기반 동적 EmbeddingModel 지원
 * - VectorStore를 프로젝트별로 동적 생성 (Spring AI 1.1.0+ 표준)
 * - 캐싱을 통한 성능 최적화
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class SemanticSearchService {

    private final PgVectorDataSourceManager dataSourceManager;
    private final DynamicEmbeddingClientFactory embeddingClientFactory;
    private final RagConfigService ragConfigService;
    private final DocChunkRepository docChunkRepository;
    private final SystemConfigService systemConfigService;

    // 프로젝트별 VectorStore 캐시 (projectId -> VectorStore)
    private final ConcurrentHashMap<UUID, VectorStore> vectorStoreCache = new ConcurrentHashMap<>();

    /**
     * 프로젝트 범위 내에서 의미 검색을 수행한다.
     *
     * @param projectId 프로젝트 ID
     * @param query 검색 쿼리
     * @param topK 상위 K개 결과
     * @return 검색 결과 목록
     */
    public List<SearchService.SearchResult> searchSemantic(UUID projectId, String query, int topK) {
        return searchSemantic(projectId, query, topK, 0.5);
    }

    /**
     * 프로젝트 범위 내에서 의미 검색을 수행한다 (유사도 임계값 지정).
     *
     * @param projectId 프로젝트 ID
     * @param query 검색 쿼리
     * @param topK 상위 K개 결과
     * @param similarityThreshold 유사도 임계값 (0.0 ~ 1.0)
     * @return 검색 결과 목록
     */
    public List<SearchService.SearchResult> searchSemantic(UUID projectId, String query, int topK, double similarityThreshold) {
        log.info("Semantic search started: projectId={}, query='{}', topK={}, threshold={}",
            projectId, query, topK, similarityThreshold);

        // 프로젝트별 VectorStore 가져오기 (동적 생성)
        VectorStore vectorStore;
        try {
            vectorStore = getOrCreateVectorStore(projectId);
        } catch (Exception e) {
            log.error("Failed to create VectorStore for project {}: {}", projectId, e.getMessage());
            return List.of();
        }

        // Spring AI SearchRequest 구성
        SearchRequest request = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .similarityThreshold(similarityThreshold)
            // TODO: Filter Expression 추가 (project_id 필터링)
            // Spring AI 1.1.0에서는 Filter 지원이 제한적일 수 있음
            // .filterExpression("project_id == '" + projectId + "'")
            .build();

        // 벡터 검색 실행
        List<org.springframework.ai.document.Document> aiDocuments;
        try {
            aiDocuments = vectorStore.similaritySearch(request);
            log.info("Vector store search completed: found {} documents", aiDocuments.size());
        } catch (Exception e) {
            log.error("Vector store search failed", e);
            return List.of();
        }

        if (aiDocuments.isEmpty()) {
            log.warn("No results from vector store for query: '{}'", query);
            return List.of();
        }

        log.debug("Semantic search: query='{}', topK={}, results={}", query, topK, aiDocuments.size());

        // Spring AI Document → DocChunk ID 추출
        List<UUID> chunkIds = new ArrayList<>();
        for (org.springframework.ai.document.Document doc : aiDocuments) {
            try {
                String chunkIdStr = (String) doc.getMetadata().get("doc_chunk_id");
                if (chunkIdStr == null) {
                    log.warn("Document missing doc_chunk_id in metadata: {}", doc.getId());
                    continue;
                }
                chunkIds.add(UUID.fromString(chunkIdStr));
            } catch (Exception e) {
                log.error("Failed to extract chunk ID from document: {}", doc.getId(), e);
            }
        }

        if (chunkIds.isEmpty()) {
            log.warn("No valid chunk IDs extracted from {} documents", aiDocuments.size());
            return List.of();
        }

        log.info("Extracted {} chunk IDs from vector search results", chunkIds.size());

        // DocChunk 조회 (순서 보존 필요)
        List<DocChunk> chunks = docChunkRepository.findAllById(chunkIds);
        Map<UUID, DocChunk> chunkMap = new HashMap<>();
        chunks.forEach(chunk -> chunkMap.put(chunk.getId(), chunk));

        // SearchResult로 변환 (검색 순서 유지)
        List<SearchService.SearchResult> results = new ArrayList<>();
        for (int i = 0; i < aiDocuments.size(); i++) {
            org.springframework.ai.document.Document aiDoc = aiDocuments.get(i);
            String chunkIdStr = (String) aiDoc.getMetadata().get("doc_chunk_id");
            UUID chunkId = UUID.fromString(chunkIdStr);

            DocChunk chunk = chunkMap.get(chunkId);
            if (chunk == null) {
                continue;
            }

            // 프로젝트 필터링 (TODO: Filter Expression으로 대체)
            Document doc = chunk.getDocumentVersion().getDocument();
            UUID docProjectId = doc.getRepository().getProject().getId();
            if (!docProjectId.equals(projectId)) {
                continue;
            }

            // 유사도 점수 추출 (distance → similarity)
            // pgvector cosine distance: 0 (동일) ~ 2 (완전 반대)
            // similarity score: 1.0 (동일) ~ 0.0 (완전 반대)
            Double distance = aiDoc.getMetadata().get("distance") != null
                ? ((Number) aiDoc.getMetadata().get("distance")).doubleValue()
                : null;
            double score = distance != null ? (1.0 - distance / 2.0) : 0.5;

            // 스니펫 생성 (청크 내용 일부)
            String content = chunk.getContent();
            String snippet = content.length() > 300
                ? content.substring(0, 300) + "..."
                : content;

            results.add(new SearchService.SearchResult(
                doc.getId(),
                doc.getRepository().getId(),
                doc.getPath(),
                chunk.getDocumentVersion().getCommitSha(),
                chunk.getId(),
                chunk.getHeadingPath(),
                score,
                snippet,
                snippet  // 의미 검색에서는 하이라이트 없음
            ));
        }

        return results;
    }

    /**
     * 프로젝트별 VectorStore를 가져오거나 새로 생성한다.
     * Spring AI 1.1.0+ 표준: PgVectorStore.builder()로 동적 생성.
     *
     * @param projectId 프로젝트 ID
     * @return 프로젝트별 VectorStore
     */
    private VectorStore getOrCreateVectorStore(UUID projectId) {
        return vectorStoreCache.computeIfAbsent(projectId, this::createVectorStore);
    }

    /**
     * 프로젝트별 VectorStore를 생성한다.
     *
     * @param projectId 프로젝트 ID
     * @return 새로 생성된 VectorStore
     */
    private VectorStore createVectorStore(UUID projectId) {
        log.info("Creating VectorStore for project: {}", projectId);

        // 동적 JdbcTemplate 획득
        JdbcTemplate jdbcTemplate = dataSourceManager.getOrCreateJdbcTemplate();
        if (jdbcTemplate == null) {
            throw new IllegalStateException("PgVector is not configured or disabled");
        }

        // 프로젝트별 RAG 설정 가져오기
        ResolvedRagConfig config = ragConfigService.resolve(projectId, null);

        // 프로젝트별 EmbeddingModel 생성 (Credential 기반)
        EmbeddingModel embeddingModel = embeddingClientFactory.createEmbeddingModel(projectId, config);

        // SystemConfigService에서 설정 읽기
        String schemaName = systemConfigService.getString(SystemConfigService.PGVECTOR_SCHEMA, "public");
        String tableName = systemConfigService.getString(SystemConfigService.PGVECTOR_TABLE, "vector_store");

        // Spring AI 1.1.0+ 표준: PgVectorStore.builder() 사용
        VectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, embeddingModel)
            .dimensions(config.getEmbeddingDimensions())
            .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
            .indexType(PgVectorStore.PgIndexType.HNSW)
            .schemaName(schemaName)
            .vectorTableName(tableName)
            .initializeSchema(false)  // 스키마는 이미 생성됨
            .build();

        log.info("VectorStore created for project {}: provider={}, model={}, dimensions={}",
            projectId, config.getEmbeddingProvider(), config.getEmbeddingModel(), config.getEmbeddingDimensions());

        return vectorStore;
    }

    /**
     * 프로젝트의 VectorStore 캐시를 무효화한다.
     * Credential 변경 시 호출하여 새로운 EmbeddingModel로 재생성하도록 함.
     *
     * @param projectId 프로젝트 ID
     */
    public void invalidateCache(UUID projectId) {
        VectorStore removed = vectorStoreCache.remove(projectId);
        if (removed != null) {
            log.info("VectorStore cache invalidated for project: {}", projectId);
        }
    }

    /**
     * 모든 VectorStore 캐시를 무효화한다.
     * PgVector 연결 변경 시 호출하여 모든 프로젝트의 VectorStore 재생성.
     */
    public void invalidateAllVectorStores() {
        int size = vectorStoreCache.size();
        vectorStoreCache.clear();
        log.info("All VectorStore caches invalidated due to PgVector connection change: {} entries removed", size);
    }
}
