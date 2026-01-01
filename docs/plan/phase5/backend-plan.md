# Phase 5: 백엔드 구현 계획

> **작성일**: 2026-01-01
> **기반**: Phase 4 완료 (RAG 파이프라인, 동적 설정)
> **목표**: MCP를 통한 문서 쓰기 및 Git 커밋/푸시 기능 구현

---

## 개요

현재 MCP Tools는 읽기 전용 기능만 제공합니다 (`list_documents`, `get_document`, `search_documents` 등). Phase 5에서는 LLM 에이전트가 문서를 생성, 수정하고 Git에 커밋/푸시할 수 있는 쓰기 기능을 추가합니다.

---

## 설계 결정사항

| 항목 | 결정 | 이유 |
|------|------|------|
| **MCP Transport** | **HTTP Streamable + SSE + STDIO** | 표준 MCP 프로토콜, 다양한 클라이언트 지원 |
| Git Author | **Bot + 사용자 명시** | Bot이 커밋하되, 메시지에 "by @username" 추가 |
| 커밋 전략 | **즉시 커밋 vs 스테이징** | `createCommit` 플래그로 선택 가능 |
| 권한 검사 | **프로젝트 멤버십 확인** | Repository WRITE 권한 필요 |
| 동기화 | **커밋 후 자동 동기화** | DB와 Git 상태 일관성 유지 |
| 동시성 제어 | **Optimistic Locking + ETag** | 동시 편집 충돌 방지 |

---

## MCP Transport 구현

### 지원 Transport 모드

MCP 프로토콜 표준에 따라 3가지 transport 모드를 동적으로 지원합니다:

| Transport | 설명 | 사용 사례 |
|-----------|------|----------|
| **HTTP Streamable** | HTTP POST/GET + SSE 옵션 | 웹 클라이언트, REST API |
| **SSE** | Server-Sent Events | 실시간 스트리밍 응답 |
| **STDIO** | 표준 입출력 | CLI 도구, Claude Desktop |

### HTTP Streamable 엔드포인트

```
POST /mcp                    # Tool 호출 (JSON-RPC)
GET  /mcp                    # SSE 스트림 (서버→클라이언트)
POST /mcp/tools/{toolName}   # 개별 Tool 직접 호출
GET  /mcp/tools              # Tools 목록 (메타데이터)
```

### JSON-RPC 메시지 포맷

```json
// Request
{
  "jsonrpc": "2.0",
  "id": "req-001",
  "method": "tools/call",
  "params": {
    "name": "update_document",
    "arguments": {
      "documentId": "...",
      "content": "# Updated Content"
    }
  }
}

// Response
{
  "jsonrpc": "2.0",
  "id": "req-001",
  "result": {
    "content": [
      { "type": "text", "text": "Document updated successfully" }
    ]
  }
}
```

---

## 신규 파일 구조

```
backend/src/main/java/com/docst/
├── mcp/
│   ├── McpController.java            # 기존 REST 엔드포인트 (레거시 호환)
│   ├── McpModels.java                # 신규 모델 추가 (Create/Update/Push)
│   ├── McpTool.java                  # Tool Enum 정의 (신규)
│   ├── McpToolDispatcher.java        # Tool 라우팅 (신규)
│   ├── McpToolHandler.java           # Handler 인터페이스 (신규)
│   ├── McpTransportController.java   # HTTP Streamable + SSE (신규)
│   └── handlers/                     # Tool 핸들러 구현체
│       ├── CreateDocumentHandler.java
│       ├── UpdateDocumentHandler.java
│       ├── PushToRemoteHandler.java
│       └── ...
├── service/
│   └── DocumentWriteService.java     # 문서 생성/수정 비즈니스 로직 (신규)
└── git/
    └── GitWriteService.java          # Git 쓰기 작업 (신규)
```

---

## 1. GitWriteService

**파일**: `backend/src/main/java/com/docst/git/GitWriteService.java`

### 책임
- 로컬 파일 시스템에 파일 쓰기
- Git commit 생성 (사용자 정보 포함)
- Git push 실행

