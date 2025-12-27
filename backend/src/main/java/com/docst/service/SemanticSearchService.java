package com.docst.service;

import com.docst.domain.DocChunk;
import com.docst.domain.Document;
import com.docst.repository.DocChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 의미 검색 서비스 (Phase 2-C).
 * Spring AI VectorStore를 활용한 벡터 유사도 검색을 제공한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class SemanticSearchService {

    private final VectorStore vectorStore;  // Spring AI 자동 주입
    private final DocChunkRepository docChunkRepository;

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
        // Spring AI SearchRequest 구성
        SearchRequest request = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .similarityThreshold(similarityThreshold)
            // TODO: Filter Expression 추가 (project_id 필터링)
            // Spring AI M5 버전에서는 Filter 지원이 제한적일 수 있음
            // .filterExpression(Filter.builder()
            //     .key("project_id")
            //     .value(projectId.toString())
            //     .build())
            .build();

        // 벡터 검색 실행
        List<org.springframework.ai.document.Document> aiDocuments = vectorStore.similaritySearch(request);

        log.debug("Semantic search: query='{}', topK={}, results={}", query, topK, aiDocuments.size());

        // Spring AI Document → DocChunk ID 추출
        List<UUID> chunkIds = aiDocuments.stream()
            .map(doc -> {
                String chunkIdStr = (String) doc.getMetadata().get("doc_chunk_id");
                return UUID.fromString(chunkIdStr);
            })
            .toList();

        if (chunkIds.isEmpty()) {
            return List.of();
        }

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
}
