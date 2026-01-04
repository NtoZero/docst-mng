package com.docst.auth;

import com.docst.domain.User;
import com.docst.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

/**
 * Authentication filter for API Key based authentication.
 * This filter checks for API keys in the following order:
 * 1. X-API-Key header
 * 2. Authorization: Bearer docst_ak_xxx
 * 3. Query parameter "api_key" (for SSE/EventSource)
 * <p>
 * If a valid API key is found, the user is authenticated and the SecurityContext is populated.
 * This filter runs before JwtAuthenticationFilter in the filter chain.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String API_KEY_PREFIX = "docst_ak_";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            // Skip if already authenticated (e.g., by another filter)
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                filterChain.doFilter(request, response);
                return;
            }

            String apiKey = extractApiKey(request);

            if (apiKey != null) {
                Optional<User> userOpt = apiKeyService.authenticateByApiKey(apiKey);

                if (userOpt.isPresent()) {
                    User user = userOpt.get();

                    // Create authentication token
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    user,
                                    null,
                                    Collections.emptyList()
                            );
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    // Set authentication in SecurityContext
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    log.debug("API key authentication successful for user: {}", user.getEmail());
                } else {
                    log.debug("API key authentication failed: invalid or expired key");
                }
            }
        } catch (Exception e) {
            log.debug("API key authentication failed: {}", e.getMessage());
            // Don't throw exception - just continue to next filter
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract API key from request headers or query parameters
     *
     * @param request HTTP request
     * @return API key if found, null otherwise
     */
    private String extractApiKey(HttpServletRequest request) {
        // 1. Try X-API-Key header first (recommended)
        String apiKeyHeader = request.getHeader(API_KEY_HEADER);
        if (apiKeyHeader != null && apiKeyHeader.startsWith(API_KEY_PREFIX)) {
            return apiKeyHeader;
        }

        // 2. Try Authorization: Bearer docst_ak_xxx
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            String token = bearerToken.substring(BEARER_PREFIX.length());
            if (token.startsWith(API_KEY_PREFIX)) {
                return token;
            }
        }

        // 3. Try query parameter (for SSE/EventSource)
        String tokenParam = request.getParameter("api_key");
        if (tokenParam != null && tokenParam.startsWith(API_KEY_PREFIX)) {
            return tokenParam;
        }

        return null;
    }
}
