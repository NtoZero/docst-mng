package com.docst.api;

import com.docst.llm.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * LLM API Controller
 *
 * 백엔드 프록시 패턴으로 LLM 서비스 제공.
 * 프론트엔드는 이 API를 통해 LLM과 대화.
 */
@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "docst.llm", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LlmController {

    private final LlmService llmService;

    /**
     * LLM Chat (동기)
     *
     * 전체 응답을 한 번에 반환.
     * 짧은 대화나 빠른 응답이 필요한 경우 사용.
     */
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        log.info("POST /api/llm/chat: projectId={}, sessionId={}",
            request.projectId(), request.sessionId());

        String response = llmService.chat(
            request.message(),
            request.projectId(),
            request.sessionId()
        );

        return new ChatResponse(response);
    }

    /**
     * LLM Chat (스트리밍, Server-Sent Events)
     *
     * 응답을 실시간으로 스트리밍하여 전송.
     * 긴 응답이나 Tool 호출이 많은 경우 사용자 경험 향상.
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        log.info("POST /api/llm/chat/stream: projectId={}, sessionId={}",
            request.projectId(), request.sessionId());

        return llmService.streamChat(
            request.message(),
            request.projectId(),
            request.sessionId()
        );
    }

    /**
     * Chat Request DTO
     *
     * @param message 사용자 메시지
     * @param projectId 프로젝트 ID (Tool Context로 전달)
     * @param sessionId 세션 ID (대화 히스토리 관리)
     */
    public record ChatRequest(
        String message,
        UUID projectId,
        String sessionId
    ) {}

    /**
     * Chat Response DTO
     *
     * @param content LLM 응답 내용
     */
    public record ChatResponse(String content) {}
}
