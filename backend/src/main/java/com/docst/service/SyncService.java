package com.docst.service;

import com.docst.domain.Repository;
import com.docst.domain.SyncJob;
import com.docst.domain.SyncJob.SyncStatus;
import com.docst.repository.RepositoryRepository;
import com.docst.repository.SyncJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final SyncJobRepository syncJobRepository;
    private final RepositoryRepository repositoryRepository;
    private final GitSyncService gitSyncService;

    public SyncService(SyncJobRepository syncJobRepository,
                       RepositoryRepository repositoryRepository,
                       GitSyncService gitSyncService) {
        this.syncJobRepository = syncJobRepository;
        this.repositoryRepository = repositoryRepository;
        this.gitSyncService = gitSyncService;
    }

    public Optional<SyncJob> findById(UUID jobId) {
        return syncJobRepository.findById(jobId);
    }

    public Optional<SyncJob> findLatestByRepositoryId(UUID repositoryId) {
        return syncJobRepository.findFirstByRepositoryIdOrderByCreatedAtDesc(repositoryId);
    }

    @Transactional
    public SyncJob startSync(UUID repositoryId, String branch) {
        Repository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        // Check if there's already a running sync
        if (syncJobRepository.existsByRepositoryIdAndStatus(repositoryId, SyncStatus.RUNNING)) {
            throw new IllegalStateException("Sync already in progress for repository: " + repositoryId);
        }

        String targetBranch = branch != null ? branch : repo.getDefaultBranch();
        SyncJob job = new SyncJob(repo, targetBranch);
        job = syncJobRepository.save(job);

        // Start async sync
        final SyncJob savedJob = job;
        CompletableFuture.runAsync(() -> executeSync(savedJob.getId()));

        return job;
    }

    private void executeSync(UUID jobId) {
        SyncJob job = syncJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.error("Sync job not found: {}", jobId);
            return;
        }

        try {
            job.start();
            syncJobRepository.save(job);

            Repository repo = job.getRepository();
            log.info("Starting sync for repository: {} branch: {}", repo.getFullName(), job.getTargetBranch());

            // Execute git sync
            String lastCommit = gitSyncService.syncRepository(repo.getId(), job.getTargetBranch());

            job.complete(lastCommit);
            syncJobRepository.save(job);
            log.info("Sync completed for repository: {} commit: {}", repo.getFullName(), lastCommit);

        } catch (Exception e) {
            log.error("Sync failed for job: {}", jobId, e);
            job.fail(e.getMessage());
            syncJobRepository.save(job);
        }
    }
}
