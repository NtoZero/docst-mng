package com.docst.repository;

import com.docst.domain.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    List<DocumentVersion> findByDocumentIdOrderByCommittedAtDesc(UUID documentId);

    Optional<DocumentVersion> findByDocumentIdAndCommitSha(UUID documentId, String commitSha);

    @Query("SELECT dv FROM DocumentVersion dv WHERE dv.document.id = :docId " +
           "AND dv.commitSha = (SELECT d.latestCommitSha FROM Document d WHERE d.id = :docId)")
    Optional<DocumentVersion> findLatestByDocumentId(@Param("docId") UUID documentId);

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

    boolean existsByDocumentIdAndContentHash(UUID documentId, String contentHash);
}
