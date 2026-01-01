# Phase 6 Backend 구현 계획

> LLM API 통합 및 고급 Git 기능 (Spring AI 1.1.0+ Core 기반)

---

## 핵심 변경사항 (2026-01 업데이트)

### Spring AI 1.1.0+ 통합 API 사용

기존 계획에서 OpenAI SDK, Anthropic SDK를 직접 사용하려 했으나, **Spring AI Core만 사용**하도록 변경합니다.

| 기존 계획 | 변경된 계획 |
|----------|------------|
| OpenAI SDK 직접 호출 | Spring AI `ChatClient` 통합 API |
| Anthropic SDK 직접 호출 | Spring AI `ChatClient` 통합 API |
| 각 Provider별 Client 클래스 | 단일 `ChatClient` + 설정으로 Provider 전환 |
| `FunctionCallback` (레거시) | `@Tool` + `ToolCallback` (최신 API) |

### 주요 장점

1. **Provider 독립적**: OpenAI, Anthropic, Ollama 등 20+ 모델 지원
2. **코드 변경 없이 Provider 전환**: application.yml 설정만 변경
3. **통합 Tool Calling API**: `@Tool` 어노테이션으로 선언적 도구 정의
4. **자동 관리**: 대화 루프, 재시도, 에러 핸들링 프레임워크 제공

---

## 신규 파일 구조

```
backend/src/main/java/com/docst/
├── llm/                              # 신규
│   ├── LlmService.java               # ChatClient 기반 LLM 서비스
│   ├── LlmConfig.java                # ChatClient Bean 설정
│   ├── tools/                        # MCP → Tool 매핑
│   │   ├── DocumentTools.java        # 문서 관련 @Tool 메서드
│   │   ├── SearchTools.java          # 검색 관련 @Tool 메서드
│   │   └── GitTools.java             # Git 관련 @Tool 메서드
│   └── advisor/
│       └── McpToolCallAdvisor.java   # MCP 연동 ToolCallAdvisor
├── git/
│   ├── BranchService.java            # 신규: Branch 관리
│   ├── MergeService.java             # 신규: Conflict 해결
│   └── PullRequestService.java       # 신규: PR 생성
└── api/
    └── LlmController.java            # 신규: LLM API 프록시
```

---

## 1. LLM Service 구현 (Spring AI ChatClient)

### 1.1 LlmConfig (ChatClient Bean 설정)

```java
package com.docst.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmConfig {

    /**
     * ChatClient Bean - Provider에 독립적
     * application.yml에서 spring.ai.openai 또는 spring.ai.anthropic 설정으로 Provider 전환
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
        return builder
            .defaultSystem("""
                You are a helpful documentation assistant.
                You can search, read, update, and create documents.
                Always respond in the same language as the user's query.
                """)
            .defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory)
                    .build()
            )
            .build();
    }

    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }
}
```

### 1.2 LlmService (통합 서비스)

```java
package com.docst.llm;

import com.docst.llm.tools.DocumentTools;
import com.docst.llm.tools.SearchTools;
import com.docst.llm.tools.GitTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class LlmService {

    private final ChatClient chatClient;
    private final DocumentTools documentTools;
    private final SearchTools searchTools;
    private final GitTools gitTools;

    /**
     * LLM과 대화 (동기 호출)
     * Spring AI ChatClient가 Tool Calling 루프를 자동 처리
     */
    public String chat(String userMessage, UUID projectId, String sessionId) {
        return chatClient.prompt()
            .user(userMessage)
            // @Tool 어노테이션 클래스들 등록
            .tools(documentTools, searchTools, gitTools)
            // Tool Context로 projectId 전달
            .toolContext(Map.of(
                "projectId", projectId.toString(),
                "sessionId", sessionId
            ))
            .call()
            .content();
    }

    /**
     * LLM과 대화 (스트리밍)
     * 텍스트 청크 단위로 실시간 응답
     */
    public Flux<String> streamChat(String userMessage, UUID projectId, String sessionId) {
        return chatClient.prompt()
            .user(userMessage)
            .tools(documentTools, searchTools, gitTools)
            .toolContext(Map.of(
                "projectId", projectId.toString(),
                "sessionId", sessionId
            ))
            .stream()
            .content();
    }

    /**
     * 스트리밍 + ChatResponse (메타데이터 포함)
     */
    public Flux<ChatResponse> streamChatWithMetadata(
        String userMessage,
        UUID projectId,
        String sessionId
    ) {
        return chatClient.prompt()
            .user(userMessage)
            .tools(documentTools, searchTools, gitTools)
            .toolContext(Map.of(
                "projectId", projectId.toString(),
                "sessionId", sessionId
            ))
            .stream()
            .chatResponse();
    }
}
```

