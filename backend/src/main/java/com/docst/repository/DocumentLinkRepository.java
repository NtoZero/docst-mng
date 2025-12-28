package com.docst.repository;

import com.docst.domain.DocumentLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 문서 링크 레포지토리.
 * 문서 간 링크 관계에 대한 데이터 접근을 제공한다.
 */
@Repository
public interface DocumentLinkRepository extends JpaRepository<DocumentLink, UUID> {

    /**
     * 특정 문서에서 나가는 링크 목록을 조회한다.
     *
     * @param sourceDocumentId 시작 문서 ID
     * @return 나가는 링크 목록
     */
    List<DocumentLink> findBySourceDocumentId(UUID sourceDocumentId);

    /**
     * 특정 문서로 들어오는 링크 목록을 조회한다 (역참조).
     *
     * @param targetDocumentId 목적지 문서 ID
     * @return 들어오는 링크 목록
     */
    List<DocumentLink> findByTargetDocumentId(UUID targetDocumentId);

    /**
     * 특정 문서의 모든 링크를 삭제한다.
     *
     * @param sourceDocumentId 시작 문서 ID
     */
    @Modifying
    @Query("DELETE FROM DocumentLink dl WHERE dl.sourceDocument.id = :sourceDocumentId")
    void deleteBySourceDocumentId(@Param("sourceDocumentId") UUID sourceDocumentId);

    /**
     * 특정 문서에서 깨진 링크 목록을 조회한다.
     *
     * @param sourceDocumentId 시작 문서 ID
     * @return 깨진 링크 목록
     */
    @Query("SELECT dl FROM DocumentLink dl WHERE dl.sourceDocument.id = :sourceDocumentId AND dl.broken = true")
    List<DocumentLink> findBrokenLinksBySourceDocumentId(@Param("sourceDocumentId") UUID sourceDocumentId);

    /**
     * 레포지토리 내 모든 깨진 링크를 조회한다.
     *
     * @param repositoryId 레포지토리 ID
     * @return 깨진 링크 목록
     */
    @Query("SELECT dl FROM DocumentLink dl " +
            "WHERE dl.sourceDocument.repository.id = :repositoryId AND dl.broken = true")
    List<DocumentLink> findBrokenLinksByRepositoryId(@Param("repositoryId") UUID repositoryId);

    /**
     * 프로젝트 내 모든 깨진 링크를 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @return 깨진 링크 목록
     */
    @Query("SELECT dl FROM DocumentLink dl " +
            "WHERE dl.sourceDocument.repository.project.id = :projectId AND dl.broken = true")
    List<DocumentLink> findBrokenLinksByProjectId(@Param("projectId") UUID projectId);

    /**
     * 특정 문서의 나가는 링크 개수를 조회한다.
     *
     * @param sourceDocumentId 시작 문서 ID
     * @return 나가는 링크 개수
     */
    @Query("SELECT COUNT(dl) FROM DocumentLink dl WHERE dl.sourceDocument.id = :sourceDocumentId")
    long countBySourceDocumentId(@Param("sourceDocumentId") UUID sourceDocumentId);

    /**
     * 특정 문서로 들어오는 링크 개수를 조회한다 (역참조 개수).
     *
     * @param targetDocumentId 목적지 문서 ID
     * @return 들어오는 링크 개수
     */
    @Query("SELECT COUNT(dl) FROM DocumentLink dl WHERE dl.targetDocument.id = :targetDocumentId")
    long countByTargetDocumentId(@Param("targetDocumentId") UUID targetDocumentId);

    /**
     * 레포지토리 내 모든 내부 링크를 조회한다 (그래프 생성용).
     *
     * @param repositoryId 레포지토리 ID
     * @return 내부 링크 목록
     */
    @Query("SELECT dl FROM DocumentLink dl " +
            "WHERE dl.sourceDocument.repository.id = :repositoryId " +
            "AND (dl.linkType = 'INTERNAL' OR dl.linkType = 'WIKI') " +
            "AND dl.broken = false")
    List<DocumentLink> findInternalLinksByRepositoryId(@Param("repositoryId") UUID repositoryId);

    /**
     * 프로젝트 내 모든 내부 링크를 조회한다 (그래프 생성용).
     *
     * @param projectId 프로젝트 ID
     * @return 내부 링크 목록
     */
    @Query("SELECT dl FROM DocumentLink dl " +
            "WHERE dl.sourceDocument.repository.project.id = :projectId " +
            "AND (dl.linkType = 'INTERNAL' OR dl.linkType = 'WIKI') " +
            "AND dl.broken = false")
    List<DocumentLink> findInternalLinksByProjectId(@Param("projectId") UUID projectId);
}
