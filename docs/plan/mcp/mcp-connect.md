# Docst MCP 서버 연동 가이드

## 1. 개요

Docst는 MCP (Model Context Protocol) 서버를 제공하여 AI 에이전트(Claude Desktop, Claude Code 등)가 문서를 조회, 검색, 수정할 수 있도록 합니다.

### 지원 프로토콜

| 프로토콜 | 엔드포인트 | 설명 |
|---------|-----------|------|
| JSON-RPC 2.0 | `POST /mcp` | 메인 MCP 엔드포인트 |
| SSE | `GET /mcp/stream` | 실시간 이벤트 스트리밍 |
| REST API | `POST /mcp/tools/{name}` | 개별 도구 직접 호출 |

### 서버 정보

```json
{
  "protocolVersion": "2024-11-05",
  "serverInfo": {
    "name": "Docst MCP Server",
    "version": "1.0.0"
  },
  "capabilities": {
    "tools": {
      "listChanged": false
    }
  }
}
```

> **Note**: `protocolVersion`은 [MCP 프로토콜 스펙](https://modelcontextprotocol.io/specification) 버전입니다 (날짜 기반).
> `serverInfo.version`은 Docst MCP 서버의 구현 버전입니다.

---

## 2. 사용 가능한 MCP 도구

### READ 도구 (6개)

| 도구 | 설명 | 주요 파라미터 |
|------|------|--------------|
| `list_documents` | 프로젝트/레포지토리의 문서 목록 조회 | `projectId`, `repositoryId`, `pathPrefix`, `type` |
| `get_document` | 문서 내용 조회 (특정 버전 가능) | `documentId`, `commitSha` (선택) |
| `list_document_versions` | 문서의 버전(커밋) 목록 조회 | `documentId` |
| `diff_document` | 두 버전 간 차이 비교 | `documentId`, `fromCommitSha`, `toCommitSha` |
| `search_documents` | 문서 검색 (키워드/시맨틱/하이브리드) | `projectId`, `query`, `mode`, `topK` |
| `sync_repository` | 레포지토리 동기화 실행 | `repositoryId`, `branch` |

### WRITE 도구 (3개)

| 도구 | 설명 | 주요 파라미터 |
|------|------|--------------|
| `create_document` | 새 문서 생성 | `repositoryId`, `path`, `content`, `message`, `createCommit` |
| `update_document` | 기존 문서 수정 | `documentId`, `content`, `message`, `createCommit` |
| `push_to_remote` | 로컬 커밋을 원격에 푸시 | `repositoryId`, `branch` |

### 검색 모드 (search_documents)

| 모드 | 설명 |
|------|------|
| `keyword` | PostgreSQL tsvector 기반 키워드 검색 |
| `semantic` | pgvector 기반 벡터 유사도 검색 |
| `hybrid` | 키워드 + 시맨틱 결합 (RRF 알고리즘) |

---

## 3. Claude Desktop 연동

### 설정 파일 위치

- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`
- **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Linux**: `~/.config/Claude/claude_desktop_config.json`

### 설정 예시 (API Key 권장)

```json
{
  "mcpServers": {
    "docst": {
      "url": "http://localhost:8342/mcp",
      "transport": "http",
      "headers": {
        "X-API-Key": "<YOUR_API_KEY>"
      }
    }
  }
}
```

> **Tip**: API Key는 Settings → API Keys에서 발급받으세요. 자세한 방법은 [6. 인증 관리](#6-인증-관리) 참조.

---

## 4. Claude Code 연동

### 프로젝트 단위 설정 (.mcp.json)

프로젝트 루트에 `.mcp.json` 파일 생성:

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

### 전역 설정

Claude Code 설정에서 MCP 서버 추가:

```bash
claude mcp add docst --url http://localhost:8342/mcp --header "X-API-Key: <YOUR_API_KEY>"
```

> **Tip**: API Key는 Settings → API Keys에서 발급받으세요. 자세한 방법은 [6. 인증 관리](#6-인증-관리) 참조.

---

## 5. JSON-RPC 2.0 사용 예시

### 도구 목록 조회 (tools/list)

```bash
curl -X POST http://localhost:8342/mcp \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN>" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tools/list",
    "params": {}
  }'
```

### 문서 검색 (tools/call)

```bash
curl -X POST http://localhost:8342/mcp \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN>" \
  -d '{
    "jsonrpc": "2.0",
    "id": "2",
    "method": "tools/call",
    "params": {
      "name": "search_documents",
      "arguments": {
        "projectId": "550e8400-e29b-41d4-a716-446655440000",
        "query": "authentication",
        "mode": "hybrid",
        "topK": 10
      }
    }
  }'
```

### 문서 조회 (tools/call)

```bash
curl -X POST http://localhost:8342/mcp \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN>" \
  -d '{
    "jsonrpc": "2.0",
    "id": "3",
    "method": "tools/call",
    "params": {
      "name": "get_document",
      "arguments": {
        "documentId": "550e8400-e29b-41d4-a716-446655440001"
      }
    }
  }'
