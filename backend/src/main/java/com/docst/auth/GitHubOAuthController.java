package com.docst.auth;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.UUID;

/**
 * GitHub OAuth 인증 컨트롤러
 */
@RestController
@RequestMapping("/api/auth/github")
@RequiredArgsConstructor
@Slf4j
public class GitHubOAuthController {

    private final GitHubOAuthService gitHubOAuthService;

    @Value("${docst.github.frontend-callback-url:http://localhost:3000/auth/callback}")
    private String frontendCallbackUrl;

    /**
     * GitHub OAuth 시작 - GitHub 로그인 페이지로 리다이렉트
     */
    @GetMapping("/start")
    public void startOAuth(HttpServletResponse response) throws IOException {
        // Generate random state for CSRF protection
        String state = UUID.randomUUID().toString();
        // TODO: Store state in session or cache for validation

        String authUrl = gitHubOAuthService.getAuthorizationUrl(state);
        response.sendRedirect(authUrl);
    }

    /**
     * GitHub OAuth 콜백 - GitHub에서 리다이렉트되는 엔드포인트
     */
    @GetMapping("/callback")
    public void handleCallback(
            @RequestParam("code") String code,
            @RequestParam(value = "state", required = false) String state,
            HttpServletResponse response
    ) throws IOException {
        try {
            // TODO: Validate state parameter

            // Exchange code for access token
            String accessToken = gitHubOAuthService.exchangeCodeForToken(code);
            if (accessToken == null) {
                log.error("Failed to exchange code for access token");
                response.sendRedirect(frontendCallbackUrl + "?error=token_exchange_failed");
                return;
            }

            // Get user info from GitHub
            GitHubOAuthService.GitHubUserInfo userInfo = gitHubOAuthService.getUserInfo(accessToken);
            if (userInfo == null) {
                log.error("Failed to get user info from GitHub");
                response.sendRedirect(frontendCallbackUrl + "?error=user_info_failed");
                return;
            }

            // Create or update user and generate JWT
            String jwtToken = gitHubOAuthService.processOAuthLogin(userInfo);

            // Redirect to frontend with JWT token
            response.sendRedirect(frontendCallbackUrl + "?token=" + jwtToken);

        } catch (Exception e) {
            log.error("OAuth callback error", e);
            response.sendRedirect(frontendCallbackUrl + "?error=authentication_failed");
        }
    }

    /**
     * OAuth 상태 확인용 (디버깅)
     */
    @GetMapping("/status")
    public ResponseEntity<String> status() {
        return ResponseEntity.ok("GitHub OAuth is configured");
    }
}
