package com.docst.api;

import com.docst.llm.LlmService;
import com.docst.llm.PromptTemplate;
import com.docst.llm.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.List;
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
    private final RateLimitService rateLimitService;

    /**
     * LLM Chat (동기)
     *
     * 전체 응답을 한 번에 반환.
     * 짧은 대화나 빠른 응답이 필요한 경우 사용.
     */
    @PostMapping("/chat")
    public ChatResponse chat(
        @RequestBody ChatRequest request,
        HttpServletRequest httpRequest
    ) {
        log.info("POST /api/llm/chat: projectId={}, sessionId={}",
            request.projectId(), request.sessionId());

        // Rate Limiting 체크
        String identifier = getIdentifier(httpRequest, request.projectId());
        if (!rateLimitService.isAllowed(identifier)) {
            RateLimitService.RateLimitInfo info = rateLimitService.getRateLimitInfo(identifier);
            throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                String.format("Rate limit exceeded. Limit: %d requests per minute. Reset at: %d",
                    info.limit(), info.resetAt())
            );
        }

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
     *
     * 주의: 공백 보존을 위해 JSON 형식으로 전송.
     * 프론트엔드에서 {"content":"..."} 형식 파싱 필요.
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(
        @RequestBody ChatRequest request,
        HttpServletRequest httpRequest
    ) {
        log.info("POST /api/llm/chat/stream: projectId={}, sessionId={}",
            request.projectId(), request.sessionId());

        // Rate Limiting 체크
        String identifier = getIdentifier(httpRequest, request.projectId());
        if (!rateLimitService.isAllowed(identifier)) {
            RateLimitService.RateLimitInfo info = rateLimitService.getRateLimitInfo(identifier);
            return Flux.error(new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                String.format("Rate limit exceeded. Limit: %d requests per minute. Reset at: %d",
                    info.limit(), info.resetAt())
            ));
        }

        return llmService.streamChat(
            request.message(),
            request.projectId(),
            request.sessionId()
        ).map(chunk -> {
            // 공백 보존을 위해 JSON 형식으로 인코딩
            // SSE에서 plain text 전송 시 선행 공백이 구분자로 오인되는 문제 방지
            String escaped = chunk
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
            return "{\"content\":\"" + escaped + "\"}";
        }).doOnNext(chunk -> {
            // SSE 청크 로깅 (디버깅용)
            log.debug("SSE chunk (JSON): {}", chunk);
        });
    }

    /**
     * 프롬프트 템플릿 목록 조회
     *
     * 시스템 기본 템플릿 목록 반환.
     * 사용자가 자주 사용하는 프롬프트 패턴을 빠르게 선택할 수 있도록 지원.
     */
    @GetMapping("/templates")
    public List<PromptTemplateResponse> getTemplates() {
        log.info("GET /api/llm/templates");

        return PromptTemplate.getSystemTemplates().stream()
            .map(t -> new PromptTemplateResponse(
                t.getId(),
                t.getName(),
                t.getDescription(),
                t.getCategory(),
                t.getTemplate(),
                t.getVariables().stream()
                    .map(v -> new TemplateVariableResponse(
                        v.name(),
                        v.label(),
                        v.placeholder(),
                        v.defaultValue()
                    ))
                    .toList()
            ))
            .toList();
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

    /**
     * Prompt Template Response DTO
     */
    public record PromptTemplateResponse(
        String id,
        String name,
        String description,
        String category,
        String template,
        List<TemplateVariableResponse> variables
    ) {}

    /**
     * Template Variable Response DTO
     */
    public record TemplateVariableResponse(
        String name,
        String label,
        String placeholder,
        String defaultValue
    ) {}

    /**
     * 사용자 식별자 생성.
     *
     * 프로젝트 ID + IP 주소 조합으로 Rate Limiting 적용.
     */
    private String getIdentifier(HttpServletRequest request, UUID projectId) {
        String ipAddress = getClientIp(request);
        return projectId.toString() + ":" + ipAddress;
    }

    /**
     * 클라이언트 IP 주소 추출.
     *
     * Proxy/Load Balancer 환경을 고려하여 X-Forwarded-For 헤더 우선 사용.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For: client, proxy1, proxy2
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
