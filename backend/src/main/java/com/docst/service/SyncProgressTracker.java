package com.docst.service;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 동기화 진행 상황 추적기.
 * 메모리에서 동기화 작업의 실시간 진행 상황을 관리한다.
 */
@Component
public class SyncProgressTracker {

    private final Map<UUID, Progress> progressMap = new ConcurrentHashMap<>();

    /**
     * 동기화 시작을 기록한다.
     *
     * @param jobId 작업 ID
     * @param repositoryId 레포지토리 ID
     */
    public void start(UUID jobId, UUID repositoryId) {
        progressMap.put(jobId, new Progress(repositoryId, 0, 0, "Initializing..."));
    }

    /**
     * 전체 문서 수를 설정한다.
     *
     * @param jobId 작업 ID
     * @param total 전체 문서 수
     */
    public void setTotal(UUID jobId, int total) {
        Progress progress = progressMap.get(jobId);
        if (progress != null) {
            progress.totalDocs = total;
            progress.message = "Found " + total + " documents";
        }
    }

    /**
     * 진행 상황을 업데이트한다.
     *
     * @param jobId 작업 ID
     * @param processed 처리된 문서 수
     * @param currentFile 현재 처리 중인 파일 경로
     */
    public void update(UUID jobId, int processed, String currentFile) {
        Progress progress = progressMap.get(jobId);
        if (progress != null) {
            progress.processedDocs = processed;
            progress.message = "Processing: " + currentFile;
        }
    }

    /**
     * 동기화 완료를 기록한다.
     *
     * @param jobId 작업 ID
     * @param message 완료 메시지
     */
    public void complete(UUID jobId, String message) {
        Progress progress = progressMap.get(jobId);
        if (progress != null) {
            progress.message = message;
        }
    }

    /**
     * 진행 상황을 조회한다.
     *
     * @param jobId 작업 ID
     * @return 진행 상황 (없으면 null)
     */
    public Progress getProgress(UUID jobId) {
        return progressMap.get(jobId);
    }

    /**
     * 레포지토리의 최근 진행 상황을 조회한다.
     *
     * @param repositoryId 레포지토리 ID
     * @return 진행 상황 (없으면 null)
     */
    public Progress getProgressByRepository(UUID repositoryId) {
        return progressMap.values().stream()
                .filter(p -> p.repositoryId.equals(repositoryId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 진행 상황을 제거한다.
     *
     * @param jobId 작업 ID
     */
    public void remove(UUID jobId) {
        progressMap.remove(jobId);
    }

    /**
     * 동기화 진행 상황.
     */
    @Getter
    public static class Progress {
        private final UUID repositoryId;
        private int totalDocs;
        private int processedDocs;
        private String message;

        public Progress(UUID repositoryId, int totalDocs, int processedDocs, String message) {
            this.repositoryId = repositoryId;
            this.totalDocs = totalDocs;
            this.processedDocs = processedDocs;
            this.message = message;
        }

        public int getProgressPercent() {
            if (totalDocs == 0) return 0;
            return (int) ((processedDocs * 100.0) / totalDocs);
        }
    }
}
