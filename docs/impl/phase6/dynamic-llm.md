# Phase 6 동적 LLM API Key 관리

> Credential 기반 프로젝트별 LLM API Key 관리

---

## 개요

Phase 6에서는 OpenAI Chat API Key를 동적으로 관리하도록 변경했습니다.

**변경 전 (Week 1)**:
- OpenAI API Key를 `application.yml` 또는 환경 변수에 정적으로 설정
- 모든 프로젝트가 동일한 API Key 사용
- 보안 위험: yml 파일이나 환경 변수에 노출

**변경 후 (Week 2)**:
- OpenAI API Key를 `dm_credential` 테이블에 암호화 저장
- 프로젝트별 또는 시스템 레벨로 API Key 관리
- 웹 UI에서 Credential 관리
- 보안 강화: AES-256 암호화

---

## 아키텍처

### 전체 흐름

```
User → Playground → LlmController → LlmService
                                        │
                                        ▼
                              DynamicChatClientFactory.getChatClient(projectId)
                                        │
                                        ├── DynamicCredentialResolver.resolveApiKey(projectId, OPENAI_API_KEY)
                                        │     │
                                        │     └──> dm_credential 조회 (프로젝트 > 시스템 우선순위)
                                        │           └──> EncryptionService.decrypt()
                                        │
                                        ├── OpenAiApi.builder().apiKey(...).build()
                                        ├── OpenAiChatOptions.builder()...
                                        ├── OpenAiChatModel 생성
                                        │
                                        └── ChatClient.builder(chatModel)
                                              .defaultSystem(...)
                                              .defaultAdvisors(ChatMemory)
                                              .build()
```

### 캐싱 전략

- `DynamicChatClientFactory`는 프로젝트별 ChatClient를 캐시
- `ConcurrentHashMap<UUID, ChatClient>`
- 크리덴셜 변경 시 `invalidateCache(projectId)` 호출 필요

---

## 주요 구현

### 1. DynamicChatClientFactory

**파일**: `backend/src/main/java/com/docst/llm/DynamicChatClientFactory.java`

**역할**:
- 프로젝트별 ChatClient 동적 생성
- Credential 기반 API Key 조회
- ChatClient 캐싱

**주요 메서드**:

```java
public ChatClient getChatClient(UUID projectId) {
    return chatClientCache.computeIfAbsent(projectId, this::createChatClient);
}

private ChatClient createChatClient(UUID projectId) {
    ChatModel chatModel = createChatModel(projectId);

    // Spring AI 1.1.0 Best Practice:
    // Available tools는 자동으로 LLM에 전달되므로 System Prompt에 명시 불필요
    return ChatClient.builder(chatModel)
        .defaultSystem("""
            You are a helpful documentation assistant for the Docst system.
            You can search and read documents using the provided tools.

            Always respond in the same language as the user's query.
            When using tools, explain what you're doing in a clear and concise manner.
            """)
        .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
        .build();
}

private ChatModel createChatModel(UUID projectId) {
    // 1. SystemConfig에서 LLM Provider 조회 (Enum 사용으로 타입 안전)
    String providerString = systemConfigService.getString("llm.provider", "openai");
    LlmProvider provider = LlmProvider.fromString(providerString);

    // 2. Provider별 ChatModel 생성
    return switch (provider) {
        case OPENAI -> createOpenAiChatModel(projectId);
        case OLLAMA -> throw new UnsupportedOperationException("Ollama not yet supported");
        case ANTHROPIC -> throw new UnsupportedOperationException("Anthropic not yet supported");
    };
}

private ChatModel createOpenAiChatModel(UUID projectId) {
    // 1. Credential에서 API Key 조회
    String apiKey = credentialResolver.resolveApiKey(projectId, CredentialType.OPENAI_API_KEY);

    // 2. OpenAI API 클라이언트 생성
    OpenAiApi openAiApi = OpenAiApi.builder().apiKey(apiKey).build();

    // 3. ChatOptions 설정
    OpenAiChatOptions options = OpenAiChatOptions.builder()
        .model("gpt-4o")
        .temperature(0.7)
        .maxTokens(4096)
        .build();

    // 4. ChatModel 생성
    return new OpenAiChatModel(
        openAiApi,
        options,
        null,  // ToolCallingManager
        RetryUtils.DEFAULT_RETRY_TEMPLATE,
        ObservationRegistry.NOOP
    );
}
```

### 2. LlmService 수정

**변경 전**:
```java
@RequiredArgsConstructor
public class LlmService {
    private final ChatClient chatClient;  // 정적 Bean 주입

    public String chat(String message, UUID projectId, String sessionId) {
        return chatClient.prompt()
            .user(message)
            .toolNames("searchDocuments", ...)
            .call()
            .content();
    }
}
```

