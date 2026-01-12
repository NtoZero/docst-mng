package com.docst.mcp.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Server Configuration.
 * Spring AI 1.1.0+ MCP Server Tool 등록 설정.
 *
 * ToolCallbackProvider Bean을 등록하여 @Tool annotation이 붙은 메서드들을
 * MCP Server에 도구로 노출한다.
 *
 * @see McpDocumentTools
 * @see McpGitTools
 * @see McpProjectTools
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class McpServerConfig {

    private final McpDocumentTools documentTools;
    private final McpGitTools gitTools;
    private final McpProjectTools projectTools;

    /**
     * MCP Server에 등록할 Tool 콜백 제공자.
     * @Tool annotation이 붙은 모든 메서드가 자동으로 MCP 도구로 노출됨.
     *
     * @return ToolCallbackProvider 인스턴스
     */
    @Bean
    public ToolCallbackProvider mcpToolCallbackProvider() {
        log.info("Registering MCP tools: DocumentTools, GitTools, ProjectTools");
        return MethodToolCallbackProvider.builder()
            .toolObjects(documentTools, gitTools, projectTools)
            .build();
    }
}
