package com.docst.repository;

import com.docst.domain.Document;
import com.docst.domain.Document.DocType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 문서 레포지토리.
 * 문서 엔티티에 대한 데이터 접근을 제공한다.
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /**
     * 레포지토리의 삭제되지 않은 문서 목록을 경로순으로 조회한다.
     *
     * @param repositoryId 레포지토리 ID
     * @return 문서 목록
     */
    List<Document> findByRepositoryIdAndDeletedFalseOrderByPath(UUID repositoryId);

    /**
     * 레포지토리의 문서를 필터링하여 조회한다.
     *
     * @param repositoryId 레포지토리 ID
     * @param pathPrefix 경로 접두사 (null이면 전체)
     * @param docType 문서 타입 (null이면 전체)
     * @return 문서 목록 (경로순)
     */
    @Query("SELECT d FROM Document d WHERE d.repository.id = :repoId AND d.deleted = false " +
           "AND (:pathPrefix IS NULL OR d.path LIKE CONCAT(:pathPrefix, '%')) " +
           "AND (:docType IS NULL OR d.docType = :docType) " +
           "ORDER BY d.path")
    List<Document> findByRepositoryIdWithFilters(
            @Param("repoId") UUID repositoryId,
            @Param("pathPrefix") String pathPrefix,
            @Param("docType") DocType docType);

    /**
     * 레포지토리와 경로로 문서를 조회한다.
     *
     * @param repositoryId 레포지토리 ID
     * @param path 파일 경로
     * @return 문서 (존재하지 않으면 empty)
     */
    Optional<Document> findByRepositoryIdAndPath(UUID repositoryId, String path);

    /**
     * 프로젝트에 속한 모든 문서를 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @return 문서 목록 (경로순)
     */
    @Query("SELECT d FROM Document d JOIN d.repository r WHERE r.project.id = :projectId AND d.deleted = false ORDER BY d.path")
    List<Document> findByProjectId(@Param("projectId") UUID projectId);

    /**
     * 특정 경로의 문서가 레포지토리에 존재하는지 확인한다.
     *
     * @param repositoryId 레포지토리 ID
     * @param path 파일 경로
     * @return 존재 여부
     */
    boolean existsByRepositoryIdAndPath(UUID repositoryId, String path);
}