**변경 후**:
```java
@RequiredArgsConstructor
public class LlmService {
    private final DynamicChatClientFactory chatClientFactory;  // Factory 주입

    public String chat(String message, UUID projectId, String sessionId) {
        // 프로젝트별 ChatClient 가져오기
        ChatClient chatClient = chatClientFactory.getChatClient(projectId);

        return chatClient.prompt()
            .user(message)
            .toolNames("searchDocuments", ...)
            .call()
            .content();
    }
}
```

### 3. LlmConfig 수정

**변경 전**:
```java
@Configuration
public class LlmConfig {
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
        return builder
            .defaultSystem("...")
            .defaultAdvisors(...)
            .build();
    }

    @Bean
    public ChatMemory chatMemory() { ... }
}
```

**변경 후**:
```java
@Configuration
public class LlmConfig {
    // ChatClient Bean 제거
    // ChatMemory Bean만 유지

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
            .maxMessages(20)
            .build();
    }
}
```

### 4. application.yml 수정

**변경 전**:
```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:}
      chat:
        enabled: true
        options:
          model: gpt-4o
```

**변경 후**:
```yaml
spring:
  autoconfigure:
    exclude:
      # OpenAI ChatAutoConfiguration 비활성화
      - org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration

  ai:
    # OpenAI 설정 제거
    # Credential 관리로 이동

docst:
  llm:
    enabled: true
```

---

## Credential 관리

### 1. API Key 등록 (웹 UI)

**경로**: Settings → Credentials → Add Credential

**입력 항목**:
- **Type**: `OPENAI_API_KEY`
- **Name**: "OpenAI Main Key" (식별용)
- **Secret**: `sk-proj-your-actual-key-here`
- **Scope**:
  - `SYSTEM`: 모든 프로젝트 공용
  - `PROJECT`: 특정 프로젝트 전용

### 2. API Key 조회 우선순위

`DynamicCredentialResolver.resolveApiKey(projectId, CredentialType.OPENAI_API_KEY)`:

1. **프로젝트 레벨 Credential** (dm_credential where project_id = ? AND type = ? AND scope = 'PROJECT')
2. **시스템 레벨 Credential** (dm_credential where scope = 'SYSTEM' AND type = ?)
3. **없으면 예외 발생**: `IllegalStateException("No credential found for type OPENAI_API_KEY")`

### 3. 암복호화

**암호화**: `EncryptionService.encrypt(plainText)`
- Algorithm: AES-256-GCM
- Key: `DOCST_ENCRYPTION_KEY` 환경 변수

**복호화**: `EncryptionService.decrypt(encryptedText)`

---

## API 사용 예시

### 1. 시스템 레벨 Credential 생성

```bash
curl -X POST http://localhost:8342/api/admin/credentials/system \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <admin-token>" \
  -d '{
    "name": "OpenAI Main Key",
    "type": "OPENAI_API_KEY",
    "secret": "sk-proj-...",
    "description": "System-wide OpenAI API Key"
  }'
```

### 2. 프로젝트 레벨 Credential 생성

```bash
curl -X POST http://localhost:8342/api/projects/{projectId}/credentials \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "name": "Project A OpenAI Key",
    "type": "OPENAI_API_KEY",
    "secret": "sk-proj-different-key",
    "description": "Project A specific OpenAI key"
  }'
```

### 3. LLM 채팅 (자동으로 프로젝트별 API Key 사용)

```bash
curl -X POST http://localhost:8342/api/llm/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "message": "프로젝트의 모든 문서를 나열해줘",
    "projectId": "abc-123-...",
    "sessionId": "session-1"
  }'
```

---

## 마이그레이션 가이드

### 기존 환경 변수 → Credential 관리

**Before:**
```bash
# .env 파일
OPENAI_API_KEY=sk-proj-...
```

**After:**
1. `.env` 파일에서 `OPENAI_API_KEY` 제거
2. 웹 UI Settings → Credentials → Add Credential
3. Type: `OPENAI_API_KEY`, Secret: `sk-proj-...`, Scope: `SYSTEM`

---

## LlmProvider Enum (타입 안전성)

**파일**: `backend/src/main/java/com/docst/llm/LlmProvider.java`

### 개요

Provider를 문자열 하드코딩 대신 Enum으로 관리하여 타입 안전성을 확보합니다.

### 코드

```java
public enum LlmProvider {
    OPENAI("openai"),
    ANTHROPIC("anthropic"),
    OLLAMA("ollama");

    private final String value;

    LlmProvider(String value) {
        this.value = value;
    }

    public static LlmProvider fromString(String value) {
        if (value == null || value.isBlank()) {
            return OPENAI;  // 기본값
        }

        for (LlmProvider provider : values()) {
            if (provider.value.equalsIgnoreCase(value)) {
                return provider;
            }
        }

        throw new IllegalArgumentException(
            "Unknown LLM provider: " + value + ". " +
            "Supported providers: openai, anthropic, ollama"
        );
    }
}
```

### 이점