---

## 2. @Tool 어노테이션 기반 도구 정의

### 2.1 DocumentTools

```java
package com.docst.llm.tools;

import com.docst.domain.Document;
import com.docst.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class DocumentTools {

    private final DocumentService documentService;

    /**
     * 문서 목록 조회
     */
    @Tool(description = "List all documents in the project. " +
                        "Returns document paths, titles, and types.")
    public List<DocumentInfo> listDocuments(
        @ToolParam(description = "Optional repository ID to filter") String repositoryId,
        ToolContext toolContext
    ) {
        UUID projectId = UUID.fromString(toolContext.getContext().get("projectId").toString());
        log.info("Listing documents for project: {}", projectId);

        List<Document> documents = repositoryId != null && !repositoryId.isEmpty()
            ? documentService.findByRepository(UUID.fromString(repositoryId))
            : documentService.findByProject(projectId);

        return documents.stream()
            .map(doc -> new DocumentInfo(
                doc.getId().toString(),
                doc.getPath(),
                doc.getTitle(),
                doc.getDocType().name()
            ))
            .toList();
    }

    /**
     * 문서 내용 조회
     */
    @Tool(description = "Get the full content of a document by its ID or path. " +
                        "Returns the document content in markdown format.")
    public DocumentContent getDocument(
        @ToolParam(description = "Document ID (UUID format)") String documentId,
        @ToolParam(description = "Document path (alternative to ID)", required = false) String path,
        ToolContext toolContext
    ) {
        UUID projectId = UUID.fromString(toolContext.getContext().get("projectId").toString());
        log.info("Getting document: id={}, path={}", documentId, path);

        Document doc;
        if (documentId != null && !documentId.isEmpty()) {
            doc = documentService.findById(UUID.fromString(documentId));
        } else if (path != null && !path.isEmpty()) {
            doc = documentService.findByProjectAndPath(projectId, path);
        } else {
            throw new IllegalArgumentException("Either documentId or path must be provided");
        }

        String content = documentService.getLatestContent(doc.getId());

        return new DocumentContent(
            doc.getId().toString(),
            doc.getPath(),
            doc.getTitle(),
            content
        );
    }

    /**
     * 문서 수정
     */
    @Tool(description = "Update the content of an existing document. " +
                        "Optionally create a git commit with the changes.")
    public UpdateResult updateDocument(
        @ToolParam(description = "Document ID to update") String documentId,
        @ToolParam(description = "New content for the document") String newContent,
        @ToolParam(description = "Commit message (required if createCommit is true)", required = false)
        String commitMessage,
        @ToolParam(description = "Whether to create a git commit", required = false)
        Boolean createCommit,
        ToolContext toolContext
    ) {
        log.info("Updating document: {}", documentId);

        String commitSha = documentService.updateContent(
            UUID.fromString(documentId),
            newContent,
            commitMessage,
            Boolean.TRUE.equals(createCommit)
        );

        return new UpdateResult(
            documentId,
            Boolean.TRUE.equals(createCommit),
            commitSha
        );
    }

    /**
     * 문서 생성
     */
    @Tool(description = "Create a new document in the repository. " +
                        "The path should include the file extension (.md, .adoc, etc).")
    public CreateResult createDocument(
        @ToolParam(description = "Repository ID where the document will be created") String repositoryId,
        @ToolParam(description = "File path for the new document (e.g., docs/guide.md)") String path,
        @ToolParam(description = "Document title") String title,
        @ToolParam(description = "Document content") String content,
        @ToolParam(description = "Commit message", required = false) String commitMessage,
        ToolContext toolContext
    ) {
        log.info("Creating document: {} in repo {}", path, repositoryId);

        Document doc = documentService.createDocument(
            UUID.fromString(repositoryId),
            path,
            title,
            content,
            commitMessage
        );

        return new CreateResult(
            doc.getId().toString(),
            doc.getPath(),
            doc.getLatestCommitSha()
        );
    }

    // DTOs
    public record DocumentInfo(String id, String path, String title, String type) {}
    public record DocumentContent(String id, String path, String title, String content) {}
    public record UpdateResult(String documentId, boolean committed, String commitSha) {}
    public record CreateResult(String documentId, String path, String commitSha) {}
}
```

