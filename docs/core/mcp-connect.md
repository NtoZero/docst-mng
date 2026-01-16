# MCP Server 연결 가이드

Docst MCP Server를 Claude Desktop/Claude Code와 연동하는 방법을 설명합니다.

---

## 개요

Docst는 **Spring AI 1.1.0+ MCP Server**를 사용하여 AI 에이전트가 문서를 조회/검색/수정할 수 있는 MCP 인터페이스를 제공합니다.

### 지원 Transport

| Transport | 용도 | 클라이언트 |
|-----------|------|-----------|
| **SSE** (Server-Sent Events) | 원격 서버, 웹 클라이언트 | Supergateway, mcp-remote |
| **STDIO** | 로컬 CLI 연동 | Claude Desktop 직접 연결 |

---

## 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                     Claude Desktop / Claude Code                 │
└─────────────────────────────────────────────────────────────────┘
                              │ STDIO
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        Supergateway                              │
│                    (SSE ↔ STDIO 변환)                            │
└─────────────────────────────────────────────────────────────────┘
                              │ SSE (HTTP)
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Docst MCP Server                              │
│              Spring AI 1.1.0+ (WebMVC SSE)                       │
├─────────────────────────────────────────────────────────────────┤
│  Endpoints:                                                      │
│  - GET  /sse           → SSE 연결 (세션 생성)                    │
│  - POST /mcp/messages  → 클라이언트 메시지 수신                   │
├─────────────────────────────────────────────────────────────────┤
│  Tools:                                                          │
│  - list_projects, list_documents, get_document                   │
│  - search_documents, list_document_versions, diff_document       │
│  - sync_repository, create_document, update_document             │
│  - push_to_remote                                                │
└─────────────────────────────────────────────────────────────────┘
```

---

## 연결 방법

### 1. Supergateway 설치 (권장)

```bash
# Supergateway: SSE ↔ STDIO 프로토콜 변환 프록시
# Claude Desktop은 STDIO 프로토콜만 지원하므로 SSE 서버 연결 시 필수
npm install -g supergateway
```

### 2. Claude Desktop 설정

**Windows**: `%APPDATA%\Claude\claude_desktop_config.json`
**macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`

```jsonc
{
  "mcpServers": {
    // "docst": MCP 서버 식별자 (Claude UI에 표시됨)
    "docst": {
      // Supergateway 실행 명령
      "command": "supergateway",
      "args": [
        // --sse: SSE 서버 URL 지정 (Spring AI 기본 endpoint: /sse)
        "--sse",
        "http://localhost:8342/sse",
        // --header: 인증 헤더 추가 (Docst API Key 인증)
        "--header",
        "X-API-Key: YOUR_API_KEY"
      ]
    }
  }
}
```

### 3. Claude Code 설정

#### 방법 A: CLI 한 줄 명령어 (권장)

Claude Code CLI는 SSE transport를 직접 지원하므로, Supergateway 없이 한 줄 명령어로 MCP 서버를 추가할 수 있습니다.

```bash
# Docst MCP 서버 추가 (SSE 직접 연결)
claude mcp add --transport sse docst http://localhost:8342/sse \
  --header "X-API-Key: YOUR_API_KEY"
```

**명령어 옵션 설명**:
| 옵션 | 설명 |
|------|------|
| `--transport sse` | SSE(Server-Sent Events) transport 사용 |
| `docst` | MCP 서버 식별자 (원하는 이름으로 변경 가능) |
| `http://localhost:8342/sse` | Docst MCP Server SSE endpoint URL |
| `--header "X-API-Key: ..."` | 인증 헤더 추가 |

**추가 명령어**:
```bash
# 등록된 MCP 서버 목록 확인
claude mcp list

# MCP 서버 제거
claude mcp remove docst

# MCP 서버 재설정 (제거 후 다시 추가)
claude mcp remove docst && claude mcp add --transport sse docst http://localhost:8342/sse --header "X-API-Key: YOUR_API_KEY"
```

#### 방법 B: 설정 파일 직접 수정

**설정 파일**: `~/.claude/settings.json` 또는 프로젝트별 `.claude/settings.local.json`

```jsonc
{
  "mcpServers": {
    // Claude Code에서 사용할 MCP 서버 정의
    "docst": {
      // SSE transport 직접 연결 (Supergateway 불필요)
      "type": "sse",
      "url": "http://localhost:8342/sse",
      "headers": {
        "X-API-Key": "YOUR_API_KEY"
      }
    }
  }
}
```

