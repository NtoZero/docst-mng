package com.docmesh.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docmesh.api.ApiModels.CreateRepositoryRequest;
import com.docmesh.api.ApiModels.RepositoryResponse;
import com.docmesh.api.ApiModels.UpdateRepositoryRequest;
import com.docmesh.store.InMemoryStore;

@RestController
@RequestMapping("/api")
public class RepositoriesController {
  private final InMemoryStore store;

  public RepositoriesController(InMemoryStore store) {
    this.store = store;
  }

  @GetMapping("/projects/{projectId}/repositories")
  public List<RepositoryResponse> listRepositories(@PathVariable UUID projectId) {
    return store.listRepositories(projectId).stream()
        .map(repo -> new RepositoryResponse(
            repo.id(),
            repo.projectId(),
            repo.provider(),
            repo.externalId(),
            repo.owner(),
            repo.name(),
            repo.cloneUrl(),
            repo.defaultBranch(),
            repo.localMirrorPath(),
            repo.active(),
            repo.createdAt()
        ))
        .toList();
  }

  @PostMapping("/projects/{projectId}/repositories")
  public ResponseEntity<RepositoryResponse> createRepository(
      @PathVariable UUID projectId,
      @RequestBody CreateRepositoryRequest request
  ) {
    var repo = store.createRepository(
        projectId,
        request.provider(),
        request.owner(),
        request.name(),
        request.defaultBranch(),
        request.localPath()
    );
    RepositoryResponse response = new RepositoryResponse(
        repo.id(),
        repo.projectId(),
        repo.provider(),
        repo.externalId(),
        repo.owner(),
        repo.name(),
        repo.cloneUrl(),
        repo.defaultBranch(),
        repo.localMirrorPath(),
        repo.active(),
        repo.createdAt()
    );
    return ResponseEntity.created(URI.create("/api/repositories/" + repo.id())).body(response);
  }

  @GetMapping("/repositories/{repoId}")
  public ResponseEntity<RepositoryResponse> getRepository(@PathVariable UUID repoId) {
    return store.getRepository(repoId)
        .map(repo -> new RepositoryResponse(
            repo.id(),
            repo.projectId(),
            repo.provider(),
            repo.externalId(),
            repo.owner(),
            repo.name(),
            repo.cloneUrl(),
            repo.defaultBranch(),
            repo.localMirrorPath(),
            repo.active(),
            repo.createdAt()
        ))
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PutMapping("/repositories/{repoId}")
  public ResponseEntity<RepositoryResponse> updateRepository(
      @PathVariable UUID repoId,
      @RequestBody UpdateRepositoryRequest request
  ) {
    return store.updateRepository(repoId, request.active(), request.defaultBranch())
        .map(repo -> new RepositoryResponse(
            repo.id(),
            repo.projectId(),
            repo.provider(),
            repo.externalId(),
            repo.owner(),
            repo.name(),
            repo.cloneUrl(),
            repo.defaultBranch(),
            repo.localMirrorPath(),
            repo.active(),
            repo.createdAt()
        ))
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @DeleteMapping("/repositories/{repoId}")
  public ResponseEntity<Void> deleteRepository(@PathVariable UUID repoId) {
    store.deleteRepository(repoId);
    return ResponseEntity.noContent().build();
  }
}
