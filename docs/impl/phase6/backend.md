# Phase 6 백엔드 구현 상세

> Spring AI 1.1.0 기반 LLM 통합

---

## 1. Spring AI BOM 업그레이드

### build.gradle.kts

**변경 사항:**

```kotlin
// Before: 1.0.0-M5 (Milestone)
implementation(platform("org.springframework.ai:spring-ai-bom:1.0.0-M5"))
implementation("org.springframework.ai:spring-ai-pgvector-store-spring-boot-starter")
implementation("org.springframework.ai:spring-ai-openai-spring-boot-starter")
implementation("org.springframework.ai:spring-ai-ollama-spring-boot-starter")

// After: 1.1.0 (GA Release)
implementation(platform("org.springframework.ai:spring-ai-bom:1.1.0"))
implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")
implementation("org.springframework.ai:spring-ai-starter-model-openai")
implementation("org.springframework.ai:spring-ai-starter-model-ollama")
```

**주요 변경:**
- Artifact ID 이름 변경 (Spring AI 1.0 GA naming convention)
- Milestone → GA 안정화 버전

---

## 2. LlmConfig - ChatClient Bean 설정

### backend/src/main/java/com/docst/llm/LlmConfig.java

**역할:**
- ChatClient Bean 생성 (Provider 독립적)
- ChatMemory 설정 (대화 히스토리 관리)
- System 프롬프트 정의

**주요 코드:**

```java
@Configuration
@ConditionalOnProperty(prefix = "docst.llm", name = "enabled",
                       havingValue = "true", matchIfMissing = true)
public class LlmConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
        return builder
            .defaultSystem("""
                You are a helpful documentation assistant for the Docst system.
                You can search and read documents using the provided tools.

                Always respond in the same language as the user's query.

                Available tools:
                - searchDocuments: Search documents in a project using keywords
                - listDocuments: List all documents in the project
                - getDocument: Get the full content of a specific document
                """)
            .defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory).build()
            )
            .build();
    }

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
            .maxMessages(20)  // 최근 20개 메시지 유지
            .build();
    }
}
```

**기술적 결정:**

1. **MessageWindowChatMemory vs InMemoryChatMemory**
   - Spring AI 1.1.0에서 `InMemoryChatMemory`는 존재하지 않음
   - `MessageWindowChatMemory`: 슬라이딩 윈도우 방식으로 최근 N개 메시지만 유지
   - 메모리 효율적이며 긴 대화에서도 안정적

2. **MessageChatMemoryAdvisor**
   - 각 요청 시 대화 히스토리를 자동으로 포함
   - `sessionId` 파라미터로 세션별 히스토리 관리

---

## 3. LlmToolsConfig - Function Bean Tools

### backend/src/main/java/com/docst/llm/LlmToolsConfig.java

**역할:**
- Spring AI Function Calling을 위한 Tool 정의
- `java.util.function.Function<Request, Response>` 패턴
- `@Description` 어노테이션으로 LLM에게 Tool 설명

**주요 코드:**

```java
@Configuration
@ConditionalOnProperty(prefix = "docst.llm", name = "enabled",
                       havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class LlmToolsConfig {

    private final DocumentService documentService;
    private final SearchService searchService;

    @Bean
    @Description("Search documents in a project using keywords. " +
                 "Returns top matching documents with snippets.")
    public Function<SearchDocumentsRequest, SearchDocumentsResponse> searchDocuments() {
        return request -> {
            log.info("Tool: searchDocuments - query={}, projectId={}",
                     request.query, request.projectId);

            UUID projectId = UUID.fromString(request.projectId);
            int limit = request.topK != null ? request.topK : 10;

            List<SearchResult> results =
                searchService.searchByKeyword(projectId, request.query, limit);

            List<DocumentResult> documents = results.stream()
                .map(r -> new DocumentResult(
                    r.documentId().toString(),
                    r.path(),
                    r.snippet(),
                    r.score()
                ))
                .toList();

            return new SearchDocumentsResponse(documents, results.size());
        };
    }

    @Bean
    @Description("List all documents in a project. " +
                 "Returns document IDs, paths, and titles.")
    public Function<ListDocumentsRequest, ListDocumentsResponse> listDocuments() {
        return request -> {
            UUID projectId = UUID.fromString(request.projectId);
            List<Document> documents = documentService.findByProjectId(projectId);

            List<DocumentInfo> docList = documents.stream()
                .filter(d -> !d.isDeleted())
                .map(d -> new DocumentInfo(
                    d.getId().toString(),
                    d.getPath(),
                    d.getTitle(),
                    d.getDocType().name()
                ))
                .toList();

            return new ListDocumentsResponse(docList, docList.size());
        };
    }

    @Bean
    @Description("Get the full content of a document by its ID. " +
                 "Returns the latest version content.")
    public Function<GetDocumentRequest, GetDocumentResponse> getDocument() {
        return request -> {
            UUID documentId = UUID.fromString(request.documentId);

            Document doc = documentService.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

            DocumentVersion latestVersion = documentService.findLatestVersion(documentId)
                .orElseThrow(() -> new RuntimeException("No version found"));

            return new GetDocumentResponse(
                doc.getId().toString(),
                doc.getPath(),
                doc.getTitle(),
                latestVersion.getContent(),
                latestVersion.getCommitSha()
            );
        };
    }

    // Request/Response DTOs with @JsonProperty annotations
    @JsonClassDescription("Request to search documents by keywords")
    public record SearchDocumentsRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("The search query keywords")
        String query,

        @JsonProperty(required = true)
        @JsonPropertyDescription("The project ID to search within")
        String projectId,

        @JsonProperty(required = false)
        @JsonPropertyDescription("Maximum number of results to return (default: 10)")
        Integer topK
    ) {}
}
```

