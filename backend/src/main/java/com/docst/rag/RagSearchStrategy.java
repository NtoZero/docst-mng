package com.docst.rag;

import com.docst.domain.DocumentVersion;
import com.docst.service.SearchService.SearchResult;

import java.util.List;
import java.util.UUID;

/**
 * RAG 검색 전략 인터페이스.
 * Phase 4: 3가지 검색 모드 (PgVector, Neo4j, Hybrid)를 위한 전략 패턴
 */
public interface RagSearchStrategy {

    /**
     * 검색 실행.
     *
     * @param projectId 프로젝트 ID
     * @param query 검색 쿼리
     * @param topK 상위 K개 결과
     * @return 검색 결과 목록
     */
    List<SearchResult> search(UUID projectId, String query, int topK);

    /**
     * 문서 인덱싱.
     * 문서 버전을 해당 RAG 모드에 맞게 인덱싱한다.
     *
     * @param documentVersion 인덱싱할 문서 버전
     */
    void indexDocument(DocumentVersion documentVersion);

    /**
     * 지원하는 RAG 모드 반환.
     *
     * @return 이 전략이 지원하는 RAG 모드
     */
    RagMode getSupportedMode();
}
