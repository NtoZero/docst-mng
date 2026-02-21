package com.docst.mcp;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * SSE 세션 추적 필터.
 * /sse endpoint로의 요청을 가로채서 세션을 추적하고,
 * 응답 완료 시 세션 활동을 기록한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class SseSessionFilter implements Filter {

    private final SseSessionManager sessionManager;

    private static final String SSE_ENDPOINT = "/sse";
    private static final String SESSION_ID_ATTR = "sse.sessionId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest httpRequest) ||
            !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        String requestURI = httpRequest.getRequestURI();

        // SSE endpoint가 아니면 통과
        if (!requestURI.equals(SSE_ENDPOINT)) {
            chain.doFilter(request, response);
            return;
        }

        // SSE 연결 시작: 세션 ID 생성 및 기록
        String sessionId = UUID.randomUUID().toString();
        httpRequest.setAttribute(SESSION_ID_ATTR, sessionId);
        sessionManager.recordActivity(sessionId);

        log.info("SSE connection started: sessionId={}, remoteAddr={}",
                sessionId, httpRequest.getRemoteAddr());

        try {
            chain.doFilter(request, response);

            // 정상 완료: 세션 활동 기록
            sessionManager.recordActivity(sessionId);

        } catch (IOException e) {
            // Broken pipe, Connection reset 등의 경우
            if (e.getMessage() != null &&
                (e.getMessage().contains("Broken pipe") ||
                 e.getMessage().contains("Connection reset"))) {
                log.warn("SSE connection broken: sessionId={}, error={}",
                        sessionId, e.getMessage());
                sessionManager.recordBrokenPipe(sessionId);
            } else {
                log.error("SSE connection error: sessionId={}", sessionId, e);
                sessionManager.removeSession(sessionId);
            }
            throw e;

        } catch (Exception e) {
            log.error("Unexpected error in SSE connection: sessionId={}", sessionId, e);
            sessionManager.removeSession(sessionId);
            throw e;

        } finally {
            log.debug("SSE connection ended: sessionId={}", sessionId);
        }
    }
}