### 주요 메서드

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class GitWriteService {

    private final GitService gitService;
    private final RepositoryRepository repositoryRepository;

    /**
     * 파일 내용을 로컬에 쓴다.
     * @throws IOException 파일 쓰기 실패 시
     */
    public void writeFile(Path filePath, String content) throws IOException {
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content, StandardCharsets.UTF_8);
        log.info("File written: {}", filePath);
    }

    /**
     * 파일을 스테이징하고 커밋한다.
     * @param repo 레포지토리 엔티티
     * @param relativePath 레포 내 상대 경로
     * @param message 커밋 메시지
     * @param branch 대상 브랜치
     * @param username 실제 작업 사용자 (커밋 메시지에 포함)
     * @return 생성된 커밋 SHA
     * @throws GitAPIException Git 작업 실패 시
     * @throws IOException I/O 오류
     */
    public String commitFile(
        Repository repo,
        String relativePath,
        String message,
        String branch,
        String username
    ) throws GitAPIException, IOException {

        try (Git git = gitService.cloneOrOpen(repo)) {
            // Checkout branch
            if (branch != null && !branch.isEmpty()) {
                gitService.checkout(git, branch);
            }

            // Stage file
            git.add()
                .addFilepattern(relativePath)
                .call();

            // Commit (Bot이 커밋하되, 메시지에 실제 사용자 명시)
            String commitMessage = message;
            if (username != null && !username.isEmpty()) {
                commitMessage = message + "\n\nby @" + username;
            }

            RevCommit commit = git.commit()
                .setMessage(commitMessage)
                .setAuthor("Docst Bot", "bot@docst.com")
                .call();

            log.info("Committed: {} ({})", commit.getName(), relativePath);
            return commit.getName();
        }
    }

    /**
     * 로컬 커밋을 원격으로 푸시한다.
     * @param repo 레포지토리 엔티티
     * @param branch 푸시할 브랜치
     * @throws GitAPIException Git 작업 실패 시
     * @throws IOException I/O 오류
     */
    public void pushToRemote(
        Repository repo,
        String branch
    ) throws GitAPIException, IOException {

        try (Git git = gitService.cloneOrOpen(repo)) {
            CredentialsProvider credProvider = gitService.getCredentialsProvider(repo);

            git.push()
                .setRemote("origin")
                .setRefSpecs(new RefSpec(branch + ":" + branch))
                .setCredentialsProvider(credProvider)
                .call();

            log.info("Pushed to remote: {} (branch: {})", repo.getFullName(), branch);
        }
    }

    /**
     * GitService에서 로컬 경로를 가져온다.
     */
    public Path getLocalPath(UUID repositoryId) {
        return gitService.getLocalPath(repositoryId);
    }
}
```

---

## 2. DocumentWriteService

**파일**: `backend/src/main/java/com/docst/service/DocumentWriteService.java`

### 책임
- 문서 수정 비즈니스 로직 (권한 검사, 파일 쓰기, 커밋, 동기화)
- 트랜잭션 관리

### 주요 메서드

```java
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DocumentWriteService {

    private final DocumentService documentService;
    private final GitWriteService gitWriteService;
    private final GitSyncService gitSyncService;
    private final UserService userService;

    /**
     * 문서 내용을 수정하고 선택적으로 커밋한다.
     *
     * @param userId 작업 사용자 ID
     * @param documentId 문서 ID
     * @param content 수정된 내용
     * @param message 커밋 메시지
     * @param branch 대상 브랜치 (null이면 defaultBranch)
     * @param createCommit true: 즉시 커밋, false: 스테이징만
     * @return 업데이트 결과
     * @throws IllegalArgumentException 문서/사용자 없음
     * @throws SecurityException 권한 없음
     */
    public UpdateDocumentResult updateDocument(
        UUID userId,
        UUID documentId,
        String content,
        String message,
        String branch,
        boolean createCommit
    ) {
        // 1. 문서 조회
        Document doc = documentService.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        Repository repo = doc.getRepository();
        Project project = repo.getProject();

        // 2. 권한 검사 (프로젝트 멤버인지 확인)
        // TODO: ProjectMemberService로 권한 확인

        // 3. 사용자 정보 조회
        User user = userService.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // 4. 파일 쓰기
        Path localPath = gitWriteService.getLocalPath(repo.getId());
        Path filePath = localPath.resolve(doc.getPath());

        try {
            gitWriteService.writeFile(filePath, content);
        } catch (IOException e) {
            log.error("Failed to write file: {}", filePath, e);
            throw new RuntimeException("File write failed: " + e.getMessage(), e);
        }

        // 5. 커밋 (옵션)
        String commitSha = null;
        String targetBranch = branch != null ? branch : repo.getDefaultBranch();

        if (createCommit) {
            String commitMessage = message != null ? message : "Update " + doc.getPath();

            try {
                commitSha = gitWriteService.commitFile(
                    repo,
                    doc.getPath(),
                    commitMessage,
                    targetBranch,
                    user.getDisplayName()
                );

                // 6. 동기화 (DB 업데이트, 임베딩 재생성)
                gitSyncService.syncRepository(
                    null, // jobId
                    repo.getId(),
                    targetBranch,
                    SyncMode.SPECIFIC_COMMIT,
                    commitSha,
                    null,
                    true // enableEmbedding
                );

            } catch (GitAPIException | IOException e) {
                log.error("Failed to commit: {}", doc.getPath(), e);
                throw new RuntimeException("Git commit failed: " + e.getMessage(), e);
            }
        }

        return new UpdateDocumentResult(
            documentId,
            doc.getPath(),
            commitSha,
            createCommit,
            createCommit ? "Document updated and committed" : "Document staged (not committed)"
        );
    }
}
```

---

## 3. MCP Transport Controller

**파일**: `backend/src/main/java/com/docst/mcp/McpTransportController.java` (신규)

### HTTP Streamable + SSE 지원

```java
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
@Slf4j
public class McpTransportController {

