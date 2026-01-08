# Docst MCP 서버 연결 트러블슈팅

## 개요

Claude Desktop에서 Docst MCP 서버 연결 시 발생할 수 있는 문제와 해결 방법을 정리합니다.

---

## 문제 1: Windows 경로 공백 문제

### 증상

Claude Desktop 로그에 다음과 같은 오류 발생:

```
'C:\Program'은(는) 내부 또는 외부 명령, 실행할 수 있는 프로그램, 또는 배치 파일이 아닙니다.
```

또는 인코딩 문제로 깨진 한글:

```
'C:\Program'  (  )       Ǵ   ܺ     ,            ִ     α׷ ,  Ǵ
  ġ         ƴմϴ .
```

### 원인

Claude Desktop이 `npx` 명령을 실행할 때 Windows의 `cmd.exe`를 통해 실행합니다:

```
C:\WINDOWS\System32\cmd.exe /C C:\Program Files\nodejs\npx.cmd -y mcp-remote ...
```

`C:\Program Files\nodejs\npx.cmd` 경로에 **공백**이 포함되어 있어, `cmd.exe`가 이를 `C:\Program`과 `Files\nodejs\npx.cmd`로 분리하여 해석합니다.

### 해결 방법

#### 방법 1: Node.js를 공백 없는 경로에 재설치 (권장)

1. 기존 Node.js 제거
2. 공백이 없는 경로에 Node.js 재설치
   - 권장 경로: `C:\nodejs` 또는 `C:\tools\nodejs`
3. 환경 변수 PATH 업데이트 확인
4. Claude Desktop 재시작

#### 방법 2: mcp-remote 전역 설치 후 직접 실행

1. mcp-remote를 전역으로 설치:
   ```bash
   npm install -g mcp-remote
   ```

2. 설치 경로 확인:
   ```bash
   npm root -g
   # 예: C:\Users\username\AppData\Roaming\npm\node_modules
   ```

3. Claude Desktop 설정을 직접 실행 파일로 변경:
   ```json
   {
     "mcpServers": {
       "docst": {
         "command": "C:\\Users\\username\\AppData\\Roaming\\npm\\mcp-remote.cmd",
         "args": [
           "http://localhost:8342/mcp/stream",
           "--header",
           "X-API-Key: docst_ak_xxxxxxxxxxxxxxxxxxxx"
         ]
       }
     }
   }
   ```

   > **Note**: `username`을 실제 Windows 사용자명으로 변경하세요.

#### 방법 3: 환경 변수로 npm 전역 경로 변경

1. 공백 없는 npm 전역 디렉토리 설정:
   ```bash
   npm config set prefix "C:\npm-global"
   ```

2. PATH 환경 변수에 `C:\npm-global` 추가

3. mcp-remote 설치:
   ```bash
   npm install -g mcp-remote
   ```

4. Claude Desktop 설정:
   ```json
   {
     "mcpServers": {
       "docst": {
         "command": "C:\\npm-global\\mcp-remote.cmd",
         "args": [
           "http://localhost:8342/mcp/stream",
           "--header",
           "X-API-Key: docst_ak_xxxxxxxxxxxxxxxxxxxx"
         ]
       }
     }
   }
   ```

#### 방법 4: PowerShell 사용

`cmd.exe` 대신 PowerShell을 통해 실행:

```json
{
  "mcpServers": {
    "docst": {
      "command": "powershell",
      "args": [
        "-Command",
        "& 'C:\\Program Files\\nodejs\\npx.cmd' -y mcp-remote http://localhost:8342/mcp/stream --header 'X-API-Key: docst_ak_xxxxxxxxxxxxxxxxxxxx'"
      ]
    }
  }
}
```

---

## 문제 2: 서버 연결 실패 (Connection refused)

### 증상

```
Error: Connection refused
```

또는 Claude Desktop에서 "Server disconnected" 표시

### 원인

- Docst 백엔드 서버가 실행되지 않음
- 포트 번호 불일치

### 해결 방법

1. 백엔드 서버 상태 확인:
   ```bash
   curl http://localhost:8342/actuator/health
   ```

2. 서버 실행:
   ```bash
   cd backend && ./gradlew bootRun
   ```
   또는 Docker Compose:
   ```bash
   docker-compose up -d
   ```