### 2.2 SearchTools

```java
package com.docst.llm.tools;

import com.docst.service.SearchService;
import com.docst.service.SearchService.SearchMode;
import com.docst.service.SearchService.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class SearchTools {

    private final SearchService searchService;

    /**
     * 문서 검색
     */
    @Tool(description = "Search documents using keyword, semantic (AI), or hybrid search. " +
                        "Hybrid mode combines keyword and semantic search for best results.")
    public List<SearchResultDto> searchDocuments(
        @ToolParam(description = "Search query string") String query,
        @ToolParam(description = "Search mode: KEYWORD, SEMANTIC, or HYBRID (default: HYBRID)", required = false)
        String mode,
        @ToolParam(description = "Maximum number of results to return (default: 10)", required = false)
        Integer topK,
        ToolContext toolContext
    ) {
        UUID projectId = UUID.fromString(toolContext.getContext().get("projectId").toString());
        log.info("Searching documents: query={}, mode={}", query, mode);

        SearchMode searchMode = mode != null
            ? SearchMode.valueOf(mode.toUpperCase())
            : SearchMode.HYBRID;

        int limit = topK != null ? topK : 10;

        List<SearchResult> results = searchService.search(projectId, query, searchMode, limit);

        return results.stream()
            .map(r -> new SearchResultDto(
                r.documentId().toString(),
                r.path(),
                r.title(),
                r.snippet(),
                r.score()
            ))
            .toList();
    }

    public record SearchResultDto(
        String documentId,
        String path,
        String title,
        String snippet,
        double score
    ) {}
}
```

### 2.3 GitTools

```java
package com.docst.llm.tools;

import com.docst.git.BranchService;
import com.docst.git.GitService;
import com.docst.service.RepositoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class GitTools {

    private final GitService gitService;
    private final BranchService branchService;
    private final RepositoryService repositoryService;

    /**
     * 레포지토리 동기화
     */
    @Tool(description = "Synchronize a repository with its remote origin. " +
                        "Pulls latest changes and updates the document index.")
    public SyncResult syncRepository(
        @ToolParam(description = "Repository ID to sync") String repositoryId,
        ToolContext toolContext
    ) {
        log.info("Syncing repository: {}", repositoryId);

        String lastCommitSha = repositoryService.sync(UUID.fromString(repositoryId));

        return new SyncResult(repositoryId, lastCommitSha, true);
    }

    /**
     * 브랜치 목록 조회
     */
    @Tool(description = "List all branches in a repository")
    public List<String> listBranches(
        @ToolParam(description = "Repository ID") String repositoryId,
        ToolContext toolContext
    ) {
        log.info("Listing branches for repository: {}", repositoryId);

        return branchService.listBranches(UUID.fromString(repositoryId));
    }

    /**
     * 브랜치 생성
     */
    @Tool(description = "Create a new branch from an existing branch")
    public BranchResult createBranch(
        @ToolParam(description = "Repository ID") String repositoryId,
        @ToolParam(description = "Name of the new branch") String branchName,
        @ToolParam(description = "Source branch to create from (default: main)", required = false)
        String fromBranch,
        ToolContext toolContext
    ) {
        log.info("Creating branch {} from {}", branchName, fromBranch);

        String ref = branchService.createBranch(
            UUID.fromString(repositoryId),
            branchName,
            fromBranch != null ? fromBranch : "main"
        );

        return new BranchResult(branchName, ref, true);
    }

    /**
     * 브랜치 전환
     */
    @Tool(description = "Switch to a different branch in the repository")
    public BranchResult switchBranch(
        @ToolParam(description = "Repository ID") String repositoryId,
        @ToolParam(description = "Branch name to switch to") String branchName,
        ToolContext toolContext
    ) {
        log.info("Switching to branch: {}", branchName);

        branchService.switchBranch(UUID.fromString(repositoryId), branchName);

        return new BranchResult(branchName, null, true);
    }

    /**
     * 문서 버전 비교 (Diff)
     */
    @Tool(description = "Compare two versions of a document and show the differences")
    public DiffResult diffDocument(
        @ToolParam(description = "Document ID") String documentId,
        @ToolParam(description = "Base version commit SHA") String baseSha,
        @ToolParam(description = "Target version commit SHA") String targetSha,
        ToolContext toolContext
    ) {
        log.info("Diffing document {} between {} and {}", documentId, baseSha, targetSha);

        String diff = gitService.diff(UUID.fromString(documentId), baseSha, targetSha);

        return new DiffResult(documentId, baseSha, targetSha, diff);
    }

    // DTOs
    public record SyncResult(String repositoryId, String lastCommitSha, boolean success) {}
    public record BranchResult(String branchName, String ref, boolean success) {}
    public record DiffResult(String documentId, String baseSha, String targetSha, String diff) {}
}
```