```

### 문서 수정 (tools/call)

```bash
curl -X POST http://localhost:8342/mcp \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN>" \
  -d '{
    "jsonrpc": "2.0",
    "id": "4",
    "method": "tools/call",
    "params": {
      "name": "update_document",
      "arguments": {
        "documentId": "550e8400-e29b-41d4-a716-446655440001",
        "content": "# Updated Content\n\nNew content here...",
        "message": "Update documentation",
        "createCommit": true
      }
    }
  }'
```

---

## 6. 인증 관리

### 현재 상태

| 환경 | 인증 상태 | 비고 |
|------|----------|------|
| 개발 | 공개 (permitAll) | 인증 없이 접근 가능 |
| 프로덕션 | **인증 필요** | API Key 또는 JWT 토큰 필수 |

> **경고**: 현재 `/mcp/**` 엔드포인트는 `permitAll` 상태입니다. 프로덕션 환경에서는 반드시 인증을 활성화해야 합니다.

### 인증 방식 비교

| 방식 | 만료 | 용도 | 권장 |
|------|------|------|------|
| **API Key** | 없음 (수동 폐기) | MCP 클라이언트 (Claude Desktop, Claude Code) | ✅ 권장 |
| JWT Token | 24시간 | 웹 UI, 일시적 API 호출 | 웹 전용 |

---

### 방식 1: API Key (권장)

MCP 클라이언트(Claude Desktop, Claude Code)에는 **API Key 방식을 권장**합니다.
API Key는 만료되지 않으므로 한 번 설정하면 계속 사용할 수 있습니다.

#### API Key 발급

1. **웹 UI에서 발급**
   - Docst 웹 UI 로그인 (`http://localhost:3000`)
   - **Settings → API Keys** 메뉴 이동
   - **"Create API Key"** 클릭
   - 이름 입력 (예: "Claude Desktop")
   - 생성된 키 복사 (이후 다시 볼 수 없음)

2. **API로 발급** (관리자)
   ```bash
   curl -X POST http://localhost:8342/api/auth/api-keys \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer <JWT_TOKEN>" \
     -d '{"name": "Claude Desktop", "expiresAt": null}'
   ```

   응답:
   ```json
   {
     "id": "550e8400-e29b-41d4-a716-446655440000",
     "name": "Claude Desktop",
     "key": "docst_ak_xxxxxxxxxxxxxxxxxxxx",
     "createdAt": "2025-01-04T12:00:00Z"
   }
   ```

#### API Key 사용

**헤더 방식**:
```http
X-API-Key: docst_ak_xxxxxxxxxxxxxxxxxxxx
```

또는 Bearer 형식:
```http
Authorization: Bearer docst_ak_xxxxxxxxxxxxxxxxxxxx
```

#### Claude Desktop 설정 예시 (API Key)

```json
{
  "mcpServers": {
    "docst": {
      "url": "http://localhost:8342/mcp",
      "transport": "http",
      "headers": {
        "X-API-Key": "docst_ak_xxxxxxxxxxxxxxxxxxxx"
      }
    }
  }
}
```

#### API Key 관리

| 작업 | 엔드포인트 |
|------|-----------|
| 목록 조회 | `GET /api/auth/api-keys` |
| 생성 | `POST /api/auth/api-keys` |
| 폐기 | `DELETE /api/auth/api-keys/{id}` |

---

### 방식 2: JWT Bearer Token

웹 UI나 일시적 API 호출에 사용합니다.

**헤더 방식**:
```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

**쿼리 파라미터 방식** (SSE 연결용):
```
GET /mcp/stream?token=eyJhbGciOiJIUzI1NiIs...
```

#### JWT 토큰 특성

| 항목 | 값 |
|------|-----|
| 알고리즘 | HMAC-SHA |
| 만료 시간 | 24시간 (86400초) |
| 페이로드 | userId (subject), email (claim) |

#### JWT 토큰 발급 방법

**방법 1: 웹 UI에서 복사**

1. Docst 웹 UI 로그인 (`http://localhost:3000`)
2. 개발자 도구 열기 (F12)
3. **Application** → **Local Storage** → `http://localhost:3000`
4. `auth_token` 값 복사

**방법 2: API 직접 호출**

```bash
curl -X POST http://localhost:8342/api/auth/local/login \
  -H "Content-Type: application/json" \
  -d '{"email": "your@email.com", "password": "your-password"}'
```

응답:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "your@email.com",
    "displayName": "Your Name"
  }
}
```

#### 토큰 갱신

JWT 토큰이 만료되면 다시 로그인하여 새 토큰을 발급받아야 합니다.

---

## 7. 프로젝트 접근 권한

### 역할 계층

```
OWNER > ADMIN > EDITOR > VIEWER
```

### 도구별 최소 권한

| 도구 | 최소 권한 | 설명 |
|------|----------|------|
| `list_documents` | VIEWER | 문서 목록 조회 |
| `get_document` | VIEWER | 문서 내용 조회 |
| `list_document_versions` | VIEWER | 버전 목록 조회 |
| `diff_document` | VIEWER | 버전 비교 |
| `search_documents` | VIEWER | 문서 검색 |
| `sync_repository` | ADMIN | 레포지토리 동기화 |
| `create_document` | EDITOR | 문서 생성 |
| `update_document` | EDITOR | 문서 수정 |
| `push_to_remote` | ADMIN | 원격 푸시 |

### 권한 확인 방법

MCP 도구 호출 시 서버에서 자동으로 권한을 검증합니다. 권한이 없는 경우:

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "error": {
    "code": -32603,
    "message": "Permission denied: EDITOR role required"
  }
}
```

---

## 8. 응답 형식

### 성공 응답

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "result": {
    "documents": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440001",
        "path": "docs/README.md",
        "title": "README",
        "docType": "MD",
        "latestCommitSha": "abc123..."
      }
    ]
  }
}
```

### 오류 응답

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "error": {
    "code": -32602,
    "message": "Invalid params: projectId is required"
  }
}
```

### JSON-RPC 에러 코드

| 코드 | 의미 |
|------|------|
| -32600 | Invalid Request |
| -32601 | Method not found |
| -32602 | Invalid params |
| -32603 | Internal error |

---

## 9. SSE 스트리밍

실시간 이벤트를 수신하려면 SSE 엔드포인트에 연결합니다.

### 연결

```bash
curl -N http://localhost:8342/mcp/stream?token=<JWT_TOKEN>
```

### 이벤트 형식

```
event: connected
data: {"clientId": "abc-123"}

event: sync_progress
data: {"repositoryId": "...", "progress": 50}

event: sync_completed
data: {"repositoryId": "...", "status": "success"}
```

---

## 10. 보안 권장 사항 (프로덕션)

### 필수 조치

1. **인증 활성화**: `SecurityConfig.java`에서 `/mcp/**`를 `authenticated()`로 변경
2. **HTTPS 사용**: 토큰 노출 방지를 위해 TLS 적용
3. **토큰 만료 시간 단축**: 필요시 24시간 → 1시간으로 조정

### 권장 조치

1. **Rate Limiting**: 과도한 요청 방지
2. **IP 화이트리스트**: 신뢰할 수 있는 클라이언트만 허용
3. **감사 로그**: MCP 도구 호출 기록 저장
4. **프로젝트별 API Key**: 각 프로젝트마다 별도 API Key 발급 고려

---

## 11. 문제 해결

### 연결 실패

```
Error: Connection refused
```
→ Docst 백엔드 서버가 실행 중인지 확인 (`http://localhost:8342/actuator/health`)

### 인증 오류

```json
{"error": {"code": -32603, "message": "Unauthorized"}}
```
→ JWT 토큰이 만료되었거나 유효하지 않음. 다시 로그인하여 토큰 갱신

### 권한 오류

```json
{"error": {"code": -32603, "message": "Permission denied"}}
```
→ 해당 프로젝트에 대한 적절한 역할이 없음. 프로젝트 관리자에게 권한 요청

### 도구 찾기 실패

```json
{"error": {"code": -32601, "message": "Method not found: unknown_tool"}}
```
→ 지원되지 않는 도구 이름. `tools/list`로 사용 가능한 도구 확인

---

## 부록: 도구 입력 스키마

### list_documents

```typescript
interface ListDocumentsInput {
  repositoryId?: string;  // UUID
  projectId?: string;     // UUID
  pathPrefix?: string;    // 예: "docs/"
  type?: string;          // "MD" | "ADOC" | "OPENAPI" | "ADR" | "OTHER"
}
```

### get_document

```typescript
interface GetDocumentInput {
  documentId: string;     // UUID (필수)
  commitSha?: string;     // 특정 버전 조회 시
}
```

### search_documents

```typescript
interface SearchDocumentsInput {
  projectId: string;      // UUID (필수)
  query: string;          // 검색어 (필수)
  mode?: string;          // "keyword" | "semantic" | "hybrid" (기본: "keyword")
  topK?: number;          // 결과 개수 (기본: 10)
}
```

### create_document

```typescript
interface CreateDocumentInput {
  repositoryId: string;   // UUID (필수)
  path: string;           // 파일 경로 (필수), 예: "docs/new-doc.md"
  content: string;        // 문서 내용 (필수)
  message?: string;       // 커밋 메시지
  branch?: string;        // 대상 브랜치 (기본: "main")
  createCommit?: boolean; // 즉시 커밋 여부 (기본: true)
}
```

### update_document

```typescript
interface UpdateDocumentInput {
  documentId: string;     // UUID (필수)
  content: string;        // 수정된 내용 (필수)
  message?: string;       // 커밋 메시지
  branch?: string;        // 대상 브랜치 (기본: "main")
  createCommit?: boolean; // 즉시 커밋 여부 (기본: true)
}
```

### push_to_remote

```typescript
interface PushToRemoteInput {
  repositoryId: string;   // UUID (필수)
  branch?: string;        // 푸시할 브랜치 (기본: "main")
}
```
