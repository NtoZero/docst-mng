package com.docst.mcp;

import com.docst.mcp.JsonRpcModels.*;
import static com.docst.mcp.JsonRpcModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * MCP Transport Controller.
 * JSON-RPC 2.0 프로토콜을 통한 MCP Server 엔드포인트를 제공한다.
 *
 * 지원 Transport:
 * - HTTP Streamable (POST /mcp)
 * - SSE (GET /mcp/stream)
 */
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
@Slf4j
public class McpTransportController {

    private final McpToolDispatcher dispatcher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // SSE 연결 관리
    private final ConcurrentHashMap<String, SseEmitter> sseEmitters = new ConcurrentHashMap<>();

    /**
     * JSON-RPC 2.0 엔드포인트 (HTTP Streamable).
     * MCP 프로토콜의 모든 메서드를 JSON-RPC로 처리한다.
     *
     * @param request JSON-RPC 요청
     * @return JSON-RPC 응답
     */
    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<JsonRpcResponse> handleJsonRpc(@RequestBody JsonRpcRequest request) {
        log.info("JSON-RPC request: method={}, id={}", request.method(), request.id());

        try {
            // 1. 프로토콜 버전 검증
            if (!"2.0".equals(request.jsonrpc())) {
                return ResponseEntity.ok(JsonRpcResponse.error(
                        request.id(),
                        JsonRpcError.invalidRequest("Invalid JSON-RPC version")
                ));
            }

            // 2. 메서드 라우팅
            Object result = switch (request.method()) {
                case "tools/list" -> handleToolsList();
                case "tools/call" -> handleToolsCall(request.params());
                case "initialize" -> handleInitialize(request.params());
                case "ping" -> handlePing();
                default -> {
                    log.warn("Unknown JSON-RPC method: {}", request.method());
                    yield null;
                }
            };

            if (result == null && !"ping".equals(request.method())) {
                return ResponseEntity.ok(JsonRpcResponse.error(
                        request.id(),
                        JsonRpcError.methodNotFound(request.method())
                ));
            }

            return ResponseEntity.ok(JsonRpcResponse.success(request.id(), result));

        } catch (IllegalArgumentException e) {
            log.warn("Invalid parameters for method {}: {}", request.method(), e.getMessage());
            return ResponseEntity.ok(JsonRpcResponse.error(
                    request.id(),
                    JsonRpcError.invalidParams(e.getMessage())
            ));
        } catch (Exception e) {
            log.error("Internal error handling JSON-RPC request: {}", e.getMessage(), e);
            return ResponseEntity.ok(JsonRpcResponse.error(
                    request.id(),
                    JsonRpcError.internalError(e.getMessage())
            ));
        }
    }

    /**
     * SSE 스트림 엔드포인트.
     * 실시간 이벤트 스트리밍을 위한 Server-Sent Events 연결.
     *
     * @param clientId 클라이언트 ID (선택)
     * @return SSE 이미터
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(required = false) String clientId) {
        String id = clientId != null ? clientId : java.util.UUID.randomUUID().toString();
        log.info("SSE connection opened: clientId={}", id);

        SseEmitter emitter = new SseEmitter(0L); // 무제한 타임아웃

        // 연결 저장
        sseEmitters.put(id, emitter);

        // 연결 종료 시 정리
        emitter.onCompletion(() -> {
            sseEmitters.remove(id);
            log.info("SSE connection completed: clientId={}", id);
        });

        emitter.onTimeout(() -> {
            sseEmitters.remove(id);
            log.info("SSE connection timeout: clientId={}", id);
        });

        emitter.onError(e -> {
            sseEmitters.remove(id);
            log.error("SSE connection error: clientId={}", id, e);
        });

        // 연결 확인 이벤트 전송
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"clientId\":\"" + id + "\"}"));
        } catch (Exception e) {
            log.error("Failed to send connection event", e);
        }

        return emitter;
    }

    /**
     * SSE 스트림을 통한 JSON-RPC 요청 처리.
     * MCP SSE transport에서 클라이언트 → 서버 메시지 전송에 사용.
     * mcp-remote 등의 클라이언트는 GET으로 SSE 연결을 열고, POST로 메시지를 보냄.
     *
     * @param request JSON-RPC 요청
     * @return JSON-RPC 응답
     */
    @PostMapping(value = "/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonRpcResponse> handleStreamPost(@RequestBody JsonRpcRequest request) {
        log.info("SSE POST request: method={}, id={}", request.method(), request.id());
        return handleJsonRpc(request);
    }

    /**
     * MCP 도구 목록 조회.
     * tools/list 메서드 핸들러.
     *
     * @return 도구 정의 목록
     */
    private ToolsListResult handleToolsList() {
        var tools = Arrays.stream(McpTool.values())
                .map(McpTool::toDefinition)
                .collect(Collectors.toList());

        log.info("Returning {} MCP tools", tools.size());
        return new ToolsListResult(tools);
    }

    /**
     * MCP 도구 호출.
     * tools/call 메서드 핸들러.
     *
     * @param params 도구 호출 파라미터 (name, arguments)
     * @return 도구 실행 결과
     */
    private Object handleToolsCall(Object params) {
        // params를 ToolCallRequest로 변환
        ToolCallRequest toolCall = objectMapper.convertValue(params, ToolCallRequest.class);

        if (toolCall.name() == null || toolCall.name().isEmpty()) {
            throw new IllegalArgumentException("Tool name is required");
        }

        log.info("Calling MCP tool: {}", toolCall.name());

        // Dispatcher로 도구 실행
        var response = dispatcher.dispatch(toolCall.name(), toolCall.arguments());

        // McpResponse를 JSON-RPC 결과로 변환
        if (response.error() != null) {
            throw new RuntimeException(response.error().message());
        }

        return response.result();
    }

    /**
     * MCP 초기화.
     * initialize 메서드 핸들러.
     *
     * @param params 초기화 파라미터
     * @return 서버 정보
     */
    private InitializeResult handleInitialize(Object params) {
        log.info("MCP initialize called");
        return new InitializeResult(
                "2024-11-05",
                new ServerInfo("Docst MCP Server", "1.0.0"),
                new Capabilities(new ToolsCapability(false))
        );
    }

    /**
     * Ping 핸들러.
     *
     * @return pong
     */
    private PingResult handlePing() {
        return new PingResult("pong");
    }

    /**
     * SSE 이벤트를 모든 연결된 클라이언트에게 브로드캐스트.
     *
     * @param eventName 이벤트 이름
     * @param data 이벤트 데이터
     */
    public void broadcast(String eventName, Object data) {
        sseEmitters.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(objectMapper.writeValueAsString(data)));
            } catch (Exception e) {
                log.error("Failed to send SSE event to client {}", id, e);
                sseEmitters.remove(id);
            }
        });
    }
}
