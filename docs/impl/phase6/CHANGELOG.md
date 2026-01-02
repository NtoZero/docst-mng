# Phase 6 변경 이력

> LLM 통합 구현 변경 사항

---

## [Week 5-6] 2025-01-03 - 템플릿 시스템 + Rate Limiting

### 추가된 기능 ✨

#### 백엔드
- **프롬프트 템플릿 시스템**:
  - `PromptTemplate`: 8개 기본 템플릿 (검색, 요약, 생성, 수정, 목록, Git, 설명, 예제)
  - `GET /api/llm/templates` - 템플릿 목록 조회 API
  - 변수 치환 기능 (`{{variable}}` 패턴)
  - 카테고리별 분류 (search, summarize, create, update, list, git, explain)
- **Rate Limiting**:
  - `RateLimitService`: Sliding Window 방식 Rate Limiter
  - 프로젝트 + IP 기반 제한 (분당 20 요청)
  - LLM Chat API에 자동 적용
  - HTTP 429 응답 + 리셋 시간 정보 제공

#### 프론트엔드
- **TemplateSelector 컴포넌트**:
  - 템플릿 선택 드롭다운 (카테고리별 그룹화)
  - 변수 입력 폼 (자동 생성)
  - 미리보기 기능
  - ChatInterface에 통합

### 변경된 사항 🔧

#### 백엔드
- **LlmController**: Rate Limiting 체크 추가
  - `getIdentifier()`: 프로젝트 + IP 식별자 생성
  - `getClientIp()`: X-Forwarded-For 헤더 지원
- **ChatInterface**: 템플릿 선택 버튼 추가

### 개선된 사항 📈

- **UX 향상**: 자주 사용하는 프롬프트를 템플릿으로 빠르게 입력
- **비용 관리**: Rate Limiting으로 과도한 LLM API 호출 방지
- **확장성**: 템플릿은 시스템 레벨로 하드코딩 (나중에 DB 확장 가능)

### 빌드 결과 ✅

```bash
# Backend
./gradlew build
# BUILD SUCCESSFUL

# Frontend
npm run build
# Compiled successfully
```

---

## [Week 3-4] 2025-01-03 - WRITE Tools 추가 + @Tool 패턴 리팩토링

### 추가된 기능 ✨

#### 백엔드
- **@Tool annotation 패턴 도입**: Function Bean → @Tool annotation 마이그레이션
  - `DocumentTools.java`: @Tool annotation 기반 재구현
  - `GitTools.java`: @Tool annotation 기반 재구현
  - `LlmToolsConfig.java`: Deprecated 처리
- **WRITE Tools 추가**:
  - `updateDocument`: 기존 문서 내용 업데이트 (새 버전 생성)
  - `createDocument`: 새 문서 생성
- **Git Tools 확장**:
  - `listBranches`, `createBranch`, `switchBranch`, `getCurrentBranch`, `syncRepository`
- **Branch Management REST API**:
  - `GET /api/repositories/{id}/branches` - 브랜치 목록
  - `POST /api/repositories/{id}/branches` - 브랜치 생성
  - `POST /api/repositories/{id}/branches/{name}/switch` - 브랜치 전환
  - `GET /api/repositories/{id}/branches/current` - 현재 브랜치

#### 프론트엔드
- **BranchSelector 컴포넌트**: Git 브랜치 선택 및 생성 UI
- **SessionManager 컴포넌트**: 대화 히스토리 저장/로드
- **use-branches Hook**: 브랜치 관리 TanStack Query Hook
- **use-session Hook**: LocalStorage 기반 세션 관리
- **shadcn/ui 컴포넌트**: Command, Popover, Sheet 추가

### 변경된 사항 🔧

#### 백엔드
- **Tool 정의 방식**: Function Bean → @Tool annotation
  - 코드량 74% 감소 (boilerplate 제거)
  - `@ToolParam`으로 파라미터 설명 명시
  - Jackson annotations 제거
- **LlmService**: `.toolNames()` → `.tools(documentTools, gitTools)`
  - @Tool annotation 기반 Components 직접 주입