---

## 3. LlmController (API 프록시)

```java
package com.docst.api;

import com.docst.llm.LlmService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.UUID;

@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmController {

    private final LlmService llmService;

    /**
     * LLM Chat (동기)
     */
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String response = llmService.chat(
            request.message(),
            request.projectId(),
            request.sessionId()
        );

        return new ChatResponse(response);
    }

    /**
     * LLM Chat (스트리밍, Server-Sent Events)
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        return llmService.streamChat(
            request.message(),
            request.projectId(),
            request.sessionId()
        );
    }

    public record ChatRequest(
        String message,
        UUID projectId,
        String sessionId
    ) {}

    public record ChatResponse(String content) {}
}
```

---

## 4. Branch 관리

### 4.1 BranchService

```java
package com.docst.git;

import com.docst.domain.Repository;
import com.docst.repository.RepositoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class BranchService {

    private final GitService gitService;
    private final RepositoryRepository repositoryRepository;

    /**
     * 브랜치 목록 조회
     */
    public List<String> listBranches(UUID repositoryId) {
        Repository repo = repositoryRepository.findById(repositoryId)
            .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        try (Git git = gitService.openRepository(repo.getLocalMirrorPath())) {
            return git.branchList()
                .call()
                .stream()
                .map(Ref::getName)
                .map(name -> name.replace("refs/heads/", ""))
                .toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to list branches", e);
        }
    }

    /**
     * 브랜치 생성
     */
    public String createBranch(UUID repositoryId, String branchName, String fromBranch) {
        Repository repo = repositoryRepository.findById(repositoryId)
            .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        try (Git git = gitService.openRepository(repo.getLocalMirrorPath())) {
            Ref ref = git.branchCreate()
                .setName(branchName)
                .setStartPoint(fromBranch)
                .call();

            log.info("Created branch: {} from {} (ref: {})", branchName, fromBranch, ref.getName());
            return ref.getName();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create branch: " + branchName, e);
        }
    }

    /**
     * 브랜치 전환
     */
    public void switchBranch(UUID repositoryId, String branchName) {
        Repository repo = repositoryRepository.findById(repositoryId)
            .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        try (Git git = gitService.openRepository(repo.getLocalMirrorPath())) {
            git.checkout()
                .setName(branchName)
                .call();

            log.info("Switched to branch: {}", branchName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to switch to branch: " + branchName, e);
        }
    }
}
```

---

## 5. Configuration

### application.yml

```yaml
spring:
  ai:
    # OpenAI 사용 시 (기본)
    openai:
      api-key: ${OPENAI_API_KEY:}
      chat:
        enabled: true
        options:
          model: ${OPENAI_CHAT_MODEL:gpt-4o}
          temperature: 0.7

    # Anthropic Claude 사용 시 (대안)
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:}
      chat:
        enabled: ${ANTHROPIC_CHAT_ENABLED:false}
        options:
          model: ${ANTHROPIC_CHAT_MODEL:claude-sonnet-4-5-20250929}
          max-tokens: 4096

    # Ollama 사용 시 (로컬 개발)
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      chat:
        enabled: ${OLLAMA_CHAT_ENABLED:false}
        options:
          model: ${OLLAMA_CHAT_MODEL:llama3.2}

# LLM 기능 활성화
llm:
  enabled: ${LLM_ENABLED:true}

# Rate Limiting
resilience4j:
  ratelimiter:
    instances:
      llm-api:
        limit-for-period: 10
        limit-refresh-period: 1m
        timeout-duration: 5s
```

