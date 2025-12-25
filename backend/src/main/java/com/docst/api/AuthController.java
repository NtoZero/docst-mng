package com.docst.api;

import com.docst.api.ApiModels.AuthTokenResponse;
import com.docst.api.ApiModels.UserResponse;
import com.docst.domain.User;
import com.docst.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 인증 컨트롤러.
 * 로그인, 로그아웃 및 현재 사용자 정보 조회 기능을 제공한다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /**
     * 로컬 계정으로 로그인한다.
     * 사용자가 없으면 새로 생성하고, 있으면 정보를 업데이트한다.
     *
     * @param request 로그인 요청 (이메일, 표시 이름)
     * @return 인증 토큰
     */
    @PostMapping("/local/login")
    public ResponseEntity<AuthTokenResponse> login(@RequestBody LoginRequest request) {
        User user = userService.createOrUpdateLocalUser(request.email(), request.displayName());
        // TODO: Generate proper JWT token
        String token = "dev-token-" + user.getId();
        AuthTokenResponse response = new AuthTokenResponse(token, "Bearer", 3600);
        return ResponseEntity.ok(response);
    }

    /**
     * 현재 인증된 사용자 정보를 반환한다.
     *
     * @param auth Authorization 헤더
     * @return 사용자 정보
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@RequestHeader(value = "Authorization", required = false) String auth) {
        // TODO: Parse JWT and get actual user
        // For now, return a placeholder user for development
        UserResponse response = new UserResponse(
                UUID.randomUUID(),
                "LOCAL",
                "local-user",
                "local@example.com",
                "Local User",
                java.time.Instant.now()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * 로그아웃한다.
     *
     * @return 204 No Content
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // TODO: Invalidate token if using server-side session
        return ResponseEntity.noContent().build();
    }

    /**
     * 로그인 요청.
     *
     * @param email 이메일 주소
     * @param displayName 표시 이름
     */
    public record LoginRequest(String email, String displayName) {}
}