#### 방법 C: Supergateway 사용 (Legacy)

SSE 직접 연결이 불가능한 환경에서만 사용:

```jsonc
{
  "mcpServers": {
    // Claude Code에서 사용할 MCP 서버 정의
    "docst": {
      // STDIO 기반 명령 실행 (Supergateway가 SSE로 변환)
      "command": "supergateway",
      "args": [
        // SSE endpoint URL (Spring AI MCP Server 기본값: /sse)
        "--sse",
        "http://localhost:8342/sse",
        // API Key를 HTTP 헤더로 전달
        "--header",
        "X-API-Key: YOUR_API_KEY"
      ]
    }
  }
}
```

> **권장**: 방법 A(CLI 한 줄 명령어) 또는 방법 B(SSE 직접 연결)를 사용하세요.
> Supergateway는 추가 의존성이 필요하고 프로세스 오버헤드가 있습니다.

---

## API Key 발급

MCP 연결에는 Docst API Key가 필요합니다.

### 발급 방법

1. Docst 웹 UI 로그인
2. **Settings** → **API Keys** 이동
3. **Generate API Key** 클릭
4. 생성된 키 복사 (형식: `docst_ak_xxxx...`)

### API Key 형식

```
# Docst API Key 형식
# - prefix: docst_ak_
# - body: Base64 인코딩된 랜덤 문자열
docst_ak_{random_string}
```

---

## 서버 설정

### application.yml

```yaml
# Spring AI MCP Server 설정
# 참고: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server.html
spring:
  ai:
    mcp:
      server:
        # MCP 서버 활성화 여부
        enabled: true

        # 서버 메타데이터 (MCP initialize 응답에 포함)
        name: Docst MCP Server
        version: 1.0.0

        # 동작 모드: SYNC(동기) 또는 ASYNC(비동기)
        # SYNC: 요청-응답 즉시 처리 (대부분의 경우 권장)
        type: SYNC

        # Transport 타입: STDIO, WEBMVC, WEBFLUX
        # WEBMVC: Spring MVC 기반 SSE transport (spring-ai-starter-mcp-server-webmvc)
        transport: WEBMVC

        # MCP Capabilities 설정
        # tool: Tool 호출 기능 (필수)
        # resource/prompt/completion: 선택적 기능
        capabilities:
          tool: true
          # resource: true   # 리소스 제공 시 활성화
          # prompt: true     # 프롬프트 템플릿 제공 시 활성화

        # SSE Transport 설정 (WEBMVC/WEBFLUX 전용)
        # 클라이언트가 메시지를 전송할 endpoint 경로
        # 기본값: /mcp/message (주의: 단수형)
        sse-message-endpoint: /mcp/messages

        # SSE endpoint 경로 (기본값: /sse)
        # sse-endpoint: /sse

        # SSE Keep-alive 간격 (기본값: 30s)
        # keep-alive-interval: 30s
```

### SecurityConfig 허용 경로

```java
// Spring Security 설정에서 MCP endpoint 허용 필수
// SSE 연결과 메시지 수신 endpoint 모두 permitAll 설정
.requestMatchers(
    "/sse",           // SSE 연결 endpoint (GET)
    "/sse/**",        // SSE 하위 경로 (세션 관리)
    "/mcp/messages"   // 클라이언트 메시지 수신 (POST)
).permitAll()

// 주의: API Key 인증은 별도 필터(ApiKeyAuthenticationFilter)에서 처리
// SecurityConfig의 permitAll은 Spring Security 인증만 우회
```

---

## MCP Tools

### 프로젝트 관련

| Tool | 설명 |
|------|------|
| `list_projects` | 사용자가 접근 가능한 프로젝트 목록 조회 |

### 문서 관련

| Tool | 설명 |
|------|------|
| `list_documents` | 프로젝트/레포지토리의 문서 목록 조회 |
| `get_document` | 문서 내용 조회 (버전 지정 가능) |
| `list_document_versions` | 문서 버전 히스토리 |
| `diff_document` | 두 버전 비교 (unified diff) |
| `search_documents` | 키워드/semantic/hybrid 검색 |

### Git 관련

| Tool | 설명 |
|------|------|
| `sync_repository` | 원격 Git에서 최신 변경사항 동기화 |
| `create_document` | 새 문서 생성 (commit 옵션) |
| `update_document` | 문서 내용 업데이트 (commit 옵션) |
| `push_to_remote` | 로컬 변경사항 원격 푸시 |