### Provider 전환 방법

**OpenAI 사용 (기본)**:
```bash
OPENAI_API_KEY=sk-proj-...
```

**Anthropic Claude 사용**:
```bash
ANTHROPIC_API_KEY=sk-ant-...
ANTHROPIC_CHAT_ENABLED=true
# OpenAI를 비활성화하려면 OPENAI_API_KEY를 비워두거나 spring.ai.openai.chat.enabled=false
```

**Ollama 로컬 사용**:
```bash
OLLAMA_CHAT_ENABLED=true
OLLAMA_CHAT_MODEL=llama3.2
```

---

## 6. 의존성

### build.gradle

```gradle
dependencies {
    // Spring AI BOM
    implementation platform("org.springframework.ai:spring-ai-bom:1.1.0")

    // Spring AI Core (필수)
    implementation 'org.springframework.ai:spring-ai-core'

    // Provider 선택 (하나 이상)
    implementation 'org.springframework.ai:spring-ai-openai-spring-boot-starter'
    implementation 'org.springframework.ai:spring-ai-anthropic-spring-boot-starter'
    // implementation 'org.springframework.ai:spring-ai-ollama-spring-boot-starter'

    // WebFlux for SSE streaming
    implementation 'org.springframework.boot:spring-boot-starter-webflux'

    // Rate Limiting
    implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.2.0'
    implementation 'io.github.resilience4j:resilience4j-ratelimiter:2.2.0'
}
```

---

## 7. 테스트

### LlmServiceTest

```java
@SpringBootTest
class LlmServiceTest {

    @Autowired
    private LlmService llmService;

    @MockBean
    private ChatClient chatClient;

    @MockBean
    private ChatClient.Builder chatClientBuilder;

    @Test
    void testChat_withToolCall() {
        // Given
        String userMessage = "README 파일을 찾아줘";
        UUID projectId = UUID.randomUUID();

        when(chatClient.prompt()).thenReturn(mockPromptSpec);
        when(mockPromptSpec.user(anyString())).thenReturn(mockPromptSpec);
        when(mockPromptSpec.tools(any())).thenReturn(mockPromptSpec);
        when(mockPromptSpec.toolContext(anyMap())).thenReturn(mockPromptSpec);
        when(mockPromptSpec.call()).thenReturn(mockCallResponse);
        when(mockCallResponse.content()).thenReturn("README.md 파일을 찾았습니다.");

        // When
        String response = llmService.chat(userMessage, projectId, "session-1");

        // Then
        assertThat(response).contains("README");
        verify(mockPromptSpec).tools(any(DocumentTools.class), any(), any());
    }
}
```

---

## 구현 체크리스트

### Week 1-2: 기초 구현
- [ ] Spring AI 의존성 추가 (1.1.0+)
- [ ] LlmConfig (ChatClient Bean)
- [ ] LlmService (chat, streamChat)
- [ ] DocumentTools (@Tool 어노테이션)
- [ ] SearchTools (@Tool 어노테이션)
- [ ] LlmController (REST API)

### Week 3-4: Git Tools & Integration
- [ ] GitTools (@Tool 어노테이션)
- [ ] BranchService
- [ ] Tool Context 전달 테스트
- [ ] Rate Limiting

### Week 5-6: 고급 기능
- [ ] PullRequestService (GitHub API)
- [ ] MergeService (Conflict 해결)
- [ ] 세션 관리 (ChatMemory 영속화)
- [ ] 에러 처리 고도화

### Week 7-8: 테스트 & 문서화
- [ ] 단위 테스트
- [ ] 통합 테스트
- [ ] API 문서 (Swagger)
- [ ] README 업데이트

---

## 참고 자료

- [Spring AI 1.1.0 ChatClient API](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [FunctionCallback → ToolCallback 마이그레이션](https://docs.spring.io/spring-ai/reference/api/tools-migration.html)
- [Spring AI 1.1.1 릴리스 노트](https://spring.io/blog/2025/12/05/spring-ai-1-1-1-available-now/)
- [ToolCallAdvisor 사용법](https://spring.io/blog/2025/11/04/spring-ai-recursive-advisors/)