package com.docst.embedding;

import com.docst.domain.DocChunk;
import com.docst.domain.DocumentVersion;
import com.docst.repository.DocChunkRepository;
import com.docst.repository.DocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 프로젝트 재임베딩 서비스.
 * Phase 4-D-5: 임베딩 모델 변경 시 기존 임베딩 삭제 후 재생성
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReEmbeddingService {

    private final DocumentVersionRepository documentVersionRepository;
    private final DocChunkRepository docChunkRepository;
    private final DocstEmbeddingService embeddingService;
    private final VectorStore vectorStore;

    // 프로젝트별 재임베딩 진행 상태 추적
    private final ConcurrentHashMap<UUID, ReEmbeddingStatus> statusMap = new ConcurrentHashMap<>();

    /**
     * 프로젝트의 모든 문서를 재임베딩한다 (비동기).
     *
     * @param projectId 프로젝트 ID
     * @return 재임베딩 작업 Future
     */
    @Async
    public CompletableFuture<ReEmbeddingResult> reEmbedProjectAsync(UUID projectId) {
        log.info("Starting re-embedding for project {}", projectId);

        // 이미 진행 중인 작업이 있는지 확인
        ReEmbeddingStatus existingStatus = statusMap.get(projectId);
        if (existingStatus != null && existingStatus.isInProgress()) {
            log.warn("Re-embedding already in progress for project {}", projectId);
            return CompletableFuture.completedFuture(
                new ReEmbeddingResult(false, 0, 0, "Re-embedding already in progress")
            );
        }

        // 상태 초기화
        ReEmbeddingStatus status = new ReEmbeddingStatus(projectId);
        statusMap.put(projectId, status);

        try {
            // 1. 프로젝트의 모든 DocumentVersion 조회
            List<DocumentVersion> versions = documentVersionRepository.findByProjectId(projectId);
            status.setTotalVersions(versions.size());
            log.info("Found {} document versions to re-embed for project {}", versions.size(), projectId);

            // 2. 기존 임베딩 삭제
            int deletedCount = deleteProjectEmbeddings(projectId);
            status.setDeletedEmbeddings(deletedCount);
            log.info("Deleted {} embeddings for project {}", deletedCount, projectId);

            // 3. 각 DocumentVersion 재임베딩
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            for (DocumentVersion version : versions) {
                try {
                    int embeddedCount = embeddingService.embedDocumentVersion(version);
                    successCount.addAndGet(embeddedCount);
                    status.incrementProcessedVersions();
                } catch (Exception e) {
                    log.error("Failed to re-embed DocumentVersion {}: {}", version.getId(), e.getMessage());
                    failCount.incrementAndGet();
                }
            }

            status.complete(successCount.get(), failCount.get());

            log.info("Re-embedding completed for project {}: {} chunks embedded, {} failures",
                projectId, successCount.get(), failCount.get());

            return CompletableFuture.completedFuture(
                new ReEmbeddingResult(true, successCount.get(), failCount.get(), null)
            );

        } catch (Exception e) {
            log.error("Re-embedding failed for project {}: {}", projectId, e.getMessage(), e);
            status.fail(e.getMessage());
            return CompletableFuture.completedFuture(
                new ReEmbeddingResult(false, 0, 0, e.getMessage())
            );
        }
    }

    /**
     * 프로젝트의 기존 임베딩을 모두 삭제한다.
     *
     * @param projectId 프로젝트 ID
     * @return 삭제된 임베딩 수
     */
    @Transactional
    public int deleteProjectEmbeddings(UUID projectId) {
        // 프로젝트의 모든 DocChunk ID 조회
        List<DocChunk> chunks = docChunkRepository.findByProjectId(projectId);

        if (chunks.isEmpty()) {
            return 0;
        }

        // VectorStore에서 삭제
        List<String> chunkIds = chunks.stream()
            .map(chunk -> chunk.getId().toString())
            .toList();

        vectorStore.delete(chunkIds);

        return chunkIds.size();
    }

    /**
     * 프로젝트의 재임베딩 진행 상태를 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @return 진행 상태 (없으면 null)
     */
    public ReEmbeddingStatus getStatus(UUID projectId) {
        return statusMap.get(projectId);
    }

    /**
     * 재임베딩 결과.
     */
    public record ReEmbeddingResult(
        boolean success,
        int embeddedCount,
        int failedCount,
        String errorMessage
    ) {}

    /**
     * 재임베딩 진행 상태.
     */
    public static class ReEmbeddingStatus {
        private final UUID projectId;
        private volatile boolean inProgress = true;
        private volatile int totalVersions = 0;
        private final AtomicInteger processedVersions = new AtomicInteger(0);
        private volatile int deletedEmbeddings = 0;
        private volatile int embeddedCount = 0;
        private volatile int failedCount = 0;
        private volatile String errorMessage;

        public ReEmbeddingStatus(UUID projectId) {
            this.projectId = projectId;
        }

        public UUID getProjectId() { return projectId; }
        public boolean isInProgress() { return inProgress; }
        public int getTotalVersions() { return totalVersions; }
        public int getProcessedVersions() { return processedVersions.get(); }
        public int getDeletedEmbeddings() { return deletedEmbeddings; }
        public int getEmbeddedCount() { return embeddedCount; }
        public int getFailedCount() { return failedCount; }
        public String getErrorMessage() { return errorMessage; }

        public double getProgress() {
            if (totalVersions == 0) return 0;
            return (double) processedVersions.get() / totalVersions * 100;
        }

        void setTotalVersions(int count) { this.totalVersions = count; }
        void setDeletedEmbeddings(int count) { this.deletedEmbeddings = count; }
        void incrementProcessedVersions() { processedVersions.incrementAndGet(); }

        void complete(int embedded, int failed) {
            this.embeddedCount = embedded;
            this.failedCount = failed;
            this.inProgress = false;
        }

        void fail(String error) {
            this.errorMessage = error;
            this.inProgress = false;
        }
    }
}
