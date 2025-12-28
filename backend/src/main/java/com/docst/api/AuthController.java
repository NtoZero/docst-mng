package com.docst.api;

import com.docst.api.ApiModels.AuthTokenResponse;
import com.docst.api.ApiModels.UserResponse;
import com.docst.auth.JwtService;
import com.docst.domain.User;
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
}
