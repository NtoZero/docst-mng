package com.docst.repository;

import com.docst.domain.SyncJob;
import com.docst.domain.SyncJob.SyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SyncJobRepository extends JpaRepository<SyncJob, UUID> {

    List<SyncJob> findByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId);

    Optional<SyncJob> findFirstByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId);

    Optional<SyncJob> findFirstByRepositoryIdAndStatusOrderByCreatedAtDesc(
            UUID repositoryId, SyncStatus status);

    List<SyncJob> findByStatus(SyncStatus status);

    boolean existsByRepositoryIdAndStatus(UUID repositoryId, SyncStatus status);
}
