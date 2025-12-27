package com.docst.service;

import com.docst.domain.Repository;
import com.docst.domain.SyncJob;
import com.docst.domain.SyncJob.SyncStatus;
import com.docst.repository.RepositoryRepository;
import com.docst.repository.SyncJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 동기화 서비스.
 * 레포지토리 동기화 작업을 관리하고 비동기로 실행한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncService {

    private final SyncJobRepository syncJobRepository;
    private final RepositoryRepository repositoryRepository;
    private final GitSyncService gitSyncService;

    /**
     * ID로 동기화 작업을 조회한다.
     *
     * @param jobId 작업 ID
     * @return 동기화 작업 (존재하지 않으면 empty)
     */
    public Optional<SyncJob> findById(UUID jobId) {
        return syncJobRepository.findById(jobId);
    }

    /**
     * 레포지토리의 가장 최근 동기화 작업을 조회한다.
     *
     * @param repositoryId 레포지토리 ID
     * @return 최근 동기화 작업 (존재하지 않으면 empty)
     */
    public Optional<SyncJob> findLatestByRepositoryId(UUID repositoryId) {
        return syncJobRepository.findFirstByRepositoryIdOrderByCreatedAtDesc(repositoryId);
    }

    /**
     * 레포지토리 동기화를 시작한다.
     * 비동기로 동기화를 실행하며, 동시에 하나의 작업만 실행될 수 있다.
     *
     * @param repositoryId 레포지토리 ID
     * @param branch 대상 브랜치 (null이면 기본 브랜치 사용)
     * @return 생성된 동기화 작업
     * @throws IllegalArgumentException 레포지토리가 존재하지 않을 경우
     * @throws IllegalStateException 이미 동기화가 진행 중일 경우
     */
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

    /**
     * 동기화 작업을 실행한다.
     * 내부적으로 GitSyncService를 호출하여 실제 Git 동기화를 수행한다.
     *
     * @param jobId 동기화 작업 ID
     */
    private void executeSync(UUID jobId) {
        // 비동기 스레드에서는 세션이 없으므로 Repository를 함께 fetch join으로 조회
        SyncJob job = syncJobRepository.findByIdWithRepository(jobId).orElse(null);
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
