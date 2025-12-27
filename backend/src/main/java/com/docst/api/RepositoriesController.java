package com.docst.api;

import com.docst.api.ApiModels.CreateRepositoryRequest;
import com.docst.api.ApiModels.RepositoryResponse;
import com.docst.api.ApiModels.SetCredentialRequest;
import com.docst.api.ApiModels.UpdateRepositoryRequest;
import com.docst.domain.Credential;
import com.docst.domain.Repository;
import com.docst.domain.Repository.RepoProvider;
import com.docst.repository.CredentialRepository;
import com.docst.service.RepositoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * 레포지토리 컨트롤러.
 * Git 레포지토리 CRUD 기능을 제공한다.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RepositoriesController {

    private final RepositoryService repositoryService;
    private final CredentialRepository credentialRepository;

    /**
     * 프로젝트의 모든 레포지토리를 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @return 레포지토리 목록
     */
    @GetMapping("/projects/{projectId}/repositories")
    public List<RepositoryResponse> listRepositories(@PathVariable UUID projectId) {
        return repositoryService.findByProjectId(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 프로젝트에 새 레포지토리를 추가한다.
     *
     * @param projectId 프로젝트 ID
     * @param request 생성 요청
     * @return 생성된 레포지토리 (201 Created)
     */
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

    /**
     * 레포지토리를 조회한다.
     *
     * @param repoId 레포지토리 ID
     * @return 레포지토리 정보 (없으면 404)
     */
    @GetMapping("/repositories/{repoId}")
    public ResponseEntity<RepositoryResponse> getRepository(@PathVariable UUID repoId) {
        return repositoryService.findById(repoId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 레포지토리 정보를 업데이트한다.
     *
     * @param repoId 레포지토리 ID
     * @param request 업데이트 요청
     * @return 업데이트된 레포지토리 (없으면 404)
     */
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

    /**
     * 레포지토리를 삭제한다.
     *
     * @param repoId 레포지토리 ID
     * @return 204 No Content
     */
    @DeleteMapping("/repositories/{repoId}")
    public ResponseEntity<Void> deleteRepository(@PathVariable UUID repoId) {
        repositoryService.delete(repoId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 레포지토리에 자격증명을 연결한다.
     *
     * @param repoId 레포지토리 ID
     * @param request 자격증명 연결 요청
     * @return 업데이트된 레포지토리
     */
    @Transactional
    @PutMapping("/repositories/{repoId}/credential")
    public ResponseEntity<RepositoryResponse> setCredential(
            @PathVariable UUID repoId,
            @RequestBody SetCredentialRequest request
    ) {
        return repositoryService.findById(repoId)
                .map(repo -> {
                    if (request.credentialId() != null) {
                        Credential credential = credentialRepository.findById(request.credentialId())
                                .orElseThrow(() -> new IllegalArgumentException("Credential not found"));
                        repo.setCredential(credential);
                    } else {
                        repo.setCredential(null);
                    }
                    Repository updated = repositoryService.save(repo);
                    return ResponseEntity.ok(toResponse(updated));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Repository 엔티티를 응답 DTO로 변환한다.
     */
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
                repo.getCreatedAt(),
                repo.getCredential() != null ? repo.getCredential().getId() : null
        );
    }
}
