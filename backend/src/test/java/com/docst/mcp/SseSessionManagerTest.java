package com.docst.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SseSessionManagerTest {

    private SseSessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new SseSessionManager();
    }

    @Test
    void recordActivity_shouldAddSession() {
        // given
        String sessionId = "test-session-1";

        // when
        sessionManager.recordActivity(sessionId);

        // then
        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(1);
    }

    @Test
    void recordBrokenPipe_shouldIncrementCount() {
        // given
        String sessionId = "test-session-2";
        sessionManager.recordActivity(sessionId);

        // when
        sessionManager.recordBrokenPipe(sessionId);

        // then
        assertThat(sessionManager.getBrokenPipeSessionCount()).isEqualTo(1);
    }

    @Test
    void recordBrokenPipe_shouldRemoveSessionAfterMaxCount() {
        // given
        String sessionId = "test-session-3";
        sessionManager.recordActivity(sessionId);

        // when
        sessionManager.recordBrokenPipe(sessionId); // 1
        sessionManager.recordBrokenPipe(sessionId); // 2
        sessionManager.recordBrokenPipe(sessionId); // 3 - should remove

        // then
        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(0);
        assertThat(sessionManager.getBrokenPipeSessionCount()).isEqualTo(0);
    }

    @Test
    void recordActivity_shouldResetBrokenPipeCount() {
        // given
        String sessionId = "test-session-4";
        sessionManager.recordActivity(sessionId);
        sessionManager.recordBrokenPipe(sessionId);

        // when
        sessionManager.recordActivity(sessionId);

        // then
        assertThat(sessionManager.getBrokenPipeSessionCount()).isEqualTo(0);
    }

    @Test
    void removeSession_shouldRemoveSession() {
        // given
        String sessionId = "test-session-5";
        sessionManager.recordActivity(sessionId);

        // when
        sessionManager.removeSession(sessionId);

        // then
        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(0);
    }
}