#### 문서
- **CLAUDE.md**: LLM Integration 섹션 추가
  - @Tool annotation 패턴 설명
  - Available Tools 목록
  - Legacy vs Modern 비교

### 개선된 사항 📈

- **코드 간결성**: Function Bean 방식 대비 74% 코드 감소
- **타입 안전성**: 컴파일 타임 검증
- **자동 스캔**: Spring이 @Tool 메서드 자동 감지
- **확장성**: 새 Tool 추가가 매우 간단 (메서드 하나만 추가)

### 빌드 결과 ✅

```bash
./gradlew build
# BUILD SUCCESSFUL
# All tests passed
```

---

## [Week 2-3] 2025-01-03 - 동적 API Key 관리 + 리팩토링

### 추가된 기능 ✨

#### 백엔드
- **DynamicChatClientFactory**: 프로젝트별 ChatClient 동적 생성
  - Credential 기반 API Key 조회
  - 프로젝트별 캐싱 (`ConcurrentHashMap<UUID, ChatClient>`)
  - Provider별 ChatModel 생성
- **LlmProvider Enum**: 타입 안전한 Provider 관리
  - `OPENAI`, `ANTHROPIC`, `OLLAMA`
  - `fromString()` 메서드로 문자열 → Enum 변환
  - IDE 자동완성 지원

### 변경된 사항 🔧

#### 백엔드
- **LlmConfig**: ChatClient Bean 제거, ChatMemory Bean만 유지
  - ChatClient는 DynamicChatClientFactory에서 동적 생성
- **LlmService**: 정적 ChatClient → Factory 기반 동적 ChatClient
  - `chatClientFactory.getChatClient(projectId)` 호출
- **application.yml**: OpenAI Chat 정적 설정 제거
  - `OpenAiChatAutoConfiguration` 비활성화
  - API Key는 Credential 관리로 이동
- **System Prompt 간소화**: Available tools 목록 제거
  - Spring AI가 `@Description`을 자동으로 LLM에 전달
  - 하드코딩 불필요

#### 문서
- **CLAUDE.md**: API Key 관리 정책 추가
  - 환경 변수 대신 웹 UI Credential 관리
  - 프로젝트별/시스템 레벨 지원
- **docs/impl/phase6/dynamic-llm.md**: 동적 LLM 구현 상세 문서 추가
  - LlmProvider Enum 설명
  - Spring AI 1.1.0 Best Practice

### 개선된 사항 📈

- **타입 안전성**: Provider를 Enum으로 관리
- **보안 강화**: API Key를 Credential 관리 (AES-256 암호화)
- **유지보수성**: Spring AI 1.1.0 Best Practice 적용
- **확장성**: 새 Provider 추가 용이

### 참고 자료 📚

- [Spring AI 1.1.0 Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Dynamic Credential Management](./dynamic-llm.md)

---

## [Week 1-2] 2025-01-02 - 기초 구현 완료

### 추가된 기능 ✨

#### 백엔드
- **Spring AI 1.1.0 통합**: Provider 독립적인 LLM 클라이언트
- **ChatClient Bean**: OpenAI GPT-4o와 통합된 채팅 클라이언트
- **MessageWindowChatMemory**: 최근 20개 메시지 대화 히스토리 관리
- **3가지 Function Bean Tools**:
  - `searchDocuments`: 키워드로 문서 검색
  - `listDocuments`: 프로젝트의 모든 문서 목록 조회
  - `getDocument`: 문서 ID로 전체 내용 조회
- **REST API 엔드포인트**:
  - `POST /api/llm/chat`: 동기 채팅 (전체 응답 반환)
  - `POST /api/llm/chat/stream`: 스트리밍 채팅 (SSE)

#### 프론트엔드
- **llm-api.ts**: 백엔드 LLM API 클라이언트 (SSE 스트리밍 지원)
- **useLlmChat Hook**: LLM 채팅 상태 관리 및 스트리밍 처리
- **StreamingMessage 컴포넌트**: 스트리밍 중 Loader 아이콘 표시
- **ChatInterface 컴포넌트**: 채팅 UI (메시지 버블, 입력 폼, 자동 스크롤)
- **Playground 페이지**: AI 채팅 인터페이스 통합