    private final ObjectMapper objectMapper;
    private final McpToolDispatcher toolDispatcher;

    /**
     * JSON-RPC 2.0 요청 처리 (HTTP Streamable).
     * MCP 프로토콜 표준 엔드포인트.
     */
    @PostMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> handleJsonRpc(
        @RequestBody JsonRpcRequest request,
        @RequestHeader(value = "Accept", required = false) String accept,
        @RequestHeader(value = "Origin", required = false) String origin
    ) {
        // Origin 헤더 검증 (보안)
        validateOrigin(origin);

        // SSE 스트리밍 요청인지 확인
        if (accept != null && accept.contains("text/event-stream")) {
            return handleStreamingRequest(request);
        }

        // 일반 JSON-RPC 응답
        try {
            JsonRpcResponse response = processRequest(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("JSON-RPC processing error", e);
            return ResponseEntity.ok(JsonRpcResponse.error(
                request.id(),
                -32603,
                e.getMessage()
            ));
        }
    }

    /**
     * SSE 스트림 연결 (서버 → 클라이언트 통신).
     */
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter openSseStream(
        @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId
    ) {
        SseEmitter emitter = new SseEmitter(0L); // 타임아웃 없음

        // Resumability: Last-Event-ID로 이전 위치부터 재개
        if (lastEventId != null) {
            log.info("Resuming SSE stream from event: {}", lastEventId);
        }

        return emitter;
    }

    private JsonRpcResponse processRequest(JsonRpcRequest request) {
        return switch (request.method()) {
            case "initialize" -> handleInitialize(request);
            case "tools/list" -> handleListTools(request);
            case "tools/call" -> handleToolCall(request);
            case "ping" -> JsonRpcResponse.result(request.id(), Map.of());
            default -> JsonRpcResponse.error(
                request.id(),
                -32601,
                "Method not found: " + request.method()
            );
        };
    }

    private JsonRpcResponse handleToolCall(JsonRpcRequest request) {
        Map<String, Object> params = request.params();
        String toolName = (String) params.get("name");
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");

        Object result = toolDispatcher.dispatch(toolName, arguments);

        return JsonRpcResponse.result(request.id(), Map.of(
            "content", List.of(Map.of(
                "type", "text",
                "text", objectMapper.writeValueAsString(result)
            ))
        ));
    }
}

/**
 * JSON-RPC 2.0 요청/응답 모델.
 */
public record JsonRpcRequest(String jsonrpc, String id, String method, Map<String, Object> params) {}
public record JsonRpcResponse(String jsonrpc, String id, Object result, JsonRpcError error) {
    public static JsonRpcResponse result(String id, Object result) {
        return new JsonRpcResponse("2.0", id, result, null);
    }
    public static JsonRpcResponse error(String id, int code, String message) {
        return new JsonRpcResponse("2.0", id, null, new JsonRpcError(code, message));
    }
}
public record JsonRpcError(int code, String message) {}
```

---

## 4. MCP Tool Enum 및 Dispatcher

**파일**: `backend/src/main/java/com/docst/mcp/McpTool.java` (신규)

### Tool 이름을 Enum으로 관리

```java
/**
 * MCP Tool 정의.
 * 모든 도구의 메타데이터를 중앙에서 관리.
 */
@Getter
@RequiredArgsConstructor
public enum McpTool {

