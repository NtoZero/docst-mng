package com.docst.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 세션 관리자.
 * Broken pipe로 끊어진 세션을 추적하고 주기적으로 정리한다.
 *
 * <p>Spring AI MCP Server의 WebMvcSseServerTransportProvider는
 * Broken pipe 발생 시 세션을 자동 정리하지 않으므로,
 * 이 컴포넌트에서 타임아웃된 세션을 수동으로 정리한다.</p>
 */
@Component
@Slf4j
public class SseSessionManager {

    /**
     * 세션 ID -> 마지막 활동 시간 (밀리초).
     */
    private final Map<String, Long> sessionActivity = new ConcurrentHashMap<>();

    /**
     * Broken pipe가 발생한 세션 ID 저장.
     */
    private final Map<String, Integer> brokenPipeSessions = new ConcurrentHashMap<>();

    /**
     * 세션 타임아웃 (밀리초): 5분.
     */
    private static final long SESSION_TIMEOUT_MS = 5 * 60 * 1000;

    /**
     * Broken pipe 허용 횟수: 3회 이상 발생 시 세션 제거.
     */
    private static final int MAX_BROKEN_PIPE_COUNT = 3;

    /**
     * 세션 활동 기록.
     *
     * @param sessionId SSE 세션 ID
     */
    public void recordActivity(String sessionId) {
        sessionActivity.put(sessionId, System.currentTimeMillis());
        // 활동이 있으면 broken pipe 카운트 초기화
        brokenPipeSessions.remove(sessionId);
        log.trace("Session activity recorded: {}", sessionId);
    }

    /**
     * Broken pipe 발생 기록.
     *
     * @param sessionId SSE 세션 ID
     */
    public void recordBrokenPipe(String sessionId) {
        int count = brokenPipeSessions.merge(sessionId, 1, Integer::sum);
        log.warn("Broken pipe recorded for session {}: count={}", sessionId, count);

        if (count >= MAX_BROKEN_PIPE_COUNT) {
            log.info("Session {} exceeded max broken pipe count ({}), marking for removal",
                    sessionId, MAX_BROKEN_PIPE_COUNT);
            removeSession(sessionId);
        }
    }

    /**
     * 세션 제거.
     *
     * @param sessionId SSE 세션 ID
     */
    public void removeSession(String sessionId) {
        sessionActivity.remove(sessionId);
        brokenPipeSessions.remove(sessionId);
        log.info("SSE session removed: {}", sessionId);
    }

    /**
     * 오래된 세션 정리 (1분마다 실행).
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupStaleSessions() {
        long now = System.currentTimeMillis();
        long cutoff = now - SESSION_TIMEOUT_MS;

        int removedCount = 0;
        for (var entry : sessionActivity.entrySet()) {
            String sessionId = entry.getKey();
            long lastActivity = entry.getValue();

            if (lastActivity < cutoff) {
                log.info("Removing stale SSE session: {} (inactive for {}ms)",
                        sessionId, now - lastActivity);
                removeSession(sessionId);
                removedCount++;
            }
        }

        if (removedCount > 0) {
            log.info("Cleaned up {} stale SSE sessions", removedCount);
        }

        // 통계 로그 (활성 세션 수)
        int activeCount = sessionActivity.size();
        int brokenCount = brokenPipeSessions.size();
        if (activeCount > 0 || brokenCount > 0) {
            log.debug("SSE sessions - active: {}, with broken pipes: {}", activeCount, brokenCount);
        }
    }

    /**
     * 현재 활성 세션 수 조회.
     *
     * @return 활성 세션 수
     */
    public int getActiveSessionCount() {
        return sessionActivity.size();
    }

    /**
     * Broken pipe가 발생한 세션 수 조회.
     *
     * @return Broken pipe 세션 수
     */
    public int getBrokenPipeSessionCount() {
        return brokenPipeSessions.size();
    }
}