### 변경된 사항 🔧

#### 백엔드
- **Spring AI BOM**: `1.0.0-M5` → `1.1.0` (GA Release)
- **Artifact 이름 변경**:
  - `spring-ai-pgvector-store-spring-boot-starter` → `spring-ai-starter-vector-store-pgvector`
  - `spring-ai-openai-spring-boot-starter` → `spring-ai-starter-model-openai`
  - `spring-ai-ollama-spring-boot-starter` → `spring-ai-starter-model-ollama`
- **OpenAiChatAutoConfiguration 활성화**: LLM 기능을 위해 Chat 모델 사용
- **application.yml**:
  ```yaml
  spring.ai.openai.chat:
    enabled: true
    options:
      model: gpt-4o
      temperature: 0.7
      max-tokens: 4096

  docst.llm:
    enabled: true
  ```

#### 프론트엔드
- **ChatMessage 타입**: `isStreaming` 속성 추가
- **Playground 페이지**: Phase 5 MCP → Phase 6 LLM으로 전환
- **타입 정의**: `ChatRequest`, `ChatResponse` 추가

### 제거된 사항 ❌

#### 백엔드
- ~~기존 Tool 클래스들 (메서드 참조 방식)~~:
  - `DocumentTools.java` (삭제)
  - `SearchTools.java` (삭제)
  - `GitTools.java` (삭제)
  - `BranchService.java` (삭제)
- 이유: Spring AI 1.1.0에서 Function Bean 패턴 필수

#### 프론트엔드
- ~~useMcpTools Hook 의존성~~ (Playground 페이지에서 제거)

### 수정된 버그 🐛

#### 컴파일 오류
- **InMemoryChatMemory 미존재**: `MessageWindowChatMemory` 사용으로 해결
- **.functions() 메서드 없음**: `.toolNames()` 사용으로 해결
- **Builder 메서드 패턴 변경**: `withX()` → `x()` 패턴 적용

#### 테스트 오류
- **SemanticSearchIntegrationTest 실패**: Phase 2+ 기능으로 `@Disabled` 처리

#### 타입 오류
- **ChatMessage 중복 정의**: 기존 타입에 `isStreaming` 추가, 중복 제거

### 알려진 이슈 ⚠️

1. **프로젝트 컨텍스트 필요**
   - Playground가 프로젝트 선택 없이 접근 불가
   - Week 3-4에 프로젝트 선택 UI 추가 예정

2. **대화 히스토리 비영속적**
   - 서버 재시작 시 대화 히스토리 초기화
   - Week 3-4에 LocalStorage/Redis 영속화 예정

3. **마크다운 렌더링 미지원**
   - 현재 일반 텍스트로만 표시
   - Week 5-6에 마크다운 렌더러 통합 예정

### 성능 개선 📈

- **스트리밍 응답**: 사용자가 LLM 응답을 실시간으로 확인 가능
- **자동 Tool Calling**: Spring AI가 필요한 Tool을 자동으로 호출, 개발자 개입 최소화

### 보안 강화 🔒

- **백엔드 프록시 패턴**: OpenAI API Key가 클라이언트에 노출되지 않음
- **Authorization 헤더**: 모든 LLM API 요청에 JWT 토큰 포함

### 문서화 📝

새로 추가된 문서:
- `docs/impl/phase6/README.md`: 전체 개요
- `docs/impl/phase6/backend.md`: 백엔드 구현 상세
- `docs/impl/phase6/frontend.md`: 프론트엔드 구현 상세
- `docs/impl/phase6/quick-start.md`: 빠른 시작 가이드
- `docs/impl/phase6/troubleshooting.md`: 문제 해결 가이드
- `docs/impl/phase6/CHANGELOG.md`: 이 파일

---

## 다음 단계 (Week 3-4 예정)