    // ===== 읽기 도구 =====
    LIST_DOCUMENTS(
        "list_documents",
        "List documents in a project or repository",
        ListDocumentsInput.class,
        ToolCategory.READ
    ),
    GET_DOCUMENT(
        "get_document",
        "Get document content by ID",
        GetDocumentInput.class,
        ToolCategory.READ
    ),
    SEARCH_DOCUMENTS(
        "search_documents",
        "Search documents using keyword, semantic, or hybrid mode",
        SearchDocumentsInput.class,
        ToolCategory.READ
    ),
    LIST_DOCUMENT_VERSIONS(
        "list_document_versions",
        "List document version history",
        ListDocumentVersionsInput.class,
        ToolCategory.READ
    ),
    DIFF_DOCUMENT(
        "diff_document",
        "Compare two document versions",
        DiffDocumentInput.class,
        ToolCategory.READ
    ),
    SYNC_REPOSITORY(
        "sync_repository",
        "Sync repository from remote",
        SyncRepositoryInput.class,
        ToolCategory.READ
    ),

    // ===== 쓰기 도구 =====
    CREATE_DOCUMENT(
        "create_document",
        "Create a new document in the repository",
        CreateDocumentInput.class,
        ToolCategory.WRITE
    ),
    UPDATE_DOCUMENT(
        "update_document",
        "Update document content and optionally commit to Git",
        UpdateDocumentInput.class,
        ToolCategory.WRITE
    ),
    PUSH_TO_REMOTE(
        "push_to_remote",
        "Push local commits to remote repository",
        PushToRemoteInput.class,
        ToolCategory.WRITE
    );

    private final String name;
    private final String description;
    private final Class<?> inputClass;
    private final ToolCategory category;

    /**
     * Tool 이름으로 Enum 조회.
     */
    public static Optional<McpTool> fromName(String name) {
        return Arrays.stream(values())
            .filter(tool -> tool.name.equals(name))
            .findFirst();
    }

    /**
     * JSON Schema 생성.
     */
    public Map<String, Object> getInputSchema() {
        return JsonSchemaGenerator.generate(inputClass);
    }