3. 포트 확인 (`application.yml`에서 `server.port` 확인)

---

## 문제 3: 인증 오류 (Unauthorized)

### 증상

```json
{"error": {"code": -32603, "message": "Unauthorized"}}
```

### 원인

- API Key가 유효하지 않음
- API Key가 만료됨
- 인증 헤더 형식 오류

### 해결 방법

1. **새 API Key 발급**:
   - Docst 웹 UI 로그인 (`http://localhost:3000`)
   - Settings → API Keys 메뉴
   - "Create API Key" 클릭
   - 생성된 키 복사 (다시 볼 수 없음)

2. **Claude Desktop 설정 업데이트**:
   ```json
   {
     "mcpServers": {
       "docst": {
         "command": "...",
         "args": [
           "...",
           "--header",
           "X-API-Key: docst_ak_새로운키값"
         ]
       }
     }
   }
   ```

3. **API Key 유효성 테스트**:
   ```bash
   curl -X POST http://localhost:8342/mcp \
     -H "Content-Type: application/json" \
     -H "X-API-Key: docst_ak_xxxxxxxxxxxxxxxxxxxx" \
     -d '{"jsonrpc": "2.0", "id": "1", "method": "tools/list", "params": {}}'
   ```

---

## 문제 4: MCP 서버가 tools/list에 응답하지 않음

### 증상

Claude Desktop에서 도구 목록이 표시되지 않음

### 원인

- MCP 엔드포인트 구현 오류
- JSON-RPC 응답 형식 불일치

### 해결 방법

1. **직접 API 테스트**:
   ```bash
   curl -X POST http://localhost:8342/mcp \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc": "2.0", "id": "1", "method": "tools/list", "params": {}}'
   ```

2. **예상 응답 형식**:
   ```json
   {
     "jsonrpc": "2.0",
     "id": "1",
     "result": {
       "tools": [
         {
           "name": "list_documents",
           "description": "...",
           "inputSchema": {...}
         }
       ]
     }
   }
   ```

3. 백엔드 로그 확인하여 오류 추적

---

## 문제 5: mcp-remote 패키지 오류

### 증상

```
npm ERR! code ENOTFOUND
npm ERR! syscall getaddrinfo
```

또는 패키지를 찾을 수 없음

### 해결 방법

1. **npm 캐시 정리**:
   ```bash
   npm cache clean --force
   ```

2. **네트워크 확인**:
   ```bash
   npm ping
   ```

3. **대체 레지스트리 사용**:
   ```bash
   npm install -g mcp-remote --registry https://registry.npmmirror.com
   ```

---

## 문제 6: HTTP 응답에 추가 데이터가 붙는 문제

### 증상

curl로 MCP 엔드포인트 테스트 시 응답에 두 개의 JSON이 붙어서 반환됨:

```
{"jsonrpc":"2.0","id":"1","result":{...}}{"timestamp":"...","status":200,"error":"OK","path":"/mcp"}
```

그리고 curl 에러 발생:

```
curl: (18) transfer closed with outstanding read data remaining
```

Claude Desktop에서는 "Server disconnected" 표시

백엔드 로그에 다음 예외 발생:

```
org.hibernate.LazyInitializationException: Could not initialize proxy [com.docst.domain.User#...] - no session
    at com.docst.domain.User$HibernateProxy.toString(Unknown Source)
    at AbstractAuthenticationToken.getName()
    at FrameworkServlet.getUsernameForRequest()
    at FrameworkServlet.publishRequestHandledEvent()
```

### 원인

1. **Hibernate LazyInitializationException**
   - `ApiKeyAuthenticationFilter`가 `User` Hibernate 프록시 객체를 Authentication의 principal로 설정
   - 응답 반환 후 Spring MVC가 요청 완료 이벤트 발행 시 `authentication.getName()` 호출
   - 이때 `User.toString()`이 호출되는데, Hibernate 세션이 이미 닫혀서 예외 발생
   - 예외가 응답 후 처리 중 발생하여 Spring Boot 에러 핸들러가 추가 응답을 씀

2. **Content-Length 불일치**
   - 정상 응답이 먼저 전송된 후 에러 메타데이터가 추가로 전송됨
   - HTTP 연결이 비정상 종료
   - `mcp-remote`가 JSON 파싱 실패하여 크래시

### 해결 방법

