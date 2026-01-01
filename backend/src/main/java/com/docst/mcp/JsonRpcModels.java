package com.docst.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON-RPC 2.0 프로토콜 모델.
 * MCP Transport에서 사용되는 JSON-RPC 메시지 구조를 정의한다.
 */
public final class JsonRpcModels {
    private JsonRpcModels() {}

    /**
     * JSON-RPC 2.0 요청.
     *
     * @param jsonrpc 프로토콜 버전 (항상 "2.0")
     * @param id 요청 ID (문자열 또는 숫자)
     * @param method 호출할 메서드 이름
     * @param params 메서드 파라미터 (선택)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JsonRpcRequest(
            String jsonrpc,
            Object id,
            String method,
            Object params
    ) {
        /**
         * JSON-RPC 2.0 요청 생성 헬퍼.
         */
        public static JsonRpcRequest create(Object id, String method, Object params) {
            return new JsonRpcRequest("2.0", id, method, params);
        }
    }

    /**
     * JSON-RPC 2.0 응답 (성공 또는 오류).
     *
     * @param jsonrpc 프로토콜 버전 (항상 "2.0")
     * @param id 요청 ID
     * @param result 성공 시 결과 (error와 배타적)
     * @param error 오류 시 오류 객체 (result와 배타적)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JsonRpcResponse(
            String jsonrpc,
            Object id,
            Object result,
            JsonRpcError error
    ) {
        /**
         * 성공 응답 생성.
         */
        public static JsonRpcResponse success(Object id, Object result) {
            return new JsonRpcResponse("2.0", id, result, null);
        }

        /**
         * 오류 응답 생성.
         */
        public static JsonRpcResponse error(Object id, JsonRpcError error) {
            return new JsonRpcResponse("2.0", id, null, error);
        }

        /**
         * 오류 응답 생성 (간편 버전).
         */
        public static JsonRpcResponse error(Object id, int code, String message) {
            return error(id, new JsonRpcError(code, message, null));
        }
    }

    /**
     * JSON-RPC 2.0 오류 객체.
     *
     * @param code 오류 코드
     * @param message 오류 메시지
     * @param data 추가 오류 정보 (선택)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JsonRpcError(
            int code,
            String message,
            Object data
    ) {
        // 표준 JSON-RPC 2.0 오류 코드
        public static final int PARSE_ERROR = -32700;
        public static final int INVALID_REQUEST = -32600;
        public static final int METHOD_NOT_FOUND = -32601;
        public static final int INVALID_PARAMS = -32602;
        public static final int INTERNAL_ERROR = -32603;

        /**
         * 표준 오류 생성 헬퍼.
         */
        public static JsonRpcError parseError(String message) {
            return new JsonRpcError(PARSE_ERROR, message, null);
        }

        public static JsonRpcError invalidRequest(String message) {
            return new JsonRpcError(INVALID_REQUEST, message, null);
        }

        public static JsonRpcError methodNotFound(String method) {
            return new JsonRpcError(METHOD_NOT_FOUND, "Method not found: " + method, null);
        }

        public static JsonRpcError invalidParams(String message) {
            return new JsonRpcError(INVALID_PARAMS, message, null);
        }

        public static JsonRpcError internalError(String message) {
            return new JsonRpcError(INTERNAL_ERROR, message, null);
        }
    }

    /**
     * JSON-RPC 2.0 알림 (응답 없음).
     *
     * @param jsonrpc 프로토콜 버전 (항상 "2.0")
     * @param method 메서드 이름
     * @param params 메서드 파라미터 (선택)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JsonRpcNotification(
            String jsonrpc,
            String method,
            Object params
    ) {
        /**
         * 알림 생성 헬퍼.
         */
        public static JsonRpcNotification create(String method, Object params) {
            return new JsonRpcNotification("2.0", method, params);
        }
    }

    /**
     * MCP tools/list 응답.
     *
     * @param tools 도구 정의 목록
     */
    public record ToolsListResult(
            java.util.List<McpTool.McpToolDefinition> tools
    ) {}

    /**
     * MCP tools/call 요청.
     *
     * @param name 도구 이름
     * @param arguments 도구 인자
     */
    public record ToolCallRequest(
            String name,
            Object arguments
    ) {}
}
