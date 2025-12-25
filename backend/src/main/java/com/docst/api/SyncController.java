package com.docst.api;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.docst.api.ApiModels.SyncJobResponse;
import com.docst.api.ApiModels.SyncRequest;
import com.docst.store.InMemoryStore;

@RestController
@RequestMapping("/api/repositories/{repoId}/sync")
public class SyncController {
  private final InMemoryStore store;

  public SyncController(InMemoryStore store) {
    this.store = store;
  }

  @PostMapping
  public ResponseEntity<SyncJobResponse> sync(@PathVariable UUID repoId, @RequestBody(required = false) SyncRequest request) {
    String branch = request != null ? request.branch() : null;
    var job = store.createSyncJob(repoId, branch);
    SyncJobResponse response = new SyncJobResponse(
        job.id(),
        job.repositoryId(),
        job.status(),
        job.targetBranch(),
        job.lastSyncedCommit(),
        job.errorMessage(),
        job.startedAt(),
        job.finishedAt(),
        job.createdAt()
    );
    return ResponseEntity.accepted().body(response);
  }

  @GetMapping("/stream")
  public SseEmitter stream(@PathVariable UUID repoId) {
    SseEmitter emitter = new SseEmitter();
    try {
      emitter.send(SseEmitter.event()
          .name("status")
          .data(Map.of("repositoryId", repoId, "status", "RUNNING", "message", "Sync started")));
      emitter.send(SseEmitter.event()
          .name("status")
          .data(Map.of("repositoryId", repoId, "status", "SUCCEEDED", "message", "Sync completed")));
      emitter.complete();
    } catch (IOException ex) {
      emitter.completeWithError(ex);
    }
    return emitter;
  }
}