**백엔드 코드 수정이 필요합니다.** 두 가지 방법이 있습니다.

---

#### 방법 A: Fetch Join으로 User를 즉시 로드 (권장)

트랜잭션 내에서 User를 미리 로드하여 세션 종료 후에도 접근 가능하게 합니다.

**1. ApiKeyRepository에 Fetch Join 쿼리 추가**

`backend/src/main/java/com/docst/repository/ApiKeyRepository.java`:

```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Query("SELECT ak FROM ApiKey ak JOIN FETCH ak.user WHERE ak.keyHash = :keyHash AND ak.active = true")
Optional<ApiKey> findByKeyHashAndActiveTrueWithUser(@Param("keyHash") String keyHash);
```

**2. ApiKeyService에서 새 쿼리 사용**

`backend/src/main/java/com/docst/service/ApiKeyService.java`:

```java
// 수정 전
Optional<ApiKey> found = apiKeyRepository.findByKeyHashAndActiveTrue(keyHash);

// 수정 후
Optional<ApiKey> found = apiKeyRepository.findByKeyHashAndActiveTrueWithUser(keyHash);
```

---

#### 방법 B: UserPrincipal DTO 사용

Hibernate 프록시 대신 순수 DTO를 사용하여 프록시 접근 문제를 방지합니다.

**1. UserPrincipal DTO 생성**

`backend/src/main/java/com/docst/auth/UserPrincipal.java`:

```java
public record UserPrincipal(
        UUID id,
        String email,
        String displayName
) implements Principal {

    public static UserPrincipal from(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getDisplayName()
        );
    }

    @Override
    public String getName() {
        return email;
    }
}
```

**2. ApiKeyAuthenticationFilter 수정**

`User` 대신 `UserPrincipal`을 principal로 사용:

```java
// 수정 전
UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());

// 수정 후
UserPrincipal principal = UserPrincipal.from(user);
UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
```

**3. JwtAuthenticationFilter도 동일하게 수정**

JWT 인증에서도 같은 문제가 발생할 수 있으므로 함께 수정.

---

#### 최종 해결: 방법 A + B 조합

**방법 A**(Fetch Join)와 **방법 B**(UserPrincipal)를 함께 적용하면 가장 안전합니다:
- Fetch Join으로 User 필드를 미리 로드
- UserPrincipal로 Hibernate 프록시 의존성 완전 제거

**4. 테스트

```bash
curl -X POST http://localhost:8342/mcp \
  -H "Content-Type: application/json" \
  -H "X-API-Key: docst_ak_xxx" \
  -d '{"jsonrpc":"2.0","id":"1","method":"initialize","params":{}}'
```

정상 응답 (단일 JSON 객체, curl 에러 없음):
```json
{"jsonrpc":"2.0","id":"1","result":{"protocolVersion":"2024-11-05","serverInfo":{"name":"Docst MCP Server","version":"1.0.0"},"capabilities":{"tools":{"listChanged":false}}}}
```

### 관련 커밋

- `fix(auth): use UserPrincipal DTO to avoid LazyInitializationException in MCP responses`
- `fix(mcp): add fetch join query to eagerly load User in API key authentication`

---

## 문제 7: POST /mcp/stream 지원 안 됨

### 증상

백엔드 로그에 다음 오류 발생:

```
org.springframework.web.HttpRequestMethodNotSupportedException: Request method 'POST' is not supported
```

Claude Desktop에서 "Server disconnected" 표시

### 원인

MCP SSE transport 프로토콜에서 `mcp-remote`는 두 가지 HTTP 메서드를 사용합니다:

| 메서드 | 엔드포인트 | 용도 |
|--------|-----------|------|
| `GET` | `/mcp/stream` | SSE 연결 수립 (서버 → 클라이언트 스트림) |
| `POST` | `/mcp/stream` | JSON-RPC 메시지 전송 (클라이언트 → 서버) |

기존 `McpTransportController`는 GET만 지원하고 POST를 지원하지 않았습니다:

```java
// 기존 코드 - GET만 지원
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(...) { ... }
```

### 해결 방법

**POST /mcp/stream 핸들러 추가**

`backend/src/main/java/com/docst/mcp/McpTransportController.java`:

```java
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
```

### 테스트

