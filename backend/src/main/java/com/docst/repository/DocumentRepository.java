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

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByRepositoryIdAndDeletedFalseOrderByPath(UUID repositoryId);

    @Query("SELECT d FROM Document d WHERE d.repository.id = :repoId AND d.deleted = false " +
           "AND (:pathPrefix IS NULL OR d.path LIKE CONCAT(:pathPrefix, '%')) " +
           "AND (:docType IS NULL OR d.docType = :docType) " +
           "ORDER BY d.path")
    List<Document> findByRepositoryIdWithFilters(
            @Param("repoId") UUID repositoryId,
            @Param("pathPrefix") String pathPrefix,
            @Param("docType") DocType docType);

    Optional<Document> findByRepositoryIdAndPath(UUID repositoryId, String path);

    @Query("SELECT d FROM Document d JOIN d.repository r WHERE r.project.id = :projectId AND d.deleted = false ORDER BY d.path")
    List<Document> findByProjectId(@Param("projectId") UUID projectId);

    boolean existsByRepositoryIdAndPath(UUID repositoryId, String path);
}
