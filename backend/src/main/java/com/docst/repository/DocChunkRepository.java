package com.docst.repository;

import com.docst.domain.DocChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 문서 청크 레포지토리.
 * 문서 청크 엔티티에 대한 데이터 접근을 제공한다.
 */
@Repository
public interface DocChunkRepository extends JpaRepository<DocChunk, UUID> {

    /**
     * 문서 버전의 모든 청크를 조회한다 (인덱스 순서).
     *
     * @param documentVersionId 문서 버전 ID
     * @return 청크 목록 (인덱스 순)
     */
    List<DocChunk> findByDocumentVersionIdOrderByChunkIndex(UUID documentVersionId);

    /**
     * 문서 버전의 모든 청크를 조회한다.
     *
     * @param documentVersionId 문서 버전 ID
     * @return 청크 목록
     */
    List<DocChunk> findByDocumentVersionId(UUID documentVersionId);

    /**
     * 문서 버전의 청크를 삭제한다.
     *
     * @param documentVersionId 문서 버전 ID
     */
    void deleteByDocumentVersionId(UUID documentVersionId);

    /**
     * 문서 버전의 청크 개수를 조회한다.
     *
     * @param documentVersionId 문서 버전 ID
     * @return 청크 개수
     */
    long countByDocumentVersionId(UUID documentVersionId);
}