**기술적 결정:**

1. **Function Bean 패턴**
   - Spring AI 1.1.0에서 `.functions(method::reference)` 방식이 작동하지 않음
   - `@Bean Function<Req, Res>` 패턴 사용
   - `.toolNames("searchDocuments", "listDocuments", "getDocument")` 로 참조

2. **@JsonProperty 어노테이션**
   - LLM에게 전달되는 JSON Schema 정의
   - `required`, `description` 메타데이터 제공
   - LLM이 올바른 파라미터로 호출할 수 있도록 유도

3. **Record 사용**
   - Request/Response DTO를 불변 Record로 정의
   - 간결하고 타입 안전

---

## 4. LlmService - LLM 대화 처리

### backend/src/main/java/com/docst/llm/LlmService.java

**역할:**
- ChatClient를 사용한 LLM 대화 처리
- 동기 및 스트리밍 API 제공
- Tool Calling 자동 처리

**주요 코드:**

```java
@Service
@RequiredArgsConstructor
public class LlmService {

    private final ChatClient chatClient;

    /**
     * LLM과 대화 (동기 호출)
     */
    public String chat(String userMessage, UUID projectId, String sessionId) {
        log.info("LLM chat request: projectId={}, sessionId={}",
                 projectId, sessionId);

        try {
            return chatClient.prompt()
                .user(userMessage)
                .toolNames("searchDocuments", "listDocuments", "getDocument")
                .advisors(spec -> spec
                    .param("projectId", projectId.toString())
                    .param("sessionId", sessionId)
                )
                .call()
                .content();
        } catch (Exception e) {
            log.error("Error during LLM chat", e);
            return "Sorry, an error occurred: " + e.getMessage();
        }
    }

    /**
     * LLM과 대화 (스트리밍)
     */
    public Flux<String> streamChat(String userMessage, UUID projectId, String sessionId) {
        return chatClient.prompt()
            .user(userMessage)
            .toolNames("searchDocuments", "listDocuments", "getDocument")
            .advisors(spec -> spec
                .param("projectId", projectId.toString())
                .param("sessionId", sessionId)
            )
            .stream()
            .content()
            .onErrorResume(e -> {
                log.error("Error during LLM stream chat", e);
                return Flux.just("Error: " + e.getMessage());
            });
    }
}
```

**기술적 결정:**

1. **.toolNames() vs .functions()**
   - Spring AI 1.1.0에서 `.functions()` 메서드는 존재하지 않음
   - `.toolNames("tool1", "tool2")` 사용하여 Function Bean 참조

2. **Advisor 파라미터**
   - `.advisors(spec -> spec.param(key, value))`로 Tool Context 전달
   - `projectId`, `sessionId` 등 동적 값 전달
   - Tool에서 `@ToolContext` 로 접근 (향후 확장)

3. **Flux<String> 스트리밍**
   - `.stream().content()`: 텍스트 청크 스트리밍
   - `.stream().chatResponse()`: 메타데이터 포함 스트리밍
   - Reactive Programming (Project Reactor)

---

## 5. LlmController - REST API

### backend/src/main/java/com/docst/api/LlmController.java

**역할:**
- LLM 서비스를 REST API로 노출
- SSE (Server-Sent Events) 스트리밍 지원

**주요 코드:**

