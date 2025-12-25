package com.docst.api;

import com.docst.api.ApiModels.SyncJobResponse;
import com.docst.api.ApiModels.SyncRequest;
import com.docst.domain.SyncJob;
import com.docst.service.SyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 동기화 컨트롤러.
 * 레포지토리 동기화 시작, 상태 조회, 실시간 스트리밍 기능을 제공한다.
 */
@RestController
@RequestMapping("/api/repositories/{repoId}/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /**
     * 레포지토리 동기화를 시작한다.
     *
     * @param repoId 레포지토리 ID
     * @param request 동기화 요청 (브랜치 선택 가능)
     * @return 생성된 동기화 작업 (202 Accepted)
     */
    @PostMapping
    public ResponseEntity<SyncJobResponse> sync(
            @PathVariable UUID repoId,
            @RequestBody(required = false) SyncRequest request
    ) {
        String branch = request != null ? request.branch() : null;
        SyncJob job = syncService.startSync(repoId, branch);
        return ResponseEntity.accepted().body(toResponse(job));
    }

    /**
     * 레포지토리의 가장 최근 동기화 상태를 조회한다.
     *
     * @param repoId 레포지토리 ID
     * @return 동기화 작업 정보 (없으면 404)
     */
    @GetMapping("/status")
    public ResponseEntity<SyncJobResponse> getStatus(@PathVariable UUID repoId) {
        return syncService.findLatestByRepositoryId(repoId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 동기화 진행 상황을 SSE로 실시간 스트리밍한다.
     * 동기화가 완료되거나 실패하면 스트림이 종료된다.
     *
     * @param repoId 레포지토리 ID
     * @return SSE 이미터
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID repoId) {
        SseEmitter emitter = new SseEmitter(60000L); // 60 second timeout

        // Poll for status updates
        scheduler.scheduleAtFixedRate(() -> {
            try {
                syncService.findLatestByRepositoryId(repoId).ifPresent(job -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("status")
                                .data(Map.of(
                                        "jobId", job.getId(),
                                        "repositoryId", repoId,
                                        "status", job.getStatus().name(),
                                        "message", getStatusMessage(job)
                                )));

                        if (job.getStatus().name().equals("SUCCEEDED") ||
                            job.getStatus().name().equals("FAILED")) {
                            emitter.complete();
                        }
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                });
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }, 0, 1, TimeUnit.SECONDS);

        emitter.onCompletion(() -> {});
        emitter.onTimeout(emitter::complete);

        return emitter;
    }

    /**
     * 동기화 작업 상태에 따른 메시지를 반환한다.
     */
    private String getStatusMessage(SyncJob job) {
        return switch (job.getStatus()) {
            case PENDING -> "Sync pending...";
            case RUNNING -> "Sync in progress...";
            case SUCCEEDED -> "Sync completed successfully";
            case FAILED -> "Sync failed: " + job.getErrorMessage();
        };
    }

    /**
     * SyncJob 엔티티를 응답 DTO로 변환한다.
     */
    private SyncJobResponse toResponse(SyncJob job) {
        return new SyncJobResponse(
                job.getId(),
                job.getRepository().getId(),
                job.getStatus().name(),
                job.getTargetBranch(),
                job.getLastSyncedCommit(),
                job.getErrorMessage(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getCreatedAt()
        );
    }
}
