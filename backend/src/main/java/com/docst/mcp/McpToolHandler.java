package com.docst.mcp;

/**
 * MCP Tool 핸들러 인터페이스.
 * 각 MCP 도구의 비즈니스 로직을 처리한다.
 *
 * @param <I> 입력 타입 (McpModels의 *Input 레코드)
 * @param <R> 결과 타입 (McpModels의 *Result 레코드)
 */
@FunctionalInterface
public interface McpToolHandler<I, R> {
    /**
     * 도구 로직을 실행한다.
     *
     * @param input 입력 데이터
     * @return 실행 결과
     * @throws Exception 실행 중 오류 발생 시
     */
    R handle(I input) throws Exception;
}
