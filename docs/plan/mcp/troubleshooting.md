# MCP 연동 Troubleshooting

## 1. Claude Desktop에서 HTTP 프록시가 필요한 이유

### Claude Desktop의 Transport 제한

Claude Desktop은 **stdio transport만 지원**합니다. HTTP transport를 직접 지원하지 않습니다.

#### MCP Transport 종류

| Transport | 설명 | 사용 방식 | 지원 클라이언트 |
|-----------|------|----------|----------------|
| **stdio** | 표준 입출력 기반 통신 | 프로세스 실행 (`command` + `args`) | Claude Desktop, 대부분의 MCP 클라이언트 |
| **HTTP** | HTTP/HTTPS 기반 통신 | URL 연결 (`url` + `transport`) | Claude Code, 웹 기반 클라이언트 |
| **SSE** | Server-Sent Events | EventSource 연결 | 웹 브라우저 |

### 왜 Claude Desktop은 stdio만 지원하는가?

#### 1. **보안 및 샌드박싱**

```
Claude Desktop Process
  ↓ (stdio - 프로세스 간 통신)
MCP Server Process (로컬)
  ↓
로컬 파일 시스템
```

- stdio는 **로컬 프로세스 간 통신**이므로 외부 네트워크 노출 없음
- 네트워크 방화벽이나 프록시 설정에 영향받지 않음
- 악의적인 원격 서버 연결 방지

#### 2. **간단한 아키텍처**

```javascript
// Claude Desktop 내부 구조 (추정)
const mcpServer = spawn('npx', ['-y', '@modelcontextprotocol/server-filesystem']);
mcpServer.stdin.write(jsonRpcRequest);
mcpServer.stdout.on('data', handleJsonRpcResponse);
```

- 자식 프로세스 생성 (`spawn`, `exec`)
- stdin/stdout 파이프로 JSON-RPC 메시지 교환
- Node.js 기본 API만으로 구현 가능

#### 3. **오프라인 동작**

- 네트워크 연결 없이도 MCP 서버 사용 가능
- 로컬 파일 시스템, SQLite 등 로컬 리소스 접근
- 인터넷 끊김에 영향 없음

---

## 2. Docst가 HTTP MCP를 제공하는 이유

### Docst의 아키텍처 특성

```
┌─────────────────────┐
│  Spring Boot App    │
│  (Port 8342)        │
│                     │
│  ┌───────────────┐  │
│  │ REST API      │  │  ← 웹 UI, 일반 API 호출
│  ├───────────────┤  │
│  │ MCP Endpoint  │  │  ← MCP 클라이언트
│  │ POST /mcp     │  │
│  └───────────────┘  │
└─────────────────────┘
```

#### 1. **이미 실행 중인 웹 서버**

- Docst는 Spring Boot 기반 웹 애플리케이션
- 포트 8342에서 이미 HTTP 서버 실행 중
- 새로운 MCP 엔드포인트(`/mcp`)를 추가하는 것이 자연스러움

#### 2. **다중 클라이언트 지원**

```
Claude Code ─────┐
                 │
웹 브라우저 ─────┤
                 ├─→ HTTP MCP Server (Docst)
API 클라이언트 ──┤
                 │
Postman ─────────┘
```

- 여러 클라이언트가 동시에 연결 가능
- 웹 기반 클라이언트 지원 (브라우저에서 직접 호출 가능)
- RESTful API와 일관된 인증 방식 (JWT, API Key)

#### 3. **인증 및 권한 관리**

```java
@PostMapping("/mcp")
public ResponseEntity<JsonRpcResponse> handleMcp(@RequestBody JsonRpcRequest request) {
    User user = SecurityUtils.getCurrentUser(); // JWT or API Key
    // 권한 검증
    // ...
}
```

- Spring Security 통합
- 사용자별 권한 관리 (OWNER, ADMIN, EDITOR, VIEWER)
- 프로젝트 단위 접근 제어

#### 4. **stdio는 부적합**

만약 Docst가 stdio MCP를 제공한다면:

```bash
# 매번 새로운 Spring Boot 프로세스 시작 (❌)
npx docst-mcp-server

# 문제점:
# - Spring Boot 시작에 5-10초 소요
# - DB 커넥션 풀 매번 생성
# - 메모리 낭비 (JVM 힙 여러 개)
# - 이미 실행 중인 Docst 서버와 별도 프로세스
```

---

## 3. 프록시의 작동 원리

### HTTP → stdio 프록시

```
┌──────────────────┐         ┌──────────────────┐         ┌──────────────────┐
│ Claude Desktop   │ stdio   │ mcp-client-http  │  HTTP   │ Docst MCP Server │
│                  │────────→│ (Proxy)          │────────→│ (Port 8342)      │
│                  │←────────│                  │←────────│                  │
└──────────────────┘         └──────────────────┘         └──────────────────┘
```

### 프록시가 하는 일

#### 1. **Transport 변환**

```javascript
// mcp-client-http 내부 동작 (의사 코드)

// stdin에서 JSON-RPC 요청 읽기
process.stdin.on('data', async (data) => {
  const jsonRpcRequest = JSON.parse(data);

  // HTTP POST 요청으로 변환
  const response = await fetch('http://localhost:8342/mcp', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-API-Key': 'docst_ak_xxx'
    },
    body: JSON.stringify(jsonRpcRequest)
  });

  const jsonRpcResponse = await response.json();

  // stdout으로 응답 전송
  process.stdout.write(JSON.stringify(jsonRpcResponse));
});
```

#### 2. **인증 헤더 추가**

