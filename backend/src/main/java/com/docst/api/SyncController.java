package com.docst.api;

import com.docst.api.ApiModels.SyncJobResponse;
import com.docst.api.ApiModels.SyncMode;
import com.docst.api.ApiModels.SyncRequest;
import com.docst.domain.SyncJob;
import com.docst.service.SyncProgressTracker;
import com.docst.service.SyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 동기화 컨트롤러.
 * 레포지토리 동기화 시작, 상태 조회, 실시간 스트리밍 기능을 제공한다.
 */
@Tag(name = "Sync", description = "레포지토리 동기화 API")
@RestController
@RequestMapping("/api/repositories/{repoId}/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;
    private final SyncProgressTracker progressTracker;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    /**
     * 레포지토리 동기화를 시작한다.
     *
     * @param repoId 레포지토리 ID
     * @param request 동기화 요청 (브랜치, 모드, 커밋 선택 가능)
     * @return 생성된 동기화 작업 (202 Accepted)
     */
    @Operation(summary = "동기화 시작", description = "레포지토리 동기화를 시작합니다. 동기화 모드 및 대상 브랜치를 지정할 수 있습니다.")
    @ApiResponse(responseCode = "202", description = "동기화 시작됨 (Accepted)")
    @PostMapping
    public ResponseEntity<SyncJobResponse> sync(
            @Parameter(description = "레포지토리 ID") @PathVariable UUID repoId,
            @RequestBody(required = false) SyncRequest request
    ) {
        String branch = request != null ? request.branch() : null;
        SyncMode mode = request != null ? request.mode() : null;
        String targetCommitSha = request != null ? request.targetCommitSha() : null;

        SyncJob job = syncService.startSync(repoId, branch, mode, targetCommitSha);
        return ResponseEntity.accepted().body(toResponse(job));
    }

    /**
     * 레포지토리의 가장 최근 동기화 상태를 조회한다.
     *
     * @param repoId 레포지토리 ID
     * @return 동기화 작업 정보 (없으면 404)
     */
    @Operation(summary = "동기화 상태 조회", description = "레포지토리의 가장 최근 동기화 작업 상태를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "동기화 작업을 찾을 수 없음")
    })
    @GetMapping("/status")
    public ResponseEntity<SyncJobResponse> getStatus(
            @Parameter(description = "레포지토리 ID") @PathVariable UUID repoId) {
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
    @Operation(summary = "동기화 진행 상황 스트리밍", description = "동기화 진행 상황을 SSE(Server-Sent Events)로 실시간 스트리밍합니다.")
    @ApiResponse(responseCode = "200", description = "스트리밍 시작")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Parameter(description = "레포지토리 ID") @PathVariable UUID repoId) {
        SseEmitter emitter = new SseEmitter(120000L); // 120 second timeout

        // Poll for status updates - 500ms 간격으로 더 빠르게
        ScheduledFuture<?> scheduledTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                syncService.findLatestByRepositoryId(repoId).ifPresent(job -> {
                    try {
                        // 진행 상황 정보 가져오기
                        SyncProgressTracker.Progress progress = progressTracker.getProgress(job.getId());

                        Map<String, Object> data = new HashMap<>();
                        data.put("jobId", job.getId().toString());
                        data.put("repositoryId", repoId.toString());
                        data.put("status", job.getStatus().name());
                        data.put("message", progress != null ? progress.getMessage() : getStatusMessage(job));
                        data.put("totalDocs", progress != null ? progress.getTotalDocs() : 0);
                        data.put("processedDocs", progress != null ? progress.getProcessedDocs() : 0);
                        data.put("progress", progress != null ? progress.getProgressPercent() : 0);

                        emitter.send(SseEmitter.event()
                                .name("message")
                                .data(data));

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
        }, 0, 500, TimeUnit.MILLISECONDS);

        // Scheduler 누수 방지: 완료/타임아웃/에러 시 task 취소
        emitter.onCompletion(() -> scheduledTask.cancel(true));
        emitter.onTimeout(() -> {
            scheduledTask.cancel(true);
            emitter.complete();
        });
        emitter.onError(e -> scheduledTask.cancel(true));

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
