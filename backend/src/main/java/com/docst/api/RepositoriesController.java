package com.docst.api;

import com.docst.api.ApiModels.CreateRepositoryRequest;
import com.docst.api.ApiModels.RepositoryResponse;
import com.docst.api.ApiModels.UpdateRepositoryRequest;
import com.docst.domain.Repository;
import com.docst.domain.Repository.RepoProvider;
import com.docst.service.RepositoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class RepositoriesController {

    private final RepositoryService repositoryService;

    public RepositoriesController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @GetMapping("/projects/{projectId}/repositories")
    public List<RepositoryResponse> listRepositories(@PathVariable UUID projectId) {
        return repositoryService.findByProjectId(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/projects/{projectId}/repositories")
    public ResponseEntity<RepositoryResponse> createRepository(
            @PathVariable UUID projectId,
            @RequestBody CreateRepositoryRequest request
    ) {
        RepoProvider provider = RepoProvider.valueOf(request.provider().toUpperCase());
        Repository repo = repositoryService.create(
                projectId,
                provider,
                request.owner(),
                request.name(),
                request.defaultBranch(),
                request.localPath()
        );
        RepositoryResponse response = toResponse(repo);
        return ResponseEntity.created(URI.create("/api/repositories/" + repo.getId())).body(response);
    }

    @GetMapping("/repositories/{repoId}")
    public ResponseEntity<RepositoryResponse> getRepository(@PathVariable UUID repoId) {
        return repositoryService.findById(repoId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/repositories/{repoId}")
    public ResponseEntity<RepositoryResponse> updateRepository(
            @PathVariable UUID repoId,
            @RequestBody UpdateRepositoryRequest request
    ) {
        return repositoryService.update(repoId, request.active(), request.defaultBranch())
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/repositories/{repoId}")
    public ResponseEntity<Void> deleteRepository(@PathVariable UUID repoId) {
        repositoryService.delete(repoId);
        return ResponseEntity.noContent().build();
    }

    private RepositoryResponse toResponse(Repository repo) {
        return new RepositoryResponse(
                repo.getId(),
                repo.getProject().getId(),
                repo.getProvider().name(),
                repo.getExternalId(),
                repo.getOwner(),
                repo.getName(),
                repo.getCloneUrl(),
                repo.getDefaultBranch(),
                repo.getLocalMirrorPath(),
                repo.isActive(),
                repo.getCreatedAt()
        );
    }
}