```bash
# Claude Desktop 설정
"args": [
  "-y",
  "mcp-client-http",
  "http://localhost:8342/mcp",
  "--header",
  "X-API-Key: docst_ak_xxx"  # ← 프록시가 모든 요청에 이 헤더 추가
]
```

프록시 없이는:
- Claude Desktop이 직접 HTTP 헤더 설정 불가능
- API Key 인증 불가능

#### 3. **에러 처리**

```javascript
// HTTP 에러를 JSON-RPC 에러로 변환
if (!response.ok) {
  const error = {
    jsonrpc: "2.0",
    id: jsonRpcRequest.id,
    error: {
      code: -32603,
      message: `HTTP ${response.status}: ${await response.text()}`
    }
  };
  process.stdout.write(JSON.stringify(error));
}
```

---

## 4. 프록시 사용법

### 옵션 1: mcp-client-http (NPM 패키지)

```json
{
  "mcpServers": {
    "docst": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-client-http",
        "http://localhost:8342/mcp",
        "--header",
        "X-API-Key: docst_ak_xxx"
      ]
    }
  }
}
```

**장점:**
- NPM 패키지로 배포됨 (자동 설치)
- 여러 HTTP MCP 서버에 범용으로 사용 가능
- 커뮤니티 유지보수

**단점:**
- 외부 의존성
- 범용이라 Docst 특화 기능 없음

### 옵션 2: Docst 전용 stdio Wrapper (개발 예정)

```json
{
  "mcpServers": {
    "docst": {
      "command": "npx",
      "args": ["-y", "@docst/mcp-client"]
    }
  }
}
```

**장점:**
- API Key 자동 관리 (환경 변수 또는 설정 파일에서 로드)
- Docst 서버 자동 검색 (localhost:8342, localhost:8080 등)
- 에러 메시지 한글화
- Docst 특화 기능 (헬스체크, 자동 재연결 등)

**단점:**
- 아직 개발되지 않음

---

## 5. 대안: Docst stdio MCP 서버 추가 (향후 고려)

### 아키텍처

```
┌──────────────────────────────────────────┐
│  Docst Backend (Spring Boot)             │
│  ┌────────────────────────────────────┐  │
│  │ HTTP MCP Server (Port 8342/mcp)    │  │ ← 기존 (유지)
│  └────────────────────────────────────┘  │
└──────────────────────────────────────────┘

┌──────────────────────────────────────────┐
│  @docst/mcp-stdio-server (Node.js)       │  ← 새로 추가
│  ┌────────────────────────────────────┐  │
│  │ stdin/stdout ←→ HTTP Client        │  │
│  │ (Docst HTTP API 호출)              │  │
│  └────────────────────────────────────┘  │
└──────────────────────────────────────────┘
```

### 장점

- Claude Desktop에서 프록시 없이 직접 연결
- 설정 간단 (npx만 실행하면 끝)

### 단점

- 새로운 패키지 개발 및 유지보수 필요
- HTTP MCP와 기능 중복
- Docst 서버가 실행 중이어야 함 (어차피 필요하지만)

---

## 6. FAQ

### Q1. Claude Code는 왜 HTTP를 지원하나요?

**A:** Claude Code는 CLI 도구이자 코드 편집기 통합 도구입니다.
- 원격 MCP 서버 연결 지원 (팀 공유 서버 등)
- 웹 기반 MCP 서버 지원
- stdio보다 HTTP가 더 표준적이고 확장 가능

### Q2. 프록시가 성능에 영향을 주나요?

**A:** 매우 미미합니다.
- 프록시는 단순 메시지 전달 (변환 오버헤드 거의 없음)
- 네트워크: localhost (루프백) - 1ms 미만
- 실제 병목은 MCP 도구 실행 시간 (DB 쿼리, 파일 읽기 등)

### Q3. 프록시 없이 사용할 수 없나요?

**A:** Claude Desktop에서는 불가능합니다.
- Claude Code는 HTTP 직접 지원 - 프록시 불필요
- 웹 기반 클라이언트는 HTTP 직접 지원 - 프록시 불필요
- **Claude Desktop만 stdio 전용** - 프록시 필수

### Q4. 프록시가 API Key를 탈취할 위험은 없나요?

**A:** mcp-client-http는 오픈소스이며 로컬에서만 실행됩니다.
- 코드 검증 가능: https://www.npmjs.com/package/mcp-client-http
- 네트워크 외부로 전송하지 않음
- 로컬 프로세스 간 통신만 수행

---

## 7. 권장 사항

### Claude Desktop 사용자

✅ **mcp-client-http 프록시 사용** (현재 최선의 방법)

```json
{
  "mcpServers": {
    "docst": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-client-http",
        "http://localhost:8342/mcp",
        "--header",
        "X-API-Key: <YOUR_API_KEY>"
      ]
    }
  }
}
```

### Claude Code 사용자

✅ **HTTP 직접 연결** (프록시 불필요)

```bash
claude mcp add docst \
  --url http://localhost:8342/mcp \
  --header "X-API-Key: <YOUR_API_KEY>"
```

또는 `.mcp.json`:

```json
{
  "mcpServers": {
    "docst": {
      "type": "http",
      "url": "http://localhost:8342/mcp",
      "headers": {
        "X-API-Key": "<YOUR_API_KEY>"
      }
    }
  }
}
```

---

## 8. 참고 자료

- [MCP Specification - Transports](https://modelcontextprotocol.io/specification#transports)
- [mcp-client-http NPM Package](https://www.npmjs.com/package/mcp-client-http)
- [Claude Desktop MCP Configuration](https://docs.anthropic.com/claude/docs/mcp)