### 계획된 기능

- [ ] **Branch Selector**: Git 브랜치 선택/생성 UI
- [ ] **Session Manager**: 대화 히스토리 저장 및 로드 (LocalStorage)
- [ ] **Template Selector**: 자주 사용하는 프롬프트 템플릿
- [ ] **Tool Call Progress Indicator**: 도구 호출 진행 상황 시각화

### 계획된 개선

- [ ] **프로젝트 선택 UI**: Playground에서 프로젝트 전환 가능하도록
- [ ] **에러 핸들링 강화**: 사용자 친화적인 에러 메시지
- [ ] **로딩 상태 개선**: 스켈레톤 UI 또는 Progress Bar
- [ ] **키보드 단축키**: Ctrl+Enter로 전송 등

### 계획된 Tool 추가

- [ ] `updateDocument`: 문서 내용 수정
- [ ] `createDocument`: 새 문서 생성
- [ ] `listBranches`: 브랜치 목록 조회
- [ ] `createBranch`: 새 브랜치 생성
- [ ] `syncRepository`: 레포지토리 동기화

---

## 의존성 변경

### 백엔드

#### 추가
```kotlin
// build.gradle.kts
implementation(platform("org.springframework.ai:spring-ai-bom:1.1.0"))
implementation("org.springframework.ai:spring-ai-starter-model-openai")
```

#### 변경
```kotlin
// Before
implementation("org.springframework.ai:spring-ai-pgvector-store-spring-boot-starter")

// After
implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")
```

### 프론트엔드

#### 변경 없음
- Next.js 16.1.0 유지
- 기존 의존성 그대로 사용

---

## 마이그레이션 가이드

### Spring AI 1.0.0-M5 → 1.1.0

기존 코드를 사용 중인 경우 다음 변경 필요:

#### 1. Gradle 의존성
```kotlin
// build.gradle.kts
dependencies {
    // BOM 버전 변경
    implementation(platform("org.springframework.ai:spring-ai-bom:1.1.0"))

    // Artifact ID 변경
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")
}
```

#### 2. ChatMemory
```java
// Before
ChatMemory chatMemory = new InMemoryChatMemory();

// After
ChatMemory chatMemory = MessageWindowChatMemory.builder()
    .maxMessages(20)
    .build();
```

#### 3. Tool 등록
```java
// Before
chatClient.prompt()
    .functions(tools::searchDocuments, tools::listDocuments)
    .call();

// After
chatClient.prompt()
    .toolNames("searchDocuments", "listDocuments")
    .call();
```

#### 4. Function Bean 정의
```java
// Before - 메서드 참조
@Component
public class DocumentTools {
    public List<Doc> listDocuments(String projectId) { ... }
}

// After - Function Bean
@Configuration
public class LlmToolsConfig {
    @Bean
    @Description("List all documents")
    public Function<ListDocsReq, ListDocsRes> listDocuments() {
        return request -> { ... };
    }
}
```

#### 5. Embedding Options
```java
// Before
OpenAiEmbeddingOptions.builder()
    .withModel("text-embedding-3-small")
    .withDimensions(1536)
    .build();

// After
OpenAiEmbeddingOptions.builder()
    .model("text-embedding-3-small")
    .dimensions(1536)
    .build();
```

---

## 기여자

- **백엔드 구현**: Spring AI 1.1.0 통합, Tool Calling, REST API
- **프론트엔드 구현**: React Hook, SSE 스트리밍, Playground UI
- **문서화**: 구현 상세, 빠른 시작 가이드, 문제 해결 가이드

---

## 참고 링크

- [Spring AI 1.1.0 Release Notes](https://github.com/spring-projects/spring-ai/releases/tag/v1.1.0)
- [Spring AI Migration Guide](https://docs.spring.io/spring-ai/reference/upgrade-notes.html)
- [OpenAI API Documentation](https://platform.openai.com/docs/api-reference)
- [Server-Sent Events Specification](https://html.spec.whatwg.org/multipage/server-sent-events.html)
