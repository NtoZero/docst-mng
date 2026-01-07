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
C:\WINDOWS\System32\cmd.exe /C C:\Program Files\nodejs\npx.cmd -y mcp-client-http ...
```

`C:\Program Files\nodejs\npx.cmd` 경로에 **공백**이 포함되어 있어, `cmd.exe`가 이를 `C:\Program`과 `Files\nodejs\npx.cmd`로 분리하여 해석합니다.

### 해결 방법

#### 방법 1: Node.js를 공백 없는 경로에 재설치 (권장)

1. 기존 Node.js 제거
2. 공백이 없는 경로에 Node.js 재설치
   - 권장 경로: `C:\nodejs` 또는 `C:\tools\nodejs`
3. 환경 변수 PATH 업데이트 확인
4. Claude Desktop 재시작

#### 방법 2: mcp-client-http 전역 설치 후 직접 실행

1. mcp-client-http를 전역으로 설치:
   ```bash
   npm install -g mcp-client-http
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
         "command": "C:\\Users\\username\\AppData\\Roaming\\npm\\mcp-client-http.cmd",
         "args": [
           "http://localhost:8342/mcp",
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

3. mcp-client-http 설치:
   ```bash
   npm install -g mcp-client-http
   ```

4. Claude Desktop 설정:
   ```json
   {
     "mcpServers": {
       "docst": {
         "command": "C:\\npm-global\\mcp-client-http.cmd",
         "args": [
           "http://localhost:8342/mcp",
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
        "& 'C:\\Program Files\\nodejs\\npx.cmd' -y mcp-client-http http://localhost:8342/mcp --header 'X-API-Key: docst_ak_xxxxxxxxxxxxxxxxxxxx'"
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

## 문제 5: mcp-client-http 패키지 오류

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
   npm install -g mcp-client-http --registry https://registry.npmmirror.com
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
   - `mcp-client-http`가 JSON 파싱 실패하여 크래시

### 해결 방법

**백엔드 코드 수정이 필요합니다.**

#### 1. UserPrincipal DTO 생성

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

#### 2. ApiKeyAuthenticationFilter 수정

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

#### 3. JwtAuthenticationFilter도 동일하게 수정

JWT 인증에서도 같은 문제가 발생할 수 있으므로 함께 수정.

#### 4. 테스트

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

mcp-client-http를 직접 실행하여 연결 테스트:

```bash
npx -y mcp-client-http http://localhost:8342/mcp --header "X-API-Key: docst_ak_xxx"
```

정상 작동 시 JSON-RPC 명령을 입력할 수 있는 인터랙티브 모드로 진입합니다.

---

## 관련 문서

- [MCP 연동 가이드](../mcp/mcp-connect.md)
- [MCP 인증/인가](../mcp/mcp-auth.md)
- [MCP 공식 디버깅 문서](https://modelcontextprotocol.io/docs/tools/debugging)