    /**
     * Tool 카테고리 (권한 검사에 사용).
     */
    public enum ToolCategory {
        READ,   // 읽기 전용 (인증만 필요)
        WRITE   // 쓰기 (프로젝트 멤버십 + WRITE 권한 필요)
    }
}
```

**파일**: `backend/src/main/java/com/docst/mcp/McpToolDispatcher.java` (신규)

### Tool 호출 라우팅 (Enum 기반)

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class McpToolDispatcher {

    private final Map<McpTool, McpToolHandler<?>> handlers;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        // 핸들러 등록은 Spring Bean으로 자동 주입
    }

    /**
     * Tool 이름으로 적절한 핸들러에 라우팅.
     */
    public Object dispatch(String toolName, Map<String, Object> arguments) {
        McpTool tool = McpTool.fromName(toolName)
            .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + toolName));

        McpToolHandler<?> handler = handlers.get(tool);
        if (handler == null) {
            throw new IllegalStateException("No handler registered for: " + tool);
        }

        // 입력 변환 및 실행
        Object input = objectMapper.convertValue(arguments, tool.getInputClass());
        return handler.handle(input);
    }

    /**
     * 모든 Tool 목록 반환 (tools/list 응답용).
     */
    public List<McpToolDefinition> listTools() {
        return Arrays.stream(McpTool.values())
            .map(tool -> new McpToolDefinition(
                tool.getName(),
                tool.getDescription(),
                tool.getInputSchema()
            ))
            .toList();
    }
}

/**
 * Tool 핸들러 인터페이스.
 */
public interface McpToolHandler<T> {
    Object handle(T input);
    McpTool getTool();
}

/**
 * 예시: CreateDocument 핸들러.
 */
@Component
@RequiredArgsConstructor
public class CreateDocumentHandler implements McpToolHandler<CreateDocumentInput> {

    private final DocumentWriteService documentWriteService;
    private final SecurityContext securityContext;

    @Override
    public Object handle(CreateDocumentInput input) {
        UUID userId = securityContext.getCurrentUserId();
        return documentWriteService.createDocument(
            userId,
            input.repositoryId(),
            input.path(),
            input.content(),
            input.message(),
            input.branch(),
            input.createCommit()
        );
    }

    @Override
    public McpTool getTool() {
        return McpTool.CREATE_DOCUMENT;
    }
}
```

---

## 5. MCP Models 확장

**파일**: `backend/src/main/java/com/docst/mcp/McpModels.java`

### 신규 모델

```java
// ===== create_document (신규) =====

/**
 * create_document 도구 입력.
 *
 * @param repositoryId 레포지토리 ID
 * @param path 파일 경로 (docs/new-file.md)
 * @param content 문서 내용
 * @param message 커밋 메시지 (옵션)
 * @param branch 대상 브랜치 (옵션, 기본: defaultBranch)
 * @param createCommit true: 즉시 커밋, false: 파일만 생성
 */
public record CreateDocumentInput(
    UUID repositoryId,
    String path,
    String content,
    String message,
    String branch,
    boolean createCommit
) {}

/**
 * create_document 도구 결과.
 *
 * @param documentId 생성된 문서 ID
 * @param path 파일 경로
 * @param commitSha 생성된 커밋 SHA (커밋된 경우)
 * @param committed 커밋 여부
 * @param message 결과 메시지
 */
public record CreateDocumentResult(
    UUID documentId,
    String path,
    String commitSha,
    boolean committed,
    String message
) {}

// ===== update_document =====

/**
 * update_document 도구 입력.
 *
 * @param documentId 문서 ID
 * @param content 수정된 내용
 * @param message 커밋 메시지 (옵션)
 * @param branch 대상 브랜치 (옵션, 기본: defaultBranch)
 * @param createCommit true: 즉시 커밋, false: 스테이징만
 */
public record UpdateDocumentInput(
    UUID documentId,
    String content,
    String message,
    String branch,
    boolean createCommit
) {}

/**
 * update_document 도구 결과.
 *
 * @param documentId 문서 ID
 * @param path 파일 경로
 * @param newCommitSha 생성된 커밋 SHA (커밋된 경우)
 * @param committed 커밋 여부
 * @param message 결과 메시지
 */
public record UpdateDocumentResult(
    UUID documentId,
    String path,
    String newCommitSha,
    boolean committed,
    String message
) {}

// ===== push_to_remote =====

/**
 * push_to_remote 도구 입력.
 *
 * @param repositoryId 레포지토리 ID
 * @param branch 푸시할 브랜치 (기본: defaultBranch)
 */
public record PushToRemoteInput(
    UUID repositoryId,
    String branch
) {}

/**
 * push_to_remote 도구 결과.
 *
 * @param repositoryId 레포지토리 ID
 * @param branch 푸시된 브랜치
 * @param success 성공 여부
 * @param message 결과 메시지
 */
public record PushToRemoteResult(
    UUID repositoryId,
    String branch,
    boolean success,
    String message
) {}
```

---

## 4. McpController 확장

**파일**: `backend/src/main/java/com/docst/mcp/McpController.java`

### 신규 엔드포인트

```java
/**
 * 문서를 수정하고 선택적으로 커밋한다.
 *
 * @param input 수정 정보
 * @return 업데이트 결과
 */
@PostMapping("/update_document")
public ResponseEntity<McpResponse<UpdateDocumentResult>> updateDocument(
    @RequestBody UpdateDocumentInput input,
    @AuthenticationPrincipal User currentUser
) {
    try {
        UpdateDocumentResult result = documentWriteService.updateDocument(
            currentUser.getId(),
            input.documentId(),
            input.content(),
            input.message(),
            input.branch(),
            input.createCommit()
        );

        return ResponseEntity.ok(McpResponse.success(result));
    } catch (SecurityException e) {
        return ResponseEntity.status(403)
            .body(McpResponse.error("Permission denied: " + e.getMessage()));
    } catch (Exception e) {
        log.error("Failed to update document", e);
        return ResponseEntity.internalServerError()
            .body(McpResponse.error(e.getMessage()));
    }
}

/**
 * 로컬 커밋을 원격 레포지토리로 푸시한다.
 *
 * @param input 푸시 정보
 * @return 푸시 결과
 */
@PostMapping("/push_to_remote")
public ResponseEntity<McpResponse<PushToRemoteResult>> pushToRemote(
    @RequestBody PushToRemoteInput input,
    @AuthenticationPrincipal User currentUser
) {
    try {
        Repository repo = repositoryService.findById(input.repositoryId())
            .orElseThrow(() -> new IllegalArgumentException("Repository not found"));

        // 권한 검사
        // TODO: 프로젝트 멤버십 확인

        String branch = input.branch() != null ? input.branch() : repo.getDefaultBranch();
        gitWriteService.pushToRemote(repo, branch);

        return ResponseEntity.ok(McpResponse.success(
            new PushToRemoteResult(
                input.repositoryId(),
                branch,
                true,
                "Pushed successfully to " + branch
            )
        ));
    } catch (SecurityException e) {
        return ResponseEntity.status(403)
            .body(McpResponse.error("Permission denied: " + e.getMessage()));
    } catch (Exception e) {
        log.error("Failed to push to remote", e);
        return ResponseEntity.internalServerError()
            .body(McpResponse.error(e.getMessage()));
    }
}
```

---

## 5. MCP Server 메타데이터

**파일**: `backend/src/main/java/com/docst/mcp/McpServerController.java` (신규)

### MCP Tools 목록 제공

```java
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class McpServerController {

    /**
     * MCP Server 메타데이터 (tools 목록).
     * Claude Desktop 등 MCP 클라이언트가 사용.
     */
    @GetMapping("/tools")
    public ResponseEntity<McpToolsResponse> getTools() {
        List<McpToolDefinition> tools = List.of(
            // 읽기 도구
            new McpToolDefinition(
                "list_documents",
                "List documents in a project or repository",
                createSchema("ListDocumentsInput")
            ),
            new McpToolDefinition(
                "get_document",
                "Get document content by ID",
                createSchema("GetDocumentInput")
            ),
            new McpToolDefinition(
                "list_document_versions",
                "List document version history",
                createSchema("ListDocumentVersionsInput")
            ),
            new McpToolDefinition(
                "diff_document",
                "Compare two document versions",
                createSchema("DiffDocumentInput")
            ),
            new McpToolDefinition(
                "search_documents",
                "Search documents using keyword, semantic, or hybrid mode",
                createSchema("SearchDocumentsInput")
            ),
            new McpToolDefinition(
                "sync_repository",
                "Sync repository from remote",
                createSchema("SyncRepositoryInput")
            ),

            // 쓰기 도구 (신규)
            new McpToolDefinition(
                "update_document",
                "Update document content and optionally commit to Git",
                createSchema("UpdateDocumentInput")
            ),
            new McpToolDefinition(
                "push_to_remote",
                "Push local commits to remote repository",
                createSchema("PushToRemoteInput")
            )
        );

        return ResponseEntity.ok(new McpToolsResponse(tools));
    }

    private Map<String, Object> createSchema(String inputType) {
        // JSON Schema 생성 로직
        // TODO: 실제 스키마 정의
        return Map.of("type", "object");
    }
}

/**
 * MCP Tools 응답.
 */
public record McpToolsResponse(List<McpToolDefinition> tools) {}

/**
 * MCP Tool 정의.
 */
public record McpToolDefinition(
    String name,
    String description,
    Map<String, Object> inputSchema
) {}
```

---

## 보안 고려사항

### 1. 권한 검사

```java
// ProjectMemberService (구현 필요)
public void checkWritePermission(UUID userId, UUID projectId) {
    Optional<ProjectMember> member = projectMemberRepository
        .findByUserIdAndProjectId(userId, projectId);

    if (member.isEmpty()) {
        throw new SecurityException("User is not a project member");
    }

    // TODO: Role 기반 권한 검사 (ADMIN, MEMBER 등)
}
```

### 2. 경로 탐색 공격 방지

```java
// GitWriteService에 추가
private void validatePath(Path basePath, Path targetPath) throws IOException {
    Path normalized = targetPath.normalize().toAbsolutePath();
    if (!normalized.startsWith(basePath.toAbsolutePath())) {
        throw new SecurityException("Path traversal detected: " + targetPath);
    }
}
```

### 3. 커밋 메시지 Sanitization

```java
// DocumentWriteService에 추가
private String sanitizeCommitMessage(String message) {
    // XSS 방지, 길이 제한
    if (message == null || message.isBlank()) {
        return "Update document";
    }

    String sanitized = message
        .replaceAll("[<>\"']", "") // 특수 문자 제거
        .substring(0, Math.min(message.length(), 500)); // 최대 500자

    return sanitized;
}
```

---

## 테스트 계획

### 단위 테스트

```java
// GitWriteServiceTest.java
@SpringBootTest
class GitWriteServiceTest {

    @Test
    void writeFile_success() throws IOException {
        Path testPath = tempDir.resolve("test.md");
        gitWriteService.writeFile(testPath, "# Test");

        String content = Files.readString(testPath);
        assertEquals("# Test", content);
    }

    @Test
    void commitFile_createsCommitWithUserInfo() throws Exception {
        // Mock Repository
        // 커밋 생성 및 메시지 검증
    }

    @Test
    void pushToRemote_withCredentials() throws Exception {
        // Mock Git push
        // Credential 사용 검증
    }
}
```

### 통합 테스트

```java
// McpControllerIntegrationTest.java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class McpControllerIntegrationTest {

    @Test
    void updateDocument_fullFlow() throws Exception {
        // 1. 문서 생성
        // 2. update_document 호출
        // 3. DB 확인
        // 4. Git 커밋 확인
    }

    @Test
    void pushToRemote_success() throws Exception {
        // 1. 문서 수정 및 커밋
        // 2. push_to_remote 호출
        // 3. 원격 레포 확인 (mock)
    }
}
```

---

## 구현 순서

1. **GitWriteService 구현**
   - [ ] writeFile 메서드
   - [ ] commitFile 메서드
   - [ ] pushToRemote 메서드
   - [ ] 단위 테스트

2. **DocumentWriteService 구현**
   - [ ] updateDocument 메서드
   - [ ] 권한 검사 로직
   - [ ] 트랜잭션 관리
   - [ ] 단위 테스트

3. **McpModels 확장**
   - [ ] UpdateDocumentInput/Result
   - [ ] PushToRemoteInput/Result

4. **McpController 확장**
   - [ ] update_document 엔드포인트
   - [ ] push_to_remote 엔드포인트
   - [ ] 에러 핸들링

5. **McpServerController 구현**
   - [ ] /mcp/tools 엔드포인트
   - [ ] JSON Schema 생성

6. **보안 강화**
   - [ ] 권한 검사
   - [ ] 경로 검증
   - [ ] 메시지 sanitization

7. **통합 테스트**
   - [ ] 전체 플로우 테스트
   - [ ] 에러 케이스 테스트

---

## 완료 기준

- [ ] GitWriteService 구현 및 테스트
- [ ] DocumentWriteService 구현 및 테스트
- [ ] MCP update_document 엔드포인트 동작
- [ ] MCP push_to_remote 엔드포인트 동작
- [ ] 권한 검사 동작
- [ ] 커밋 후 자동 동기화 동작
- [ ] 사용자 정보가 커밋 메시지에 포함됨
- [ ] 모든 단위/통합 테스트 통과