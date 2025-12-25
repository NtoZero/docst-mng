package com.docst.api;

import com.docst.api.ApiModels.SyncJobResponse;
import com.docst.api.ApiModels.SyncRequest;
import com.docst.domain.SyncJob;
import com.docst.service.SyncService;
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

@RestController
@RequestMapping("/api/repositories/{repoId}/sync")
public class SyncController {

    private final SyncService syncService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping
    public ResponseEntity<SyncJobResponse> sync(
            @PathVariable UUID repoId,
            @RequestBody(required = false) SyncRequest request
    ) {
        String branch = request != null ? request.branch() : null;
        SyncJob job = syncService.startSync(repoId, branch);
        return ResponseEntity.accepted().body(toResponse(job));
    }

    @GetMapping("/status")
    public ResponseEntity<SyncJobResponse> getStatus(@PathVariable UUID repoId) {
        return syncService.findLatestByRepositoryId(repoId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

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

    private String getStatusMessage(SyncJob job) {
        return switch (job.getStatus()) {
            case PENDING -> "Sync pending...";
            case RUNNING -> "Sync in progress...";
            case SUCCEEDED -> "Sync completed successfully";
            case FAILED -> "Sync failed: " + job.getErrorMessage();
        };
    }

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