---

## 구현 상세

### Tool 등록 (Spring AI 1.1.0+)

`@Tool` annotation을 사용한 선언적 도구 정의:

```java
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP Document Tools.
 * Spring AI 1.1.0+ @Tool annotation 기반 문서 관련 MCP 도구.
 *
 * @Tool annotation 속성:
 * - name: Tool 이름 (MCP tools/list 응답에 포함)
 * - description: Tool 설명 (AI 모델이 언제/어떻게 호출할지 판단하는 데 사용)
 *
 * @ToolParam annotation 속성:
 * - description: 파라미터 설명 (AI 모델이 올바른 값을 전달하도록 안내)
 * - required: 필수 여부 (기본값: true, @Nullable이면 자동으로 false)
 */
@Component
public class McpDocumentTools {

    // AI 모델이 이 Tool을 언제 호출해야 하는지 명확히 설명
    // 좋은 description은 Tool 사용 정확도를 크게 높임
    @Tool(name = "list_documents",
          description = "List documents in a project or repository. " +
                        "Either repositoryId or projectId is required. " +
                        "Can filter by path prefix and document type.")
    public ListDocumentsResult listDocuments(
        // required=true (기본값): AI가 반드시 이 값을 제공해야 함
        @ToolParam(description = "Repository ID to list documents from (UUID format)")
        String repositoryId,

        // required=false: 선택적 파라미터, AI가 생략 가능
        @ToolParam(description = "Project ID to list documents from", required = false)
        String projectId,

        // 파라미터 설명에 예시나 형식을 포함하면 AI가 더 정확하게 호출
        @ToolParam(description = "Path prefix filter (e.g., 'docs/')", required = false)
        String pathPrefix
    ) {
        // Spring AI가 자동으로 JSON Schema 생성
        // AI 모델은 이 schema를 보고 파라미터 형식을 이해
        // 구현 로직...
    }
}
```

### ToolCallbackProvider Bean

```java
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Server Configuration.
 * Spring AI 1.1.0+ MCP Server Tool 등록 설정.
 *
 * ToolCallbackProvider Bean을 등록하면 Spring AI MCP Server가
 * 자동으로 @Tool annotation이 붙은 메서드들을 MCP 도구로 노출함.
 *
 * 주의: 이 Bean이 없으면 MCP tools/list 응답이 비어있음!
 */
@Configuration
public class McpServerConfig {

    /**
     * MCP Server에 등록할 Tool 콜백 제공자.
     *
     * MethodToolCallbackProvider.builder()
     * - toolObjects(): @Tool annotation이 붙은 메서드를 가진 객체들 등록
     * - 각 객체의 @Tool 메서드가 자동으로 MCP 도구로 변환됨
     *
     * @param documentTools 문서 관련 도구 (list_documents, get_document, ...)
     * @param gitTools Git 관련 도구 (sync_repository, create_document, ...)
     * @param projectTools 프로젝트 관련 도구 (list_projects)
     * @return ToolCallbackProvider 인스턴스
     */
    @Bean
    public ToolCallbackProvider mcpToolCallbackProvider(
        McpDocumentTools documentTools,
        McpGitTools gitTools,
        McpProjectTools projectTools
    ) {
        // Spring AI가 이 Bean을 감지하고 MCP Server에 자동 등록
        return MethodToolCallbackProvider.builder()
            .toolObjects(documentTools, gitTools, projectTools)
            .build();
    }
}
```

### Annotation 비교

```java
// ✅ 올바른 import (Spring AI 1.1.0+ 공식)
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

// ❌ 잘못된 import (Spring AI Community 확장 - 호환 안됨)
// import org.springaicommunity.mcp.annotation.McpTool;
// import org.springaicommunity.mcp.annotation.McpToolParam;

// 주의: Community 확장(@McpTool)은 spring-ai-starter-mcp-server-webmvc와 호환되지 않음
// 반드시 공식 @Tool annotation 사용
```

---

## 트러블슈팅

### 문제 1: `NoResourceFoundException: No static resource mcp/sse`

**원인**: 클라이언트가 `/mcp/sse`로 요청하지만 Spring AI 기본 경로는 `/sse`

**해결**: 클라이언트 설정에서 endpoint를 `/sse`로 변경

```diff
- "http://localhost:8342/mcp/sse"
+ "http://localhost:8342/sse"
```