1. **타입 안전성**: 컴파일 타임에 잘못된 Provider 사용 방지
2. **IDE 자동완성**: Provider 목록 자동 제시
3. **유지보수성**: 새 Provider 추가 시 enum에만 추가
4. **명확한 에러 메시지**: 잘못된 값 입력 시 지원 Provider 목록 출력

### 사용 예시

```java
// SystemConfig에서 문자열로 조회
String providerString = systemConfigService.getString("llm.provider", "openai");

// Enum으로 변환 (타입 안전)
LlmProvider provider = LlmProvider.fromString(providerString);

// Switch expression (컴파일러가 모든 case 확인)
return switch (provider) {
    case OPENAI -> createOpenAiChatModel(projectId);
    case OLLAMA -> throw new UnsupportedOperationException("...");
    case ANTHROPIC -> throw new UnsupportedOperationException("...");
};
```

---

## Spring AI 1.1.0 Best Practice

### 1. Tool 정보 자동 전달

**Before** (잘못된 방법):
```java
.defaultSystem("""
    You are a helpful assistant.

    Available tools:
    - searchDocuments: Search documents in a project using keywords
    - listDocuments: List all documents in the project
    - getDocument: Get the full content of a specific document
    """)
```

**After** (권장 방법):
```java
.defaultSystem("""
    You are a helpful documentation assistant for the Docst system.
    You can search and read documents using the provided tools.

    Always respond in the same language as the user's query.
    When using tools, explain what you're doing in a clear and concise manner.
    """)
```

**이유**:
- Spring AI가 Function Bean의 `@Description`을 자동으로 LLM에 전달
- System prompt에 도구 목록 하드코딩 불필요
- `@Description`만 상세하게 작성하면 됨

### 2. Function Bean Description 작성

```java
@Configuration
public class LlmToolsConfig {

    @Bean
    @Description("Search documents in a project using keywords. " +
                 "Returns top matching documents with snippets.")
    public Function<SearchDocumentsRequest, SearchDocumentsResponse> searchDocuments() {
        return request -> {
            // 구현
        };
    }

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

### 3. Tool 등록 방법

**LlmService에서 사용**:
```java
public String chat(String userMessage, UUID projectId, String sessionId) {
    ChatClient chatClient = chatClientFactory.getChatClient(projectId);

    return chatClient.prompt()
        .user(userMessage)
        .toolNames("searchDocuments", "listDocuments", "getDocument")  // Function Bean 이름으로 등록
        .advisors(spec -> spec
            .param("projectId", projectId.toString())
            .param("sessionId", sessionId)
        )
        .call()
        .content();
}
```

**자동 Tool Calling 흐름**:
1. LLM이 사용자 질문 분석
2. 필요한 Tool 자동 선택 (Spring AI가 Tool description 전달)
3. Tool 실행 후 결과를 LLM에 전달
4. LLM이 Tool 결과를 기반으로 최종 답변 생성

---

## 알려진 제약사항

### 1. Ollama 지원 미완성

**현재 상태**: `LlmProvider.OLLAMA` → `UnsupportedOperationException`

**이유**: Spring AI 1.1.0 Ollama Chat API 확인 필요

**TODO**: `DynamicChatClientFactory.createOllamaChatModel()` 구현

### 2. ChatClient 캐시 무효화

**현재**: Credential 변경 시 자동 캐시 무효화 없음

**해결 필요**:
- CredentialService에서 update/delete 시 `chatClientFactory.invalidateCache(projectId)` 호출
- 또는 Event 기반 캐시 무효화

### 3. 프로젝트 삭제 시 캐시 정리

**현재**: 프로젝트 삭제 시 캐시에 남아 있음

**해결 필요**:
- ProjectService에서 delete 시 캐시 무효화
- 또는 WeakHashMap 사용 고려

---

## 보안 고려사항

### 1. API Key 암호화

- **Algorithm**: AES-256-GCM
- **Key 관리**: 환경 변수 (`DOCST_ENCRYPTION_KEY`)
- **중요**: Encryption Key는 외부 노출 금지

### 2. 권한 관리

- **시스템 레벨 Credential**: ADMIN 권한 필요
- **프로젝트 레벨 Credential**: PROJECT_ADMIN 권한 필요
- **Credential 조회**: 소유자만 가능 (CredentialService 권한 확인)

### 3. 감사 로그

`CredentialService`에서 모든 Credential 변경 시 로그 출력:
```java
log.info("AUDIT: Created system credential: {}", name);
log.info("AUDIT: Updated project credential: {} in project {}", name, projectId);
log.info("AUDIT: Deleted system credential: {}", name);
```

---

## 참고 자료

- [Spring AI 1.1.0 ChatClient API](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Phase 4-E: Dynamic Embedding](../phase4/phase4-e-dynamic-embedding.md)
- [Credential 엔티티 스키마](../../database/schema.md#dm_credential)