```bash
# POST /mcp/stream 테스트
curl -X POST http://localhost:8342/mcp/stream \
  -H "Content-Type: application/json" \
  -H "X-API-Key: docst_ak_xxxxxxxxxxxxxxxxxxxx" \
  -d '{"jsonrpc":"2.0","id":"1","method":"initialize","params":{}}'
```

정상 응답:
```json
{"jsonrpc":"2.0","id":"1","result":{"protocolVersion":"2024-11-05","serverInfo":{"name":"Docst MCP Server","version":"1.0.0"},"capabilities":{"tools":{"listChanged":false}}}}
```

### 관련 커밋

- `fix(mcp): add POST handler for /mcp/stream to support MCP SSE transport`

---

## 문제 8: Java 8 date/time 직렬화 오류 (Instant not supported)

### 증상

MCP 도구 실행 시 다음 오류 발생:

```
com.fasterxml.jackson.databind.exc.InvalidDefinitionException: Java 8 date/time type `java.time.Instant` not supported by default: add Module "com.fasterxml.jackson.datatype:jackson-datatype-jsr310" to enable handling
```

스택 트레이스:

```
at com.docst.mcp.McpTransportController.handleToolsCall(McpTransportController.java:203)
(through reference chain: com.docst.mcp.McpModels$GetDocumentResult["committedAt"])
```

### 원인

`McpTransportController`에서 **직접 `new ObjectMapper()`로 ObjectMapper를 생성**하여 사용:

```java
// 문제의 코드 (McpTransportController.java:31)
private final ObjectMapper objectMapper = new ObjectMapper();  // JavaTimeModule 미등록
```

이 ObjectMapper에는 JavaTimeModule이 등록되지 않아 `java.time.Instant` 타입을 직렬화할 수 없습니다.

`McpModels.java`의 다음 record들이 `Instant` 타입 필드를 가지고 있어 직렬화 실패:
- `GetDocumentResult.committedAt`
- `VersionSummary.committedAt`

### 해결 방법

**Spring Boot 자동 구성 ObjectMapper를 주입받아 사용**

Spring Boot의 자동 구성된 ObjectMapper에는 JavaTimeModule이 기본 등록되어 있습니다.

`backend/src/main/java/com/docst/mcp/McpTransportController.java`:

```java
// 수정 전
private final ObjectMapper objectMapper = new ObjectMapper();

// 수정 후
private final ObjectMapper objectMapper;  // Spring Boot 자동 구성 ObjectMapper 주입
```

`@RequiredArgsConstructor`가 클래스에 적용되어 있으므로, `final` 필드로 선언하면 생성자를 통해 자동 주입됩니다.

### 테스트

```bash
curl -X POST http://localhost:8342/mcp/stream \
  -H "Content-Type: application/json" \
  -H "X-API-Key: docst_ak_xxxxxxxxxxxxxxxxxxxx" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tools/call",
    "params": {
      "name": "get_document",
      "arguments": {"documentId": "your-doc-uuid"}
    }
  }'
```

정상 응답 (Instant가 ISO-8601 형식으로 직렬화됨):

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\"id\":\"...\",\"committedAt\":\"2026-01-08T12:00:00Z\",...}"
      }
    ],
    "isError": false
  }
}
```

### 관련 커밋

- `fix(mcp): use Spring auto-configured ObjectMapper for Java 8 date/time support`

---

## 디버깅 팁

### Claude Desktop 로그 위치

- **Windows**: `%APPDATA%\Claude\logs\`
- **macOS**: `~/Library/Logs/Claude/`
- **Linux**: `~/.config/Claude/logs/`

### 설정 파일 위치

- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`
- **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Linux**: `~/.config/Claude/claude_desktop_config.json`

### 수동 연결 테스트

mcp-remote를 직접 실행하여 연결 테스트:

```bash
npx -y mcp-remote http://localhost:8342/mcp/stream --header "X-API-Key: docst_ak_xxx"
```

정상 작동 시 SSE 연결이 수립되고 JSON-RPC 명령을 입력할 수 있는 인터랙티브 모드로 진입합니다.

---

## 관련 문서

- [MCP 연동 가이드](../mcp/mcp-connect.md)
- [MCP 인증/인가](../mcp/mcp-auth.md)
- [MCP 공식 디버깅 문서](https://modelcontextprotocol.io/docs/tools/debugging)