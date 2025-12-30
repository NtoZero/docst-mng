package com.docst.repository;

import com.docst.domain.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 문서 버전 레포지토리.
 * 문서 버전 엔티티에 대한 데이터 접근을 제공한다.
 */
@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    /**
     * 문서의 모든 버전을 최신순으로 조회한다.
     *
     * @param documentId 문서 ID
     * @return 버전 목록 (최신순)
     */
    List<DocumentVersion> findByDocumentIdOrderByCommittedAtDesc(UUID documentId);

    /**
     * 문서의 특정 커밋 버전을 조회한다.
     *
     * @param documentId 문서 ID
     * @param commitSha 커밋 SHA
     * @return 버전 (존재하지 않으면 empty)
     */
    Optional<DocumentVersion> findByDocumentIdAndCommitSha(UUID documentId, String commitSha);

    /**
     * 문서의 최신 버전을 조회한다.
     *
     * @param documentId 문서 ID
     * @return 최신 버전 (존재하지 않으면 empty)
     */
    @Query("SELECT dv FROM DocumentVersion dv WHERE dv.document.id = :docId " +
           "AND dv.commitSha = (SELECT d.latestCommitSha FROM Document d WHERE d.id = :docId)")
    Optional<DocumentVersion> findLatestByDocumentId(@Param("docId") UUID documentId);

    /**
     * 프로젝트 내에서 키워드로 문서를 검색한다.
     *
     * @param projectId 프로젝트 ID
     * @param query 검색어
     * @param limit 결과 개수 제한
     * @return 검색 결과 목록
     */
    @Query(value = """
        SELECT dv.* FROM dm_document_version dv
        JOIN dm_document d ON d.id = dv.document_id
        JOIN dm_repository r ON r.id = d.repository_id
        WHERE r.project_id = :projectId
          AND dv.content ILIKE '%' || :query || '%'
        ORDER BY dv.committed_at DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<DocumentVersion> searchByKeyword(
            @Param("projectId") UUID projectId,
            @Param("query") String query,
            @Param("limit") int limit);

    /**
     * 동일한 내용 해시를 가진 버전이 문서에 존재하는지 확인한다.
     *
     * @param documentId 문서 ID
     * @param contentHash 내용 해시
     * @return 존재 여부
     */
    boolean existsByDocumentIdAndContentHash(UUID documentId, String contentHash);

    /**
     * 프로젝트의 모든 문서 버전을 조회한다 (Phase 4-D-5).
     *
     * @param projectId 프로젝트 ID
     * @return 문서 버전 목록
     */
    @Query("""
        SELECT dv FROM DocumentVersion dv
        JOIN dv.document d
        JOIN d.repository r
        WHERE r.project.id = :projectId
        ORDER BY dv.committedAt DESC
        """)
    List<DocumentVersion> findByProjectId(@Param("projectId") UUID projectId);
}
