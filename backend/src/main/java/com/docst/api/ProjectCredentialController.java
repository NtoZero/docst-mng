package com.docst.api;

import com.docst.domain.Credential;
import com.docst.domain.Credential.CredentialType;
import com.docst.service.CredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 프로젝트 크리덴셜 관리 API (PROJECT_ADMIN 권한).
 * 프로젝트별 크리덴셜(PROJECT scope)을 관리한다.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/credentials")
@RequiredArgsConstructor
@Slf4j
public class ProjectCredentialController {

    private final CredentialService credentialService;

    /**
     * 프로젝트 크리덴셜 목록 조회.
     *
     * @param projectId 프로젝트 ID
     * @return 프로젝트 크리덴셜 목록
     */
    @GetMapping
    public ResponseEntity<List<ApiModels.ProjectCredentialResponse>> listProjectCredentials(
            @PathVariable UUID projectId
    ) {
        log.debug("Fetching credentials for project: {}", projectId);

        List<ApiModels.ProjectCredentialResponse> credentials = credentialService.findProjectCredentials(projectId).stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(credentials);
    }

    /**
     * 프로젝트 크리덴셜 조회.
     *
     * @param projectId 프로젝트 ID
     * @param credentialId 크리덴셜 ID
     * @return 프로젝트 크리덴셜
     */
    @GetMapping("/{credentialId}")
    public ResponseEntity<ApiModels.ProjectCredentialResponse> getProjectCredential(
            @PathVariable UUID projectId,
            @PathVariable UUID credentialId
    ) {
        log.debug("Fetching credential {} for project {}", credentialId, projectId);

        return credentialService.findProjectCredentialById(projectId, credentialId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 프로젝트 크리덴셜 생성.
     *
     * @param projectId 프로젝트 ID
     * @param request 생성 요청
     * @return 생성된 프로젝트 크리덴셜
     */
    @PostMapping
    public ResponseEntity<ApiModels.ProjectCredentialResponse> createProjectCredential(
            @PathVariable UUID projectId,
            @RequestBody ApiModels.CreateProjectCredentialRequest request
    ) {
        log.info("Creating credential '{}' for project {}", request.name(), projectId);

        try {
            CredentialType type = CredentialType.valueOf(request.type());

            Credential credential = credentialService.createProjectCredential(
                    projectId,
                    request.name(),
                    type,
                    request.secret(),
                    request.description()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(credential));

        } catch (IllegalArgumentException e) {
            log.error("Invalid request for project {}: {}", projectId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 프로젝트 크리덴셜 업데이트.
     *
     * @param projectId 프로젝트 ID
     * @param credentialId 크리덴셜 ID
     * @param request 업데이트 요청
     * @return 업데이트된 프로젝트 크리덴셜
     */
    @PutMapping("/{credentialId}")
    public ResponseEntity<ApiModels.ProjectCredentialResponse> updateProjectCredential(
            @PathVariable UUID projectId,
            @PathVariable UUID credentialId,
            @RequestBody ApiModels.UpdateProjectCredentialRequest request
    ) {
        log.info("Updating credential {} for project {}", credentialId, projectId);

        try {
            Credential updated = credentialService.updateProjectCredential(
                    projectId,
                    credentialId,
                    request.secret(),
                    request.description()
            );

            return ResponseEntity.ok(toResponse(updated));

        } catch (IllegalArgumentException e) {
            log.error("Credential {} not found in project {}: {}", credentialId, projectId, e.getMessage(), e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 프로젝트 크리덴셜 삭제.
     *
     * @param projectId 프로젝트 ID
     * @param credentialId 크리덴셜 ID
     * @return 성공 응답
     */
    @DeleteMapping("/{credentialId}")
    public ResponseEntity<Void> deleteProjectCredential(
            @PathVariable UUID projectId,
            @PathVariable UUID credentialId
    ) {
        log.info("Deleting credential {} from project {}", credentialId, projectId);

        try {
            credentialService.deleteProjectCredential(projectId, credentialId);
            return ResponseEntity.noContent().build();

        } catch (IllegalArgumentException e) {
            log.error("Credential {} not found in project {}: {}", credentialId, projectId, e.getMessage(), e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Credential 엔티티를 응답 DTO로 변환.
     *
     * @param credential 크리덴셜
     * @return 응답 DTO
     */
    private ApiModels.ProjectCredentialResponse toResponse(Credential credential) {
        return new ApiModels.ProjectCredentialResponse(
                credential.getId(),
                credential.getProject().getId(),
                credential.getName(),
                credential.getType().name(),
                credential.getScope().name(),
                credential.getDescription(),
                credential.isActive(),
                credential.getCreatedAt(),
                credential.getUpdatedAt()
        );
    }
}
