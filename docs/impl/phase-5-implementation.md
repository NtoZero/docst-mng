# Phase 5: LLM 통합 및 Playground - 구현 완료 보고서

> **작성일**: 2026-01-01
> **상태**: ✅ MVP 완료
> **빌드**: Backend ✅ | Frontend ✅

---

## 목차

- [개요](#개요)
- [백엔드 구현](#백엔드-구현)
- [프론트엔드 구현](#프론트엔드-구현)
- [구현된 파일 목록](#구현된-파일-목록)
- [빌드 및 테스트](#빌드-및-테스트)
- [사용 방법](#사용-방법)
- [남은 작업 (TODO)](#남은-작업-todo)
- [Phase 6 계획](#phase-6-계획)

---

## 개요

Phase 5에서는 MCP(Model Context Protocol)를 통한 문서 쓰기 기능과 이를 테스트할 수 있는 Playground UI를 구현했습니다.

### 주요 성과

- ✅ MCP Tools 확장 (READ 6개 + WRITE 3개)
- ✅ JSON-RPC 2.0 프로토콜 구현 (HTTP Streamable + SSE)
- ✅ Git 쓰기 작업 (파일 생성/수정, 커밋, 푸시)
- ✅ 권한 시스템 통합
- ✅ Playground UI (MVP)
- ✅ MCP Client 라이브러리

---

## 백엔드 구현

### 1. 핵심 아키텍처

#### McpTool Enum 기반 도구 관리

**파일**: `backend/src/main/java/com/docst/mcp/McpTool.java`

```java
public enum McpTool {
    // READ 도구 (6개)
    LIST_DOCUMENTS("list_documents", "List documents...", ListDocumentsInput.class, ToolCategory.READ),
    GET_DOCUMENT("get_document", "Get document content...", GetDocumentInput.class, ToolCategory.READ),
    LIST_DOCUMENT_VERSIONS("list_document_versions", "List versions...", ListDocumentVersionsInput.class, ToolCategory.READ),
    DIFF_DOCUMENT("diff_document", "Compare versions...", DiffDocumentInput.class, ToolCategory.READ),
    SEARCH_DOCUMENTS("search_documents", "Search documents...", SearchDocumentsInput.class, ToolCategory.READ),
    SYNC_REPOSITORY("sync_repository", "Trigger sync...", SyncRepositoryInput.class, ToolCategory.READ),

    // WRITE 도구 (3개)
    CREATE_DOCUMENT("create_document", "Create new document...", CreateDocumentInput.class, ToolCategory.WRITE),
    UPDATE_DOCUMENT("update_document", "Update document...", UpdateDocumentInput.class, ToolCategory.WRITE),
    PUSH_TO_REMOTE("push_to_remote", "Push to remote...", PushToRemoteInput.class, ToolCategory.WRITE);
}
```

**특징**:
- 타입 안전한 도구 이름 관리
- 자동 JSON Schema 생성
- READ/WRITE 카테고리 분류

#### McpToolDispatcher 패턴

**파일**: `backend/src/main/java/com/docst/mcp/McpToolDispatcher.java`

```java
@Service
public class McpToolDispatcher {
    private final Map<McpTool, McpToolHandler<?, ?>> handlers = new HashMap<>();

    @PostConstruct
    public void registerHandlers() {
        registerHandler(McpTool.LIST_DOCUMENTS, this::handleListDocuments);
        registerHandler(McpTool.CREATE_DOCUMENT, this::handleCreateDocument);
        // ... 9개 도구 등록
    }

    public <T> McpResponse<T> dispatch(String toolName, Object input) {
        McpTool tool = McpTool.fromName(toolName).orElseThrow(...);
        McpToolHandler<Object, T> handler = (McpToolHandler<Object, T>) handlers.get(tool);
        T result = handler.handle(convertedInput);
        return McpResponse.success(result);
    }
}
```

**특징**:
- Enum 기반 자동 라우팅
- `@PostConstruct`로 핸들러 자동 등록
- 통합 예외 처리

### 2. Git 쓰기 작업

#### GitWriteService

**파일**: `backend/src/main/java/com/docst/git/GitWriteService.java`

**주요 메서드**:

```java
// 1. 파일 쓰기 (부모 디렉토리 자동 생성)
public void writeFile(Path filePath, String content) throws IOException

// 2. Git 커밋 (Bot author + "by @username")
public String commitFile(
    Repository repo,
    String relativePath,
    String message,
    String branch,
    String username
) throws GitAPIException, IOException

// 3. 원격 푸시
public void pushToRemote(Repository repo, String branch) throws GitAPIException, IOException
```

**커밋 메시지 형식**:
```
Update README.md

by @john
```

**Author 정보**:
- Name: `Docst Bot`
- Email: `bot@docst.com`

### 3. 문서 쓰기 비즈니스 로직

#### DocumentWriteService

**파일**: `backend/src/main/java/com/docst/service/DocumentWriteService.java`

**주요 메서드**:

```java
// 1. 문서 생성
public CreateDocumentResult createDocument(
    CreateDocumentInput input,
    UUID userId,
    String username
) {
    // 권한 검사 (EDITOR 이상)
    permissionService.requireWriteRepository(...);

    // 파일 쓰기
    gitWriteService.writeFile(filePath, content);

    // 선택적 커밋
    if (createCommit) {
        String commitSha = gitWriteService.commitFile(...);

        // 자동 동기화 (Chunk & Embedding 포함)
        gitSyncService.syncRepository(...);
    }
}

// 2. 문서 수정
public UpdateDocumentResult updateDocument(...)

// 3. 원격 푸시
public void pushToRemote(UUID repositoryId, String branch)
```

**플로우**:
1. 권한 검사
2. 파일 쓰기
3. 선택적 커밋
4. 자동 DB 동기화
5. Chunk & Embedding 업데이트

### 4. JSON-RPC 2.0 프로토콜

#### JsonRpcModels

**파일**: `backend/src/main/java/com/docst/mcp/JsonRpcModels.java`

```java
public record JsonRpcRequest(String jsonrpc, Object id, String method, Object params);
public record JsonRpcResponse(String jsonrpc, Object id, Object result, JsonRpcError error);
public record JsonRpcError(int code, String message, Object data);
```

**표준 오류 코드**:
- `-32700`: Parse error
- `-32600`: Invalid request
- `-32601`: Method not found
- `-32602`: Invalid params
- `-32603`: Internal error

#### McpTransportController

**파일**: `backend/src/main/java/com/docst/mcp/McpTransportController.java`

**엔드포인트**:

```java
// 1. JSON-RPC (HTTP Streamable)
@PostMapping("/mcp")
public ResponseEntity<JsonRpcResponse> handleJsonRpc(@RequestBody JsonRpcRequest request)

// 2. SSE 스트림
@GetMapping("/mcp/stream")
public SseEmitter stream(@RequestParam(required = false) String clientId)
```

**지원 메서드**:
- `tools/list`: 도구 목록 조회
- `tools/call`: 도구 호출
- `initialize`: MCP 서버 초기화
- `ping`: 헬스 체크

### 5. 권한 시스템

#### PermissionService

**파일**: `backend/src/main/java/com/docst/service/PermissionService.java`

**역할 계층**:
```
OWNER > ADMIN > EDITOR > VIEWER
```

**주요 메서드**:

```java
// 1. 역할 확인
public boolean hasRole(UUID projectId, UUID userId, ProjectRole requiredRole)

// 2. 레포지토리 쓰기 권한 (EDITOR 이상)
public boolean canWriteRepository(UUID repositoryId, UUID userId)

// 3. 문서 수정 권한
public boolean canWriteDocument(UUID documentId, UUID userId)

// 4. 권한 검사 + 예외 발생
public void requireWriteRepository(UUID repositoryId, UUID userId, String action)
```

**통합**:
- `DocumentWriteService.createDocument()`: EDITOR 이상 필요
- `DocumentWriteService.updateDocument()`: 문서별 권한 확인

### 6. MCP Models 확장

**파일**: `backend/src/main/java/com/docst/mcp/McpModels.java`

**추가된 모델**:

```java
// create_document
public record CreateDocumentInput(
    UUID repositoryId,
    String path,
    String content,
    String message,
    String branch,
    Boolean createCommit
)

public record CreateDocumentResult(
    UUID documentId,
    String path,
    String newCommitSha,
    boolean committed,
    String message
)

// update_document
public record UpdateDocumentInput(...)
public record UpdateDocumentResult(...)

// push_to_remote
public record PushToRemoteInput(UUID repositoryId, String branch)
public record PushToRemoteResult(...)
```

---

## 프론트엔드 구현

### 1. MCP Protocol & Client

#### MCP Protocol 클라이언트

**파일**: `frontend/lib/mcp-protocol.ts`

```typescript
export class McpProtocol {
  // JSON-RPC 요청
  private async sendJsonRpc<T>(method: string, params?: unknown): Promise<T>

  // MCP 초기화
  async initialize(): Promise<ServerInfo>

  // 도구 목록 조회
  async listTools(): Promise<McpToolsListResult>

  // 도구 호출
  async callTool<T>(toolName: string, args: Record<string, unknown>): Promise<T>

  // SSE 스트림 연결
  connectStream(onEvent: (eventName: string, data: unknown) => void): void
}
```

**Transport 지원**:
- ✅ HTTP Streamable (POST /mcp)
- ✅ SSE (GET /mcp/stream)

#### MCP Client

**파일**: `frontend/lib/mcp-client.ts`

```typescript
export class McpClient {
  // READ 도구
  async listDocuments(params: {...}): Promise<McpResponse<{documents: [...]}>>
  async getDocument(params: {...}): Promise<McpResponse<{...}>>
  async searchDocuments(params: {...}): Promise<McpResponse<{results: [...]}>>

  // WRITE 도구
  async createDocument(params: {...}): Promise<McpResponse<{...}>>
  async updateDocument(params: {...}): Promise<McpResponse<{...}>>
  async pushToRemote(params: {...}): Promise<McpResponse<{...}>>
}
```

### 2. TypeScript 타입

**파일**: `frontend/lib/types.ts`

**추가된 타입**:

```typescript
// JSON-RPC 2.0
export interface JsonRpcRequest {
  jsonrpc: '2.0';
  id: string | number;
  method: string;
  params?: unknown;
}

export interface JsonRpcResponse<T = unknown> {
  jsonrpc: '2.0';
  id: string | number;
  result?: T;
  error?: JsonRpcError;
}

// Chat Messages
export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: Date;
  toolCalls?: ToolCall[];
  isError?: boolean;
}

export interface ToolCall {
  toolName: string;
  input: Record<string, unknown>;
  output?: unknown;
  error?: string;
  duration?: number;
}
```

### 3. UI 컴포넌트

#### MessageInput

**파일**: `frontend/components/playground/message-input.tsx`

**기능**:
- Textarea + Send 버튼
- Enter: 전송
- Shift+Enter: 줄바꿈
- 로딩 중 비활성화

#### MessageItem

**파일**: `frontend/components/playground/message-item.tsx`

**기능**:
- 유저/어시스턴트 구분 (아바타, 배경색)
- 타임스탬프 (HH:mm:ss)
- 에러 상태 표시
- **Tool Call 시각화**:
  - 도구 이름 + 실행 시간
  - Input (JSON)
  - Output (JSON)
  - Error (있을 경우)

#### MessageList

**파일**: `frontend/components/playground/message-list.tsx`

**기능**:
- 메시지 목록 렌더링
- 자동 스크롤 (새 메시지 도착 시)
- 빈 상태 표시

#### ChatInterface

**파일**: `frontend/components/playground/chat-interface.tsx`

**구조**:
```
┌─────────────────────────┐
│   MessageList (flex-1)  │
├─────────────────────────┤
│   MessageInput          │
└─────────────────────────┘
```

### 4. Hooks

#### useMcpTools

**파일**: `frontend/hooks/use-mcp-tools.ts`

**기능**:
- 메시지 상태 관리
- 간단한 명령어 파싱 (ping, list tools)
- MCP 도구 직접 호출
- Tool Call 정보 수집 (duration 포함)

**Phase 5 MVP 제한사항**:
- LLM 통합 없음 (하드코딩된 명령어만 지원)
- Phase 6에서 실제 LLM 연동 예정

### 5. Playground 페이지

**파일**: `frontend/app/[locale]/playground/page.tsx`

**레이아웃**:
```
┌────────────────────────────────────────────────┐
│  Header (Title + Clear button)                 │
├─────────────────────────┬──────────────────────┤
│                         │                      │
│  Chat Interface         │  Quick Commands      │
│  (MessageList + Input)  │  - Ping Server       │
│                         │  - List Tools        │
│                         │                      │
│                         │  Available Commands  │
│                         │  (Help text)         │
│                         │                      │
└─────────────────────────┴──────────────────────┘
```

**기능**:
- 메시지 히스토리
- Quick Command 버튼
- Clear 버튼
- Phase 5 MVP 배지

---

## 구현된 파일 목록

### 백엔드 (13개)

#### 신규 파일 (8개)

| 파일 | 설명 |
|------|------|
| `mcp/McpTool.java` | MCP 도구 Enum 정의 |
| `mcp/McpToolHandler.java` | 도구 핸들러 인터페이스 |
| `mcp/McpToolDispatcher.java` | Enum 기반 라우팅 |
| `mcp/JsonRpcModels.java` | JSON-RPC 2.0 모델 |
| `mcp/McpTransportController.java` | HTTP + SSE 엔드포인트 |
| `git/GitWriteService.java` | Git 쓰기 작업 |
| `service/DocumentWriteService.java` | 문서 쓰기 로직 |
| `service/PermissionService.java` | 권한 검사 |

#### 수정된 파일 (3개)

| 파일 | 변경 내용 |
|------|----------|
| `mcp/McpModels.java` | CreateDocument, UpdateDocument, PushToRemote 모델 추가 |
| `mcp/McpController.java` | 3개 WRITE 도구 엔드포인트 추가 + Dispatcher 통합 |
| `git/GitService.java` | `getCredentialsProvider()` public으로 변경 |

### 프론트엔드 (11개)

#### 신규 파일 (10개)

| 파일 | 설명 |
|------|------|
| `lib/mcp-protocol.ts` | JSON-RPC 2.0 클라이언트 |
| `lib/mcp-client.ts` | MCP Tools 타입 안전 래퍼 |
| `hooks/use-mcp-tools.ts` | MCP 도구 호출 hook |
| `components/playground/message-input.tsx` | 메시지 입력 |
| `components/playground/message-item.tsx` | 메시지 아이템 |
| `components/playground/message-list.tsx` | 메시지 목록 |
| `components/playground/chat-interface.tsx` | 채팅 인터페이스 |
| `components/ui/scroll-area.tsx` | 스크롤 영역 |
| `app/[locale]/playground/page.tsx` | Playground 페이지 |

#### 수정된 파일 (1개)

| 파일 | 변경 내용 |
|------|----------|
| `lib/types.ts` | JsonRpc, ChatMessage, ToolCall 타입 추가 |

---

## 빌드 및 테스트

### 빌드 상태

```bash
# 백엔드
cd backend && ./gradlew build -x test
# ✅ BUILD SUCCESSFUL in 7s

# 프론트엔드
cd frontend && npm run build
# ✅ Compiled successfully
# ✅ Route: /[locale]/playground ƒ (Dynamic)
```

### 엔드포인트 확인

#### MCP Server

```bash
# 1. Ping
curl -X POST http://localhost:8342/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"ping"}'

# 응답:
# {"jsonrpc":"2.0","id":1,"result":{"status":"pong"}}

# 2. Tools List
curl -X POST http://localhost:8342/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'

# 응답:
# {"jsonrpc":"2.0","id":2,"result":{"tools":[...]}}
```

#### SSE Stream

```bash
curl -N http://localhost:8342/mcp/stream

# 출력:
# event: connected
# data: {"clientId":"..."}
```

---

## 사용 방법

### 1. 서버 시작

```bash
# 백엔드
cd backend && ./gradlew bootRun

# 프론트엔드
cd frontend && npm run dev
```

### 2. Playground 접속

```
http://localhost:3000/playground
```

### 3. 명령어 테스트

**Ping Server**:
```
입력: ping
출력: Pong! Server is alive.
```

**List Tools**:
```
입력: list tools
출력: Available tools: list_documents, get_document, ..., push_to_remote
```

### 4. Tool Call 확인

메시지에서 Tool Call 섹션 확인:
- 도구 이름
- Input (JSON)
- Output (JSON)
- Duration (ms)

---

## 남은 작업 (TODO)

### Phase 5 완료 사항

- [x] McpTool Enum 구현
- [x] McpToolDispatcher 구현
- [x] GitWriteService 구현
- [x] DocumentWriteService 구현
- [x] PermissionService 구현
- [x] McpTransportController (JSON-RPC + SSE)
- [x] MCP Protocol 클라이언트
- [x] MCP Client 타입 안전 래퍼
- [x] Playground UI 컴포넌트
- [x] use-mcp-tools hook
- [x] Playground 페이지

### Phase 5 남은 작업

#### 백엔드

- [ ] **사용자 인증 통합**: SecurityContext에서 실제 사용자 정보 가져오기
  - 현재: 더미 사용자 `00000000-0000-0000-0000-000000000000` 사용
  - 목표: JWT 토큰에서 사용자 ID 추출
  - 파일: `McpToolDispatcher.java:227, 235`

- [ ] **권한 검사 테스트**: PermissionService 통합 테스트
  - EDITOR/VIEWER 역할별 접근 제어 검증
  - SecurityException 발생 확인

- [ ] **Optimistic Locking**: 문서 수정 시 동시성 제어
  - ETag 기반 충돌 감지
  - 계획서에는 있지만 미구현

- [ ] **단위 테스트**:
  - GitWriteService 테스트
  - DocumentWriteService 테스트
  - PermissionService 테스트

- [ ] **통합 테스트**:
  - MCP update_document 전체 플로우
  - MCP create_document → sync → DB 확인
  - MCP push_to_remote 성공/실패

#### 프론트엔드

- [ ] **프로젝트 선택기**: Playground에서 프로젝트 선택 가능
  - 현재: 하드코딩 없음
  - 목표: 드롭다운으로 프로젝트 선택

- [ ] **Tool Call 고급 시각화**:
  - 접기/펴기 기능
  - JSON Syntax Highlighting
  - Copy to Clipboard

- [ ] **문서 미리보기**: 사이드바에 문서 내용 표시
  - get_document 결과 표시
  - Markdown 렌더링

- [ ] **MCP Connection Status**: 연결 상태 표시
  - 연결됨/끊김 표시
  - 재연결 버튼

- [ ] **대화 세션 저장**: LocalStorage에 메시지 히스토리 저장
  - 새로고침 시에도 유지
  - Clear 버튼으로 삭제

- [ ] **다국어 지원**: Playground 페이지 i18n
  - 현재: 영어만
  - 목표: 한국어/영어

#### 공통

- [ ] **에러 처리 개선**:
  - 사용자 친화적 에러 메시지
  - Retry 로직
  - Rate Limiting 표시

- [ ] **로깅 개선**:
  - MCP 도구 호출 로그
  - 성능 메트릭

---

## Phase 6 계획

### 1. LLM 통합 (최우선)

**목표**: 실제 대화형 문서 작업

**구현 항목**:
- [ ] OpenAI/Anthropic API 연동
- [ ] LLM → MCP Tools 자동 호출
- [ ] 대화 컨텍스트 관리
- [ ] 스트리밍 응답 지원

**아키텍처**:
```
User Input → LLM → Function Call → MCP Tools → Result → LLM → Response
```

**예시 대화**:
```
User: "README.md에 설치 방법 추가해줘"
Assistant: [get_document] → [분석] → [update_document] → "README.md를 업데이트했습니다."
```

### 2. 고급 기능

- [ ] **Branch 관리 UI**:
  - 브랜치 선택/생성/전환
  - PR 생성 (GitHub API)

- [ ] **Conflict 해결**:
  - 충돌 감지 UI
  - 3-way merge 인터페이스

- [ ] **대화 세션 관리**:
  - 세션 목록
  - 세션 저장/불러오기
  - 세션 공유 (URL)

- [ ] **Tool Call 템플릿**:
  - 자주 사용하는 작업 템플릿
  - "모든 README 검색"
  - "문서 생성 후 커밋"

### 3. 모니터링 & 디버깅

- [ ] **MCP Tools 대시보드**:
  - 도구별 사용 통계
  - 성공/실패율
  - 평균 실행 시간

- [ ] **디버그 모드**:
  - Raw JSON-RPC 요청/응답 표시
  - Tool Call 타임라인

### 4. 보안 & 성능

- [ ] **Rate Limiting**:
  - MCP 쓰기 도구 제한 (사용자별)
  - LLM API 호출 제한

- [ ] **경로 탐색 공격 방지**:
  - 파일 경로 정규화
  - Git 레포지토리 외부 쓰기 차단

- [ ] **커밋 메시지 Sanitization**:
  - XSS 방지
  - 길이 제한 (500자)

---

## 참고 자료

### 내부 문서

- [Phase 5 계획서](../plan/phase5/README.md)
- [Backend 계획](../plan/phase5/backend-plan.md)
- [Frontend 계획](../plan/phase5/frontend-plan.md)
- [User Scenarios](../plan/phase5/user-scenarios.md)

### 외부 자료

- [MCP Protocol Specification](https://modelcontextprotocol.io/)
- [JSON-RPC 2.0 Specification](https://www.jsonrpc.org/specification)
- [JGit Documentation](https://www.eclipse.org/jgit/documentation/)

---

## 변경 이력

| 날짜 | 작성자 | 내용 |
|------|--------|------|
| 2026-01-01 | Claude Sonnet 4.5 | Phase 5 MVP 구현 완료 |
