package com.docst.service;

import com.docst.api.ApiModels.SyncMode;
import com.docst.domain.Repository;
import com.docst.domain.SyncJob;
import com.docst.domain.SyncJob.SyncStatus;
import com.docst.repository.RepositoryRepository;
import com.docst.repository.SyncJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final SyncProgressTracker progressTracker;

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
        return startSync(repositoryId, branch, null, null);
    }

    /**
     * 레포지토리 동기화를 시작한다 (모드 지정).
     * 비동기로 동기화를 실행하며, 동시에 하나의 작업만 실행될 수 있다.
     *
     * @param repositoryId 레포지토리 ID
     * @param branch 대상 브랜치 (null이면 기본 브랜치 사용)
     * @param mode 동기화 모드 (null이면 FULL_SCAN)
     * @param targetCommitSha 특정 커밋 SHA (SPECIFIC_COMMIT 모드에서 사용)
     * @return 생성된 동기화 작업
     * @throws IllegalArgumentException 레포지토리가 존재하지 않을 경우
     * @throws IllegalStateException 이미 동기화가 진행 중일 경우
     */
    @Transactional
    public SyncJob startSync(UUID repositoryId, String branch, SyncMode mode, String targetCommitSha) {
        return startSync(repositoryId, branch, mode, targetCommitSha, null);
    }

    /**
     * 레포지토리 동기화를 시작한다 (모드 및 임베딩 지정).
     * 비동기로 동기화를 실행하며, 동시에 하나의 작업만 실행될 수 있다.
     *
     * @param repositoryId 레포지토리 ID
     * @param branch 대상 브랜치 (null이면 기본 브랜치 사용)
     * @param mode 동기화 모드 (null이면 FULL_SCAN)
     * @param targetCommitSha 특정 커밋 SHA (SPECIFIC_COMMIT 모드에서 사용)
     * @param enableEmbedding 임베딩 생성 여부 (null이면 true)
     * @return 생성된 동기화 작업
     * @throws IllegalArgumentException 레포지토리가 존재하지 않을 경우
     * @throws IllegalStateException 이미 동기화가 진행 중일 경우
     */
    @Transactional
    public SyncJob startSync(UUID repositoryId, String branch, SyncMode mode, String targetCommitSha, Boolean enableEmbedding) {
        Repository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        // Check if there's already a running sync
        if (syncJobRepository.existsByRepositoryIdAndStatus(repositoryId, SyncStatus.RUNNING)) {
            throw new IllegalStateException("Sync already in progress for repository: " + repositoryId);
        }

        String targetBranch = branch != null ? branch : repo.getDefaultBranch();
        SyncMode syncMode = mode != null ? mode : SyncMode.FULL_SCAN;
        boolean doEmbedding = enableEmbedding != null ? enableEmbedding : true;

        SyncJob job = new SyncJob(repo, targetBranch, syncMode, targetCommitSha, doEmbedding);
        job = syncJobRepository.save(job);

        // 트랜잭션 커밋 후에 비동기 작업 시작 (job이 DB에 확실히 저장된 후 실행)
        final UUID jobId = job.getId();
        final boolean finalDoEmbedding = doEmbedding;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                CompletableFuture.runAsync(() -> executeSync(jobId, finalDoEmbedding));
            }
        });

        return job;
    }

    /**
     * 동기화 작업을 실행한다.
     * 내부적으로 GitSyncService를 호출하여 실제 Git 동기화를 수행한다.
     *
     * @param jobId 동기화 작업 ID
     * @param enableEmbedding 임베딩 생성 여부
     */
    private void executeSync(UUID jobId, boolean enableEmbedding) {
        // 비동기 스레드에서는 세션이 없으므로 Repository를 함께 fetch join으로 조회
        SyncJob job = syncJobRepository.findByIdWithRepository(jobId).orElse(null);
        if (job == null) {
            log.error("Sync job not found: {}", jobId);
            return;
        }

        Repository repo = job.getRepository();

        try {
            job.start();
            syncJobRepository.save(job);

            // 진행 상황 추적 시작
            progressTracker.start(jobId, repo.getId());

            log.info("Starting sync for repository: {} branch: {} mode: {} embedding: {}",
                    repo.getFullName(), job.getTargetBranch(), job.getSyncMode(), enableEmbedding);

            // 마지막 동기화 커밋 조회 (INCREMENTAL 모드에서 사용)
            String lastSyncedCommit = findLatestByRepositoryId(repo.getId())
                    .map(SyncJob::getLastSyncedCommit)
                    .orElse(null);

            // Execute git sync (jobId를 전달하여 진행 상황 추적)
            String lastCommit = gitSyncService.syncRepository(
                    jobId,
                    repo.getId(),
                    job.getTargetBranch(),
                    job.getSyncMode(),
                    job.getTargetCommitSha(),
                    lastSyncedCommit,
                    enableEmbedding
            );

            job.complete(lastCommit);
            syncJobRepository.save(job);
            log.info("Sync completed for repository: {} commit: {}", repo.getFullName(), lastCommit);

        } catch (Exception e) {
            log.error("Sync failed for job: {}", jobId, e);
            job.fail(e.getMessage());
            syncJobRepository.save(job);
        } finally {
            // 완료 후 약간의 지연 후 진행 상황 정리 (SSE가 마지막 상태를 읽을 시간 확보)
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            progressTracker.remove(jobId);
        }
    }
}
