package com.docst.api;

import com.docst.api.ApiModels.CreateCredentialRequest;
import com.docst.api.ApiModels.CredentialResponse;
import com.docst.api.ApiModels.UpdateCredentialRequest;
import com.docst.domain.Credential;
import com.docst.domain.Credential.CredentialType;
import com.docst.service.CredentialService;
import com.docst.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 자격증명 컨트롤러.
 * 자격증명 CRUD API를 제공한다.
 */
@RestController
@RequestMapping("/api/credentials")
@RequiredArgsConstructor
public class CredentialController {

    private final CredentialService credentialService;
    private final UserService userService;

    /**
     * Authorization 헤더에서 사용자 ID를 추출한다.
     * 개발 모드에서는 "dev-token-{userId}" 형식의 토큰을 파싱한다.
     */
    private UUID extractUserId(String auth) {
        if (auth != null && auth.startsWith("Bearer dev-token-")) {
            String userId = auth.replace("Bearer dev-token-", "");
            return UUID.fromString(userId);
        }
        throw new IllegalArgumentException("Invalid or missing authorization token");
    }

    /**
     * 현재 사용자의 모든 자격증명을 조회한다.
     *
     * @param auth Authorization 헤더
     * @return 자격증명 목록
     */
    @GetMapping
    public ResponseEntity<List<CredentialResponse>> list(
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        UUID userId = extractUserId(auth);
        List<Credential> credentials = credentialService.findByUserId(userId);
        return ResponseEntity.ok(credentials.stream().map(this::toResponse).toList());
    }

    /**
     * 자격증명을 ID로 조회한다.
     *
     * @param id 자격증명 ID
     * @param auth Authorization 헤더
     * @return 자격증명
     */
    @GetMapping("/{id}")
    public ResponseEntity<CredentialResponse> get(
            @PathVariable UUID id,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        UUID userId = extractUserId(auth);
        return credentialService.findById(id, userId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 새 자격증명을 생성한다.
     *
     * @param request 생성 요청
     * @param auth Authorization 헤더
     * @return 생성된 자격증명
     */
    @PostMapping
    public ResponseEntity<CredentialResponse> create(
            @RequestBody CreateCredentialRequest request,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        UUID userId = extractUserId(auth);
        CredentialType type = CredentialType.valueOf(request.type());
        Credential credential = credentialService.create(
                userId,
                request.name(),
                type,
                request.username(),
                request.secret(),
                request.description()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(credential));
    }

    /**
     * 자격증명을 수정한다.
     *
     * @param id 자격증명 ID
     * @param request 수정 요청
     * @param auth Authorization 헤더
     * @return 수정된 자격증명
     */
    @PutMapping("/{id}")
    public ResponseEntity<CredentialResponse> update(
            @PathVariable UUID id,
            @RequestBody UpdateCredentialRequest request,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        UUID userId = extractUserId(auth);
        try {
            Credential credential = credentialService.update(
                    id,
                    userId,
                    null, // name은 변경 불가
                    request.username(),
                    request.secret(),
                    request.description()
            );

            // active 상태 별도 처리
            if (request.active() != null) {
                credential.setActive(request.active());
            }

            return ResponseEntity.ok(toResponse(credential));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 자격증명을 삭제한다.
     *
     * @param id 자격증명 ID
     * @param auth Authorization 헤더
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        UUID userId = extractUserId(auth);
        try {
            credentialService.delete(id, userId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Credential 엔티티를 응답 DTO로 변환한다.
     */
    private CredentialResponse toResponse(Credential credential) {
        return new CredentialResponse(
                credential.getId(),
                credential.getName(),
                credential.getType().name(),
                credential.getUsername(),
                credential.getDescription(),
                credential.isActive(),
                credential.getCreatedAt(),
                credential.getUpdatedAt()
        );
    }
}
