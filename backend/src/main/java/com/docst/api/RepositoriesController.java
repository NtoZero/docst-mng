package com.docst.api;

import com.docst.api.ApiModels.CreateRepositoryRequest;
import com.docst.api.ApiModels.MoveRepositoryRequest;
import com.docst.api.ApiModels.RepositoryResponse;
import com.docst.api.ApiModels.SetCredentialRequest;
import com.docst.api.ApiModels.UpdateRepositoryRequest;
import com.docst.auth.RequireProjectRole;
import com.docst.auth.RequireRepositoryAccess;
import com.docst.domain.Credential;
import com.docst.domain.ProjectRole;
import com.docst.domain.Repository;
import com.docst.domain.Repository.RepoProvider;
import com.docst.git.BranchService;
import com.docst.repository.CredentialRepository;
import com.docst.service.RepositoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Repositories", description = "Git 레포지토리 관리 API")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RepositoriesController {

    private final RepositoryService repositoryService;
    private final CredentialRepository credentialRepository;
    private final BranchService branchService;

    /**
     * 프로젝트의 모든 레포지토리를 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @return 레포지토리 목록
     */
    @Operation(summary = "레포지토리 목록 조회", description = "프로젝트의 모든 레포지토리를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/projects/{projectId}/repositories")
    @RequireProjectRole(role = ProjectRole.VIEWER, projectIdParam = "projectId")
    public List<RepositoryResponse> listRepositories(
            @Parameter(description = "프로젝트 ID") @PathVariable UUID projectId) {
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
    @Operation(summary = "레포지토리 생성", description = "프로젝트에 새 레포지토리를 추가합니다. (ADMIN 권한 필요)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "레포지토리 생성 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청")
    })
    @PostMapping("/projects/{projectId}/repositories")
    @RequireProjectRole(role = ProjectRole.ADMIN, projectIdParam = "projectId")
    public ResponseEntity<RepositoryResponse> createRepository(
            @Parameter(description = "프로젝트 ID") @PathVariable UUID projectId,
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
    @Operation(summary = "레포지토리 조회", description = "레포지토리 ID로 레포지토리 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "레포지토리를 찾을 수 없음")
    })
    @GetMapping("/repositories/{repoId}")
    @RequireRepositoryAccess(role = ProjectRole.VIEWER, repositoryIdParam = "repoId")
    public ResponseEntity<RepositoryResponse> getRepository(
            @Parameter(description = "레포지토리 ID") @PathVariable UUID repoId) {
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
    @Operation(summary = "레포지토리 수정", description = "레포지토리 정보를 수정합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "404", description = "레포지토리를 찾을 수 없음")
    })
    @PutMapping("/repositories/{repoId}")
    public ResponseEntity<RepositoryResponse> updateRepository(
            @Parameter(description = "레포지토리 ID") @PathVariable UUID repoId,
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
    @Operation(summary = "레포지토리 삭제", description = "레포지토리를 삭제합니다.")
    @ApiResponse(responseCode = "204", description = "삭제 성공")
    @DeleteMapping("/repositories/{repoId}")
    public ResponseEntity<Void> deleteRepository(
            @Parameter(description = "레포지토리 ID") @PathVariable UUID repoId) {
        repositoryService.delete(repoId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 레포지토리를 다른 프로젝트로 이관한다.
     *
     * @param repoId 레포지토리 ID
     * @param request 이관 요청
     * @return 이관된 레포지토리
     */
    @Operation(summary = "레포지토리 이관", description = "레포지토리를 다른 프로젝트로 이관합니다. 문서와 동기화 기록도 함께 이동됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "이관 성공"),
            @ApiResponse(responseCode = "404", description = "레포지토리 또는 대상 프로젝트를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "대상 프로젝트에 동일한 레포지토리가 이미 존재함")
    })
    @PostMapping("/repositories/{repoId}/move")
    @RequireRepositoryAccess(role = ProjectRole.ADMIN, repositoryIdParam = "repoId")
    public ResponseEntity<RepositoryResponse> moveRepository(
            @Parameter(description = "레포지토리 ID") @PathVariable UUID repoId,
            @RequestBody MoveRepositoryRequest request
    ) {
        try {
            Repository moved = repositoryService.moveToProject(repoId, request.targetProjectId());
            return ResponseEntity.ok(toResponse(moved));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).build();
        }
    }

    /**
     * 레포지토리에 자격증명을 연결한다.
     *
     * @param repoId 레포지토리 ID
     * @param request 자격증명 연결 요청
     * @return 업데이트된 레포지토리
     */
    @Operation(summary = "자격증명 연결", description = "레포지토리에 자격증명을 연결하거나 제거합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "연결 성공"),
            @ApiResponse(responseCode = "404", description = "레포지토리 또는 자격증명을 찾을 수 없음")
    })
    @Transactional
    @PutMapping("/repositories/{repoId}/credential")
    public ResponseEntity<RepositoryResponse> setCredential(
            @Parameter(description = "레포지토리 ID") @PathVariable UUID repoId,
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

    // ===== Branch Management APIs =====

    /**
     * 레포지토리의 모든 브랜치를 조회한다.
     */
    @Operation(summary = "브랜치 목록 조회", description = "레포지토리의 모든 브랜치를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/repositories/{id}/branches")
    @RequireRepositoryAccess(role = ProjectRole.VIEWER, repositoryIdParam = "id")
    public ResponseEntity<List<String>> listBranches(
            @Parameter(description = "레포지토리 ID") @PathVariable UUID id
    ) {
        List<String> branches = branchService.listBranches(id);
        return ResponseEntity.ok(branches);
    }

    /**
     * 새 브랜치를 생성한다.
     */
    @Operation(summary = "브랜치 생성", description = "새 브랜치를 생성합니다.")
    @ApiResponse(responseCode = "201", description = "생성 성공")
    @PostMapping("/repositories/{id}/branches")
    @RequireRepositoryAccess(role = ProjectRole.EDITOR, repositoryIdParam = "id")
    public ResponseEntity<BranchResult> createBranch(
            @Parameter(description = "레포지토리 ID") @PathVariable UUID id,
            @RequestBody CreateBranchRequest request
    ) {
        String ref = branchService.createBranch(
            id,
            request.branchName(),
            request.fromBranch() != null ? request.fromBranch() : "main"
        );
        return ResponseEntity.status(201)
            .body(new BranchResult(request.branchName(), ref, true));
    }

    /**
     * 브랜치를 전환한다.
     */
    @Operation(summary = "브랜치 전환", description = "다른 브랜치로 전환합니다.")
    @ApiResponse(responseCode = "200", description = "전환 성공")
    @PostMapping("/repositories/{id}/branches/{branchName}/switch")
    @RequireRepositoryAccess(role = ProjectRole.EDITOR, repositoryIdParam = "id")
    public ResponseEntity<BranchResult> switchBranch(
            @Parameter(description = "레포지토리 ID") @PathVariable UUID id,
            @Parameter(description = "브랜치명") @PathVariable String branchName
    ) {
        branchService.switchBranch(id, branchName);
        return ResponseEntity.ok(new BranchResult(branchName, null, true));
    }

    /**
     * 현재 브랜치를 조회한다.
     */
    @Operation(summary = "현재 브랜치 조회", description = "현재 체크아웃된 브랜치를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/repositories/{id}/branches/current")
    @RequireRepositoryAccess(role = ProjectRole.VIEWER, repositoryIdParam = "id")
    public ResponseEntity<CurrentBranchResponse> getCurrentBranch(
            @Parameter(description = "레포지토리 ID") @PathVariable UUID id
    ) {
        String branchName = branchService.getCurrentBranch(id);
        return ResponseEntity.ok(new CurrentBranchResponse(branchName));
    }

    // Branch DTOs
    public record CreateBranchRequest(String branchName, String fromBranch) {}
    public record BranchResult(String branchName, String ref, boolean success) {}
    public record CurrentBranchResponse(String branchName) {}

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
