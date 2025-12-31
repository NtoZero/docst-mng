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
 * 시스템 크리덴셜 관리 API (ADMIN 권한).
 * 시스템 전역 크리덴셜(SYSTEM scope)을 관리한다.
 */
@RestController
@RequestMapping("/api/admin/credentials")
@RequiredArgsConstructor
@Slf4j
public class AdminCredentialController {

    private final CredentialService credentialService;

    /**
     * 시스템 크리덴셜 목록 조회.
     *
     * @return 시스템 크리덴셜 목록
     */
    @GetMapping
    public ResponseEntity<List<ApiModels.SystemCredentialResponse>> listSystemCredentials() {
        log.debug("Fetching all system credentials");

        List<ApiModels.SystemCredentialResponse> credentials = credentialService.findSystemCredentials().stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(credentials);
    }

    /**
     * 시스템 크리덴셜 조회.
     *
     * @param id 크리덴셜 ID
     * @return 시스템 크리덴셜
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiModels.SystemCredentialResponse> getSystemCredential(@PathVariable UUID id) {
        log.debug("Fetching system credential: {}", id);

        return credentialService.findSystemCredentialById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 시스템 크리덴셜 생성.
     *
     * @param request 생성 요청
     * @return 생성된 시스템 크리덴셜
     */
    @PostMapping
    public ResponseEntity<ApiModels.SystemCredentialResponse> createSystemCredential(
            @RequestBody ApiModels.CreateSystemCredentialRequest request
    ) {
        log.info("Creating system credential: {}", request.name());

        try {
            CredentialType type = CredentialType.valueOf(request.type());

            Credential credential = credentialService.createSystemCredential(
                    request.name(),
                    type,
                    request.secret(),
                    request.description()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(credential));

        } catch (IllegalArgumentException e) {
            log.error("Invalid credential type: {}", request.type(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 시스템 크리덴셜 업데이트.
     *
     * @param id 크리덴셜 ID
     * @param request 업데이트 요청
     * @return 업데이트된 시스템 크리덴셜
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiModels.SystemCredentialResponse> updateSystemCredential(
            @PathVariable UUID id,
            @RequestBody ApiModels.UpdateSystemCredentialRequest request
    ) {
        log.info("Updating system credential: {}", id);

        try {
            Credential updated = credentialService.updateSystemCredential(
                    id,
                    request.secret(),
                    request.description()
            );

            return ResponseEntity.ok(toResponse(updated));

        } catch (IllegalArgumentException e) {
            log.error("System credential not found: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 시스템 크리덴셜 삭제.
     *
     * @param id 크리덴셜 ID
     * @return 성공 응답
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSystemCredential(@PathVariable UUID id) {
        log.info("Deleting system credential: {}", id);

        try {
            credentialService.deleteSystemCredential(id);
            return ResponseEntity.noContent().build();

        } catch (IllegalArgumentException e) {
            log.error("System credential not found: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Credential 엔티티를 응답 DTO로 변환.
     *
     * @param credential 크리덴셜
     * @return 응답 DTO
     */
    private ApiModels.SystemCredentialResponse toResponse(Credential credential) {
        return new ApiModels.SystemCredentialResponse(
                credential.getId(),
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
