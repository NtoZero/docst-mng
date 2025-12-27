package com.docst.embedding;

import com.docst.domain.DocChunk;
import com.docst.domain.DocumentVersion;
import com.docst.repository.DocChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Docst 임베딩 서비스.
 * DocChunk와 Spring AI VectorStore 간의 변환 및 저장/검색을 담당한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocstEmbeddingService {

    private final VectorStore vectorStore;  // Spring AI가 자동 주입
    private final DocChunkRepository docChunkRepository;

    /**
     * DocumentVersion의 모든 청크를 임베딩하여 VectorStore에 저장한다.
     *
     * @param documentVersion 문서 버전
     * @return 임베딩된 청크 수
     */
    @Transactional
    public int embedDocumentVersion(DocumentVersion documentVersion) {
        if (documentVersion == null) {
            throw new IllegalArgumentException("DocumentVersion cannot be null");
        }

        // DocumentVersion의 모든 청크 조회
        List<DocChunk> chunks = docChunkRepository.findByDocumentVersionIdOrderByChunkIndex(
            documentVersion.getId()
        );

        if (chunks.isEmpty()) {
            log.debug("No chunks found for DocumentVersion {}", documentVersion.getId());
            return 0;
        }

        return embedChunks(chunks);
    }

    /**
     * DocChunk 목록을 임베딩하여 VectorStore에 저장한다.
     *
     * @param chunks 청크 목록
     * @return 임베딩된 청크 수
     */
    @Transactional
    public int embedChunks(List<DocChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return 0;
        }

        // DocChunk를 Spring AI Document로 변환
        List<Document> documents = chunks.stream()
            .map(this::convertToDocument)
            .toList();

        // Spring AI VectorStore에 자동 임베딩 및 저장
        vectorStore.add(documents);

        log.info("Embedded and stored {} chunks in VectorStore", documents.size());
        return documents.size();
    }

    /**
     * DocChunk를 Spring AI Document로 변환한다.
     *
     * @param chunk DocChunk 엔티티
     * @return Spring AI Document
     */
    private Document convertToDocument(DocChunk chunk) {
        DocumentVersion docVersion = chunk.getDocumentVersion();
        com.docst.domain.Document doc = docVersion.getDocument();
        UUID projectId = doc.getRepository().getProject().getId();
        UUID repositoryId = doc.getRepository().getId();

        // 메타데이터 구성
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("doc_chunk_id", chunk.getId().toString());
        metadata.put("chunk_index", chunk.getChunkIndex());
        metadata.put("heading_path", chunk.getHeadingPath() != null ? chunk.getHeadingPath() : "");
        metadata.put("token_count", chunk.getTokenCount());
        metadata.put("document_version_id", docVersion.getId().toString());
        metadata.put("document_id", doc.getId().toString());
        metadata.put("document_path", doc.getPath());
        metadata.put("document_title", doc.getTitle());
        metadata.put("repository_id", repositoryId.toString());
        metadata.put("project_id", projectId.toString());
        metadata.put("commit_sha", docVersion.getCommitSha());

        // Spring AI Document 생성
        // ID를 doc_chunk_id로 설정하여 중복 방지
        return new Document(
            chunk.getId().toString(),  // ID
            chunk.getContent(),         // 임베딩할 텍스트
            metadata                    // 메타데이터
        );
    }

    /**
     * 의미 검색을 수행한다.
     *
     * @param query 검색 쿼리
     * @param topK 상위 K개 결과
     * @return 검색된 Spring AI Document 목록
     */
    public List<Document> semanticSearch(String query, int topK) {
        return semanticSearch(query, topK, 0.7);
    }

    /**
     * 의미 검색을 수행한다 (유사도 임계값 지정).
     *
     * @param query 검색 쿼리
     * @param topK 상위 K개 결과
     * @param similarityThreshold 유사도 임계값 (0.0 ~ 1.0)
     * @return 검색된 Spring AI Document 목록
     */
    public List<Document> semanticSearch(String query, int topK, double similarityThreshold) {
        SearchRequest request = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .similarityThreshold(similarityThreshold)
            .build();

        return vectorStore.similaritySearch(request);
    }

    /**
     * 프로젝트 범위 내에서 의미 검색을 수행한다.
     *
     * @param projectId 프로젝트 ID
     * @param query 검색 쿼리
     * @param topK 상위 K개 결과
     * @return 검색된 DocChunk 목록
     */
    public List<DocChunk> semanticSearchInProject(UUID projectId, String query, int topK) {
        SearchRequest request = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .similarityThreshold(0.7)
            // TODO: Spring AI Filter Expression 추가 (project_id 필터링)
            // .filterExpression(Filter.builder()
            //     .key("project_id")
            //     .value(projectId.toString())
            //     .build())
            .build();

        List<Document> results = vectorStore.similaritySearch(request);

        // Spring AI Document → DocChunk ID 추출
        List<UUID> chunkIds = results.stream()
            .map(doc -> UUID.fromString(doc.getMetadata().get("doc_chunk_id").toString()))
            .toList();

        // DocChunk 조회 (순서 보존)
        List<DocChunk> chunks = docChunkRepository.findAllById(chunkIds);

        // 검색 결과 순서대로 재정렬
        Map<UUID, DocChunk> chunkMap = new HashMap<>();
        chunks.forEach(chunk -> chunkMap.put(chunk.getId(), chunk));

        return chunkIds.stream()
            .map(chunkMap::get)
            .filter(chunk -> chunk != null)
            .toList();
    }

    /**
     * DocumentVersion의 임베딩을 VectorStore에서 삭제한다.
     *
     * @param documentVersionId 문서 버전 ID
     * @return 삭제된 청크 수
     */
    @Transactional
    public int deleteEmbeddings(UUID documentVersionId) {
        // DocChunk 조회
        List<DocChunk> chunks = docChunkRepository.findByDocumentVersionId(documentVersionId);

        if (chunks.isEmpty()) {
            return 0;
        }

        // Spring AI Document ID 목록 생성
        List<String> documentIds = chunks.stream()
            .map(chunk -> chunk.getId().toString())
            .toList();

        // VectorStore에서 삭제
        vectorStore.delete(documentIds);

        log.info("Deleted {} embeddings from VectorStore for DocumentVersion {}",
            documentIds.size(), documentVersionId);

        return documentIds.size();
    }
}