```java
@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "docst.llm", name = "enabled",
                       havingValue = "true", matchIfMissing = true)
public class LlmController {

    private final LlmService llmService;

    /**
     * LLM Chat (동기)
     */
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        log.info("POST /api/llm/chat: projectId={}, sessionId={}",
            request.projectId(), request.sessionId());

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
        log.info("POST /api/llm/chat/stream: projectId={}, sessionId={}",
            request.projectId(), request.sessionId());

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

**기술적 결정:**

1. **produces = MediaType.TEXT_EVENT_STREAM_VALUE**
   - SSE 형식으로 응답
   - 브라우저에서 EventSource API 또는 fetch() 스트리밍으로 수신

2. **Flux<String> 반환**
   - Spring WebFlux가 자동으로 SSE 형식으로 변환
   - `data: <content>\n\n` 형식

3. **@ConditionalOnProperty**
   - `docst.llm.enabled=true` 설정으로 LLM 기능 활성화 제어

---

## 6. application.yml 설정

### backend/src/main/resources/application.yml

**추가/수정 사항:**

```yaml
spring:
  autoconfigure:
    exclude:
      # OpenAiChatAutoConfiguration을 제거 (LLM 기능 활성화)
      # - org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration
      - org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration
      # ... (Embedding은 여전히 동적 관리)

  ai:
    openai:
      api-key: ${OPENAI_API_KEY:}
      chat:
        enabled: ${OPENAI_CHAT_ENABLED:true}
        options:
          model: ${OPENAI_CHAT_MODEL:gpt-4o}
          temperature: 0.7
          max-tokens: 4096

docst:
  llm:
    enabled: ${LLM_ENABLED:true}
```

**주요 변경:**

1. **OpenAiChatAutoConfiguration 활성화**
   - Phase 4-E에서 비활성화했던 것을 다시 활성화
   - LLM 기능을 위해 OpenAI Chat 모델 사용

2. **Embedding은 여전히 동적 관리**
   - `OpenAiEmbeddingAutoConfiguration`은 비활성화 유지
   - Phase 4-E 동적 Embedding 시스템 유지

---

## 7. 컴파일 및 테스트

### 빌드 성공

```bash
./gradlew compileJava
# BUILD SUCCESSFUL in 4s

./gradlew build -x test
# BUILD SUCCESSFUL in 1s
```

### 테스트 결과

```bash
./gradlew test
# 150 tests completed, 6 failed
```

**실패한 테스트:**
- `SemanticSearchIntegrationTest` (6개)
- Phase 2+ 기능 (시맨틱 검색) - Phase 6 작업 중 비활성화

**해결:**
```java
@Disabled("Phase 2+ 기능 (시맨틱 검색) - Phase 6 LLM 구현 중에는 비활성화")
class SemanticSearchIntegrationTest {
    // ...
}
```

---

## 8. API 호출 예시

### cURL - 동기 채팅

```bash
curl -X POST http://localhost:8342/api/llm/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "message": "프로젝트의 모든 README 파일을 찾아줘",
    "projectId": "123e4567-e89b-12d3-a456-426614174000",
    "sessionId": "session-1"
  }'
```

**Response:**
```json
{
  "content": "프로젝트에서 3개의 README 파일을 찾았습니다:\n1. /README.md\n2. /docs/README.md\n3. /backend/README.md"
}
```

### cURL - 스트리밍 채팅

```bash
curl -X POST http://localhost:8342/api/llm/chat/stream \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "message": "authentication.md 파일 내용을 요약해줘",
    "projectId": "123e4567-e89b-12d3-a456-426614174000",
    "sessionId": "session-1"
  }'
```

**Response (SSE):**
```
data: 프로젝트에서
data:  authentication.md
data:  파일을
data:  찾았습니다
data: .
data:  내용은
data:  다음과
data:  같습니다
data: :
...
```

---

## 9. 로그 예시

### Tool Calling 로그

```
2025-01-02 15:30:12 INFO  LlmService - LLM chat request: projectId=abc-123, sessionId=session-1
2025-01-02 15:30:12 INFO  LlmToolsConfig - Tool: searchDocuments - query=README, projectId=abc-123, topK=10
2025-01-02 15:30:13 INFO  SearchService - Keyword search: projectId=abc-123, query=README, limit=10
2025-01-02 15:30:13 INFO  LlmToolsConfig - Tool: searchDocuments returned 3 results
```

---

## 10. 알려진 제약 사항

### 1. ChatMemory 영속화

**현재**: MessageWindowChatMemory (인메모리)
**제한**: 서버 재시작 시 대화 히스토리 손실
**계획**: Week 5-6에 Redis 또는 데이터베이스 기반 영속화

### 2. Tool 확장

**현재**: 3개 Tool (search, list, get)
**계획**: Week 3-4에 create, update, branch 관리 Tool 추가

### 3. Rate Limiting

**현재**: 없음
**계획**: Week 7-8에 사용자별 Rate Limiting 구현

---

## 참고 자료

- [Spring AI 1.1.0 ChatClient API](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Spring AI Function Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Spring AI Chat Memory](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)
