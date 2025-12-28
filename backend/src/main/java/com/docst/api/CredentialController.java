package com.docst.api;

import com.docst.api.ApiModels.CreateCredentialRequest;
import com.docst.api.ApiModels.CredentialResponse;
import com.docst.api.ApiModels.UpdateCredentialRequest;
import com.docst.auth.SecurityUtils;
import com.docst.domain.Credential;
import com.docst.domain.Credential.CredentialType;
import com.docst.service.CredentialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Credentials", description = "자격증명 관리 API")
@RestController
@RequestMapping("/api/credentials")
@RequiredArgsConstructor
public class CredentialController {

    private final CredentialService credentialService;

    /**
     * 현재 사용자의 모든 자격증명을 조회한다.
     *
     * @return 자격증명 목록
     */
    @Operation(summary = "자격증명 목록 조회", description = "현재 사용자의 모든 자격증명을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ResponseEntity<List<CredentialResponse>> list() {
        UUID userId = SecurityUtils.requireCurrentUser().getId();
        List<Credential> credentials = credentialService.findByUserId(userId);
        return ResponseEntity.ok(credentials.stream().map(this::toResponse).toList());
    }

    /**
     * 자격증명을 ID로 조회한다.
     *
     * @param id 자격증명 ID
     * @return 자격증명
     */
    @Operation(summary = "자격증명 조회", description = "자격증명 ID로 자격증명을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "자격증명을 찾을 수 없음")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CredentialResponse> get(
            @Parameter(description = "자격증명 ID") @PathVariable UUID id) {
        UUID userId = SecurityUtils.requireCurrentUser().getId();
        return credentialService.findById(id, userId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 새 자격증명을 생성한다.
     *
     * @param request 생성 요청
     * @return 생성된 자격증명
     */
    @Operation(summary = "자격증명 생성", description = "새 자격증명을 생성합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청")
    })
    @PostMapping
    public ResponseEntity<CredentialResponse> create(@RequestBody CreateCredentialRequest request) {
        UUID userId = SecurityUtils.requireCurrentUser().getId();
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
     * @return 수정된 자격증명
     */
    @Operation(summary = "자격증명 수정", description = "자격증명을 수정합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "404", description = "자격증명을 찾을 수 없음")
    })
    @PutMapping("/{id}")
    public ResponseEntity<CredentialResponse> update(
            @Parameter(description = "자격증명 ID") @PathVariable UUID id,
            @RequestBody UpdateCredentialRequest request
    ) {
        UUID userId = SecurityUtils.requireCurrentUser().getId();
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
     * @return 204 No Content
     */
    @Operation(summary = "자격증명 삭제", description = "자격증명을 삭제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "자격증명을 찾을 수 없음")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "자격증명 ID") @PathVariable UUID id) {
        UUID userId = SecurityUtils.requireCurrentUser().getId();
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
