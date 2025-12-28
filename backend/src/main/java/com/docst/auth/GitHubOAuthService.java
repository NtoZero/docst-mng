package com.docst.auth;

import com.docst.domain.User;
import com.docst.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubOAuthService {

    private final UserService userService;
    private final JwtService jwtService;

    @Value("${docst.github.client-id}")
    private String clientId;

    @Value("${docst.github.client-secret}")
    private String clientSecret;

    @Value("${docst.github.callback-url}")
    private String callbackUrl;

    private static final String GITHUB_AUTH_URL = "https://github.com/login/oauth/authorize";
    private static final String GITHUB_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String GITHUB_USER_API = "https://api.github.com/user";

    /**
     * Generate GitHub OAuth authorization URL
     */
    public String getAuthorizationUrl(String state) {
        return String.format(
                "%s?client_id=%s&redirect_uri=%s&state=%s&scope=user:email",
                GITHUB_AUTH_URL,
                clientId,
                callbackUrl,
                state
        );
    }

    /**
     * Exchange code for access token
     */
    public String exchangeCodeForToken(String code) {
        RestTemplate restTemplate = new RestTemplate();

        Map<String, String> params = new HashMap<>();
        params.put("client_id", clientId);
        params.put("client_secret", clientSecret);
        params.put("code", code);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/json");

        HttpEntity<Map<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    GITHUB_TOKEN_URL,
                    HttpMethod.POST,
                    request,
                    JsonNode.class
            );

            JsonNode body = response.getBody();
            if (body != null && body.has("access_token")) {
                return body.get("access_token").asText();
            }
        } catch (Exception e) {
            log.error("Failed to exchange code for token", e);
        }

        return null;
    }

    /**
     * Get GitHub user info with access token
     */
    public GitHubUserInfo getUserInfo(String accessToken) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Accept", "application/json");

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    GITHUB_USER_API,
                    HttpMethod.GET,
                    request,
                    JsonNode.class
            );

            JsonNode body = response.getBody();
            if (body != null) {
                Long id = body.get("id").asLong();
                String login = body.get("login").asText();
                String email = body.has("email") && !body.get("email").isNull()
                        ? body.get("email").asText()
                        : login + "@github.users.noreply.github.com";
                String name = body.has("name") && !body.get("name").isNull()
                        ? body.get("name").asText()
                        : login;

                return new GitHubUserInfo(id.toString(), login, email, name);
            }
        } catch (Exception e) {
            log.error("Failed to get user info", e);
        }

        return null;
    }

    /**
     * Create or update user from GitHub info and generate JWT
     */
    public String processOAuthLogin(GitHubUserInfo userInfo) {
        User user = userService.createOrUpdateGitHubUser(
                userInfo.id(),
                userInfo.email(),
                userInfo.name()
        );

        return jwtService.generateToken(user.getId(), user.getEmail());
    }

    public record GitHubUserInfo(String id, String login, String email, String name) {}
}
