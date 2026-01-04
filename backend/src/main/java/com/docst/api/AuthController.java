package com.docst.api;

import com.docst.api.ApiModels.AuthTokenResponse;
import com.docst.api.ApiModels.UserResponse;
import com.docst.auth.JwtService;
import com.docst.domain.ApiKey;
import com.docst.domain.User;
import com.docst.service.ApiKeyService;
import com.docst.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * 인증 컨트롤러.
 * 로그인, 회원가입, 비밀번호 변경 등 인증 관련 기능을 제공한다.
 */
@Tag(name = "Authentication", description = "사용자 인증 및 회원가입 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final ApiKeyService apiKeyService;

    /**
     * LOCAL 사용자 회원가입.
     * 새로운 LOCAL 사용자를 생성한다.
     *
     * @param request 회원가입 요청 (이메일, 비밀번호, 표시 이름)
     * @return 인증 토큰
     */
    @Operation(summary = "회원가입", description = "새로운 LOCAL 사용자를 생성하고 JWT 토큰을 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (이메일 중복, 비밀번호 검증 실패 등)")
    })
    @PostMapping("/local/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            User user = userService.createLocalUser(
                    request.email(),
                    request.password(),
                    request.displayName()
            );

            String token = jwtService.generateToken(user.getId(), user.getEmail());
            AuthTokenResponse response = new AuthTokenResponse(token, "Bearer", 86400);

            log.info("User registered successfully: {}", request.email());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("Registration failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error", "REGISTRATION_FAILED",
                            "message", e.getMessage()
                    ));
        }
    }

    /**
     * LOCAL 사용자 로그인.
     * 이메일과 비밀번호로 인증한다.
     *
     * @param request 로그인 요청 (이메일, 비밀번호)
     * @return 인증 토큰
     */
    @Operation(summary = "로그인", description = "이메일과 비밀번호로 인증하고 JWT 토큰을 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패 (이메일 또는 비밀번호 불일치)")
    })
    @PostMapping("/local/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            User user = userService.authenticateLocalUser(request.email(), request.password());

            String token = jwtService.generateToken(user.getId(), user.getEmail());
            AuthTokenResponse response = new AuthTokenResponse(token, "Bearer", 86400);

            log.info("User logged in successfully: {}", request.email());
            return ResponseEntity.ok(response);
        } catch (BadCredentialsException e) {
            log.warn("Login failed for email: {}", request.email());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "AUTHENTICATION_FAILED",
                            "message", "Invalid email or password"
                    ));
        }
    }

    /**
     * 현재 인증된 사용자 정보를 반환한다.
     *
     * @param authentication Spring Security Authentication
     * @return 사용자 정보
     */
    @Operation(summary = "현재 사용자 정보 조회", description = "인증된 사용자의 정보를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않음")
    })
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
            return ResponseEntity.status(401).build();
        }

        User user = (User) authentication.getPrincipal();
        UserResponse response = new UserResponse(
                user.getId(),
                user.getProvider().name(),
                user.getProviderUserId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getCreatedAt()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * 비밀번호 변경.
     * 현재 로그인한 사용자의 비밀번호를 변경한다.
     *
     * @param request 비밀번호 변경 요청 (기존 비밀번호, 새 비밀번호)
     * @param authentication Spring Security Authentication
     * @return 성공 메시지
     */
    @Operation(summary = "비밀번호 변경", description = "현재 로그인한 사용자의 비밀번호를 변경합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "비밀번호 변경 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (비밀번호 검증 실패 등)"),
            @ApiResponse(responseCode = "401", description = "인증되지 않음 또는 현재 비밀번호 불일치")
    })
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication
    ) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "UNAUTHORIZED",
                            "message", "Authentication required"
                    ));
        }

        User user = (User) authentication.getPrincipal();

        try {
            userService.changePassword(user.getId(), request.oldPassword(), request.newPassword());

            log.info("Password changed successfully for user: {}", user.getEmail());
            return ResponseEntity.ok(Map.of(
                    "message", "Password changed successfully"
            ));
        } catch (IllegalArgumentException e) {
            log.warn("Password change failed for user {}: {}", user.getEmail(), e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error", "INVALID_REQUEST",
                            "message", e.getMessage()
                    ));
        } catch (BadCredentialsException e) {
            log.warn("Password change failed for user {}: incorrect current password", user.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "INCORRECT_PASSWORD",
                            "message", "Current password is incorrect"
                    ));
        }
    }

    /**
     * 로그아웃한다.
     *
     * @return 204 No Content
     */
    @Operation(summary = "로그아웃", description = "로그아웃을 수행합니다. (현재는 클라이언트 측에서 토큰 삭제)")
    @ApiResponse(responseCode = "204", description = "로그아웃 성공")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // TODO: Invalidate token if using server-side session
        return ResponseEntity.noContent().build();
    }

    // ===== API Key Management =====

    /**
     * Create a new API key.
     * The full API key is returned only once at creation time.
     *
     * @param request API key creation request
     * @param authentication Current user authentication
     * @return API key creation response with full key
     */
    @Operation(summary = "Create API Key", description = "Generate a new API key for MCP client authentication. The full key is shown only once.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "API key created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or duplicate name"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @PostMapping("/api-keys")
    public ResponseEntity<?> createApiKey(
            @Valid @RequestBody CreateApiKeyRequest request,
            Authentication authentication
    ) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "UNAUTHORIZED", "message", "Authentication required"));
        }

        User user = (User) authentication.getPrincipal();

        try {
            java.time.Instant expiresAt = request.expiresInDays() != null
                    ? java.time.Instant.now().plus(request.expiresInDays(), java.time.temporal.ChronoUnit.DAYS)
                    : null;

            ApiKeyService.ApiKeyCreationResult result = apiKeyService.createApiKey(
                    user.getId(),
                    request.name(),
                    expiresAt
            );

            ApiKeyCreationResponse response = new ApiKeyCreationResponse(
                    result.apiKey().getId(),
                    result.apiKey().getName(),
                    result.fullKey(),  // Only returned once!
                    result.apiKey().getKeyPrefix(),
                    result.apiKey().getExpiresAt(),
                    result.apiKey().getCreatedAt()
            );

            log.info("API key created for user {}: {}", user.getId(), request.name());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("API key creation failed for user {}: {}", user.getId(), e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "INVALID_REQUEST", "message", e.getMessage()));
        }
    }

    /**
     * List all API keys for the current user (without secrets).
     *
     * @param authentication Current user authentication
     * @return List of API keys
     */
    @Operation(summary = "List API Keys", description = "List all API keys for the authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "API keys retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping("/api-keys")
    public ResponseEntity<?> listApiKeys(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "UNAUTHORIZED", "message", "Authentication required"));
        }

        User user = (User) authentication.getPrincipal();
        java.util.List<ApiKey> keys = apiKeyService.findByUserId(user.getId());

        java.util.List<ApiKeyResponse> response = keys.stream()
                .map(k -> new ApiKeyResponse(
                        k.getId(),
                        k.getName(),
                        k.getKeyPrefix(),
                        k.getLastUsedAt(),
                        k.getExpiresAt(),
                        k.isActive(),
                        k.getCreatedAt()
                ))
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Revoke an API key.
     *
     * @param id API key ID
     * @param authentication Current user authentication
     * @return 204 No Content
     */
    @Operation(summary = "Revoke API Key", description = "Revoke an existing API key.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "API key revoked successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "404", description = "API key not found or access denied")
    })
    @DeleteMapping("/api-keys/{id}")
    public ResponseEntity<?> revokeApiKey(
            @Parameter(description = "API key ID") @PathVariable UUID id,
            Authentication authentication
    ) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "UNAUTHORIZED", "message", "Authentication required"));
        }

        User user = (User) authentication.getPrincipal();

        try {
            apiKeyService.revokeApiKey(id, user.getId());
            log.info("API key revoked: {} by user {}", id, user.getId());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.warn("API key revocation failed for user {}: {}", user.getId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", e.getMessage()));
        }
    }

    /**
     * 회원가입 요청.
     */
    public record RegisterRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Invalid email format")
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
            String password,

            @NotBlank(message = "Display name is required")
            @Size(min = 1, max = 100, message = "Display name must be between 1 and 100 characters")
            String displayName
    ) {}

    /**
     * 로그인 요청.
     */
    public record LoginRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Invalid email format")
            String email,

            @NotBlank(message = "Password is required")
            String password
    ) {}

    /**
     * 비밀번호 변경 요청.
     */
    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required")
            String oldPassword,

            @NotBlank(message = "New password is required")
            @Size(min = 8, max = 128, message = "New password must be between 8 and 128 characters")
            String newPassword
    ) {}

    /**
     * API Key 생성 요청.
     */
    public record CreateApiKeyRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 100, message = "Name must be 100 characters or less")
            String name,

            @jakarta.validation.constraints.Min(value = 1, message = "Expiration must be at least 1 day")
            @jakarta.validation.constraints.Max(value = 365, message = "Expiration must be 365 days or less")
            Integer expiresInDays
    ) {}

    /**
     * API Key 생성 응답.
     */
    public record ApiKeyCreationResponse(
            UUID id,
            String name,
            String key,  // Full key - only shown once!
            String keyPrefix,
            java.time.Instant expiresAt,
            java.time.Instant createdAt
    ) {}

    /**
     * API Key 조회 응답.
     */
    public record ApiKeyResponse(
            UUID id,
            String name,
            String keyPrefix,
            java.time.Instant lastUsedAt,
            java.time.Instant expiresAt,
            boolean active,
            java.time.Instant createdAt
    ) {}
}
