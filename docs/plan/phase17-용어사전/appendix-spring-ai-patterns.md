# Appendix: Spring AI MCP Tool Annotation Patterns

## 현재 프로젝트 패턴

Docst 프로젝트는 **Spring AI 1.1.0+ `@Tool` / `@ToolParam` annotation** 패턴을 사용합니다.

### 패턴 비교

| Annotation | Package | 용도 |
|------------|---------|------|
| `@Tool` | `org.springframework.ai.tool.annotation` | 일반 LLM Tool (ChatModel) |
| `@ToolParam` | `org.springframework.ai.tool.annotation` | Tool 파라미터 설명 |
| `@McpTool` | `org.springframework.ai.mcp.server.annotation` | MCP Server 전용 Tool |
| `@McpToolParam` | `org.springframework.ai.mcp.server.annotation` | MCP Tool 파라미터 설명 |

### Docst 채택 이유: `@Tool` 사용

1. **이중 활용**: 동일 Tool이 LLM Chat과 MCP Server 양쪽에서 사용 가능
2. **기존 코드 호환**: 프로젝트 전반에 걸쳐 `@Tool` 패턴 일관 적용
3. **MethodToolCallbackProvider**: MCP Server에서 `@Tool` 메서드 자동 감지

### 코드 예시

```java
package com.docst.mcp.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class McpGlossaryTools {

    private final GlossaryService glossaryService;

    @Tool(name = "list_glossary_terms",
          description = "List glossary terms in a project.")
    public ListGlossaryTermsResult listGlossaryTerms(
        @ToolParam(description = "Project ID") String projectId,
        @ToolParam(description = "Category filter", required = false) String category
    ) {
        // Implementation
    }
}
```

### MCP Server 설정

```java
@Configuration
public class McpServerConfig {

    @Bean
    public MethodToolCallbackProvider toolProvider(
        McpDocumentTools documentTools,
        McpGitTools gitTools,
        McpProjectTools projectTools,
        McpGlossaryTools glossaryTools
    ) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(documentTools, gitTools, projectTools, glossaryTools)
            .build();
    }
}
```

### application.yml

```yaml
spring:
  ai:
    mcp:
      server:
        enabled: true
        name: Docst MCP Server
        version: 1.0.0
        type: SYNC
        capabilities:
          tool: true
        sse-message-endpoint: /mcp/messages
```

## `@McpTool` 패턴 (참고용)

Spring AI MCP Server 전용 annotation으로, MCP 서버만 제공하는 경우 사용 가능.

```java
import org.springframework.ai.mcp.server.annotation.McpTool;
import org.springframework.ai.mcp.server.annotation.McpToolParam;

@Component
public class McpOnlyTools {

    @McpTool(name = "mcp_specific_tool",
             description = "This tool is only for MCP Server")
    public Result mcpSpecificTool(
        @McpToolParam(description = "Parameter description") String param
    ) {
        // Implementation
    }
}
```

**Docst에서 사용하지 않는 이유**:
- LLM Chat 기능에서도 동일 Tool 재사용 필요
- `@Tool`이 MCP Server에서도 정상 동작
- 코드 중복 방지

## 결론

Docst 프로젝트에서 MCP Tool을 추가할 때는 **`@Tool` / `@ToolParam`** annotation을 사용합니다.
이는 LLM Chat 기능과 MCP Server 양쪽에서 동일한 Tool을 활용할 수 있게 합니다.

## 참고 자료

- [Spring AI Tool Annotation 문서](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Spring AI MCP Server 문서](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server.html)