### 문제 2: `POST /sse` 실패

**원인**: mcp-remote는 메시지를 `POST /sse`로 전송하지만, Spring AI는 별도 메시지 엔드포인트 사용

**해결**: Supergateway 사용 (SSE ↔ STDIO 변환)

```bash
# mcp-remote 대신 supergateway 사용
npm install -g supergateway
```

### 문제 3: Tool 목록이 비어있음

**원인**:
1. `@McpTool` (Community) 대신 `@Tool` (공식) annotation 사용 필요
2. `ToolCallbackProvider` Bean 누락

**해결**:
```java
// 1. 올바른 import 사용
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

// 2. McpServerConfig에서 ToolCallbackProvider Bean 등록
@Bean
public ToolCallbackProvider mcpToolCallbackProvider(...) {
    return MethodToolCallbackProvider.builder()
        .toolObjects(documentTools, gitTools, projectTools)
        .build();
}
```

### 문제 4: 인증 실패

**원인**: API Key가 없거나 유효하지 않음

**해결**:
```bash
# Supergateway --header 옵션으로 API Key 전달
supergateway --sse http://localhost:8342/sse --header "X-API-Key: docst_ak_xxx"
```

---

## SSE 연결 테스트

### curl로 SSE 연결 확인

```bash
# SSE 연결 테스트
# -v: verbose (상세 출력)
# -N: buffering 비활성화 (SSE 실시간 수신)
# -H: HTTP 헤더 추가
curl -v -N \
  -H "X-API-Key: YOUR_API_KEY" \
  http://localhost:8342/sse
```

**정상 응답**:
```
# HTTP 200 + text/event-stream Content-Type
HTTP/1.1 200 OK
Content-Type: text/event-stream

# endpoint 이벤트: 클라이언트가 메시지를 보낼 경로
event: endpoint
data: /mcp/messages?sessionId=xxx

# message 이벤트: MCP 프로토콜 메시지 (JSON-RPC 2.0)
event: message
data: {"jsonrpc":"2.0","method":"notifications/initialized"}
```

### MCP Inspector로 도구 목록 확인

```bash
# MCP Inspector: Anthropic 공식 MCP 디버깅 도구
# SSE 서버에 연결하여 tools/list, resources/list 등 테스트 가능
npx @anthropic-ai/mcp-inspector sse http://localhost:8342/sse
```

---

## 대안 연결 방식

### STDIO Transport (로컬 전용)

REST API 없이 MCP만 사용하는 경우:

**application-mcp.yml**:
```yaml
spring:
  main:
    # 웹 서버 비활성화 (STDIO 전용)
    # 주의: REST API 사용 불가
    web-application-type: none
  ai:
    mcp:
      server:
        # STDIO transport 활성화
        # 표준 입출력으로 MCP 프로토콜 통신
        stdio: true
```

**Claude Desktop 설정**:
```jsonc
{
  "mcpServers": {
    "docst": {
      // JAR 직접 실행 (Supergateway 불필요)
      "command": "java",
      "args": [
        "-jar",
        "backend.jar",
        // MCP 전용 프로필 활성화
        "--spring.profiles.active=mcp"
      ]
    }
  }
}
```

### HTTP Remote (Claude Desktop Max 전용)

Claude Desktop Max 플랜에서는 직접 HTTP 연결 지원:

```jsonc
{
  "mcpServers": {
    "docst": {
      // Max 플랜 전용: HTTP Remote 직접 연결
      // Supergateway 없이 SSE 서버에 직접 연결
      "url": "http://localhost:8342/sse",
      "headers": {
        "X-API-Key": "YOUR_API_KEY"
      }
    }
  }
}
```

---

## 의존성

### Maven

```xml
<!-- Spring AI MCP Server (WebMVC SSE Transport) -->
<!-- 포함: spring-boot-starter-web, mcp-spring-webmvc -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

### Gradle

```kotlin
// Spring AI MCP Server (WebMVC SSE Transport)
// SSE endpoint 자동 구성, @Tool annotation 지원
implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")
```

---

## 참고 자료

- [Spring AI MCP Server Documentation](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server.html)
- [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [MCP Protocol Specification](https://modelcontextprotocol.io/docs)
- [Supergateway GitHub](https://github.com/anthropics/supergateway)
- [Claude Desktop MCP Setup](https://docs.anthropic.com/en/docs/claude-code/mcp)
