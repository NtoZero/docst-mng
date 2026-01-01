# Phase 6 문제 해결 가이드

> 일반적인 문제와 해결 방법

---

## 백엔드 관련 문제

### 1. 컴파일 오류

#### 문제: "cannot find symbol: class InMemoryChatMemory"

**원인**: Spring AI 1.1.0에서 클래스 이름이 변경됨

**해결:**
```java
// ❌ 잘못된 코드
import org.springframework.ai.chat.memory.InMemoryChatMemory;
new InMemoryChatMemory();

// ✅ 올바른 코드
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
MessageWindowChatMemory.builder()
    .maxMessages(20)
    .build();
```

#### 문제: "cannot find symbol: method functions(...)"

**원인**: Spring AI 1.1.0 API 변경

**해결:**
```java
// ❌ 잘못된 코드
.functions(tools::searchDocuments, tools::listDocuments)

// ✅ 올바른 코드
.toolNames("searchDocuments", "listDocuments")
```

#### 문제: "Builder method withX() not found"

**원인**: Spring AI 1.1.0에서 빌더 메서드 패턴 변경

**해결:**
```java
// ❌ 잘못된 코드
OpenAiEmbeddingOptions.builder()
    .withModel("text-embedding-3-small")
    .withDimensions(1536)
    .build();

// ✅ 올바른 코드
OpenAiEmbeddingOptions.builder()
    .model("text-embedding-3-small")
    .dimensions(1536)
    .build();
```

---

### 2. 런타임 오류

#### 문제: "Error creating bean 'chatClient'"

**원인**: OpenAI API Key 미설정 또는 Auto-configuration 충돌

**진단:**
```bash
# 로그 확인
./gradlew bootRun | grep -i "openai\|chatclient"
```

**해결 1**: API Key 설정
```bash
# .env 파일 확인
cat backend/.env

# OPENAI_API_KEY가 없으면 추가
echo "OPENAI_API_KEY=sk-proj-..." >> backend/.env

# 또는 환경 변수로 설정
export OPENAI_API_KEY=sk-proj-...
./gradlew bootRun
```

**해결 2**: Auto-configuration 확인
```yaml
# application.yml
spring:
  autoconfigure:
    exclude:
      # OpenAiChatAutoConfiguration이 제외되지 않았는지 확인
      # ❌ - org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration
```

#### 문제: "No qualifying bean of type 'Function<Request, Response>'"

**원인**: LlmToolsConfig가 Bean으로 등록되지 않음

**진단:**
```java
// LlmToolsConfig.java 확인
@Configuration  // ← 이 어노테이션이 있는지 확인
@ConditionalOnProperty(prefix = "docst.llm", name = "enabled",
                       havingValue = "true", matchIfMissing = true)
public class LlmToolsConfig {
    // ...
}
```

**해결:**
```yaml
# application.yml
docst:
  llm:
    enabled: true  # ← 이 설정이 있는지 확인
```

#### 문제: "Tool: searchDocuments - NoSuchMethodException"

**원인**: Function Bean의 Request 타입과 실제 호출 시 타입 불일치

**진단:**
```java
// @JsonProperty 어노테이션 확인
public record SearchDocumentsRequest(
    @JsonProperty(required = true)  // ← 필수
    @JsonPropertyDescription("The search query keywords")
    String query,
    // ...
) {}
```

**해결:**
- Request Record에 모든 필드에 `@JsonProperty` 추가
- `@JsonPropertyDescription`으로 설명 추가
- `required` 속성 명시

---

### 3. OpenAI API 오류

#### 문제: "401 Unauthorized"

**원인**: 잘못된 API Key

**해결:**
```bash
# API Key 확인
curl https://api.openai.com/v1/models \
  -H "Authorization: Bearer $OPENAI_API_KEY"

# 정상 응답: 모델 목록
# 오류 응답: { "error": { "message": "Incorrect API key", ... } }
```

#### 문제: "429 Too Many Requests"

**원인**: Rate Limit 초과

**해결:**
1. OpenAI 대시보드에서 Rate Limit 확인
2. 요청 빈도 줄이기
3. Tier 업그레이드 고려

#### 문제: "Insufficient quota"

**원인**: 크레딧 부족

**해결:**
1. https://platform.openai.com/usage 에서 사용량 확인
2. https://platform.openai.com/account/billing 에서 크레딧 충전

#### 문제: "Model not found: gpt-4o"

**원인**: 계정에서 해당 모델 사용 불가

**해결:**
```yaml
# application.yml - 다른 모델로 변경
spring:
  ai:
    openai:
      chat:
        options:
          model: gpt-3.5-turbo  # 또는 gpt-4
```

---

### 4. 성능 문제

#### 문제: LLM 응답이 너무 느림

**원인**: 네트워크, OpenAI 서버 상태, 복잡한 Tool Calling

**진단:**
```bash
# 로그에서 Tool Calling 시간 확인
./gradlew bootRun | grep -i "tool:\|duration"
```

**해결:**
1. **스트리밍 사용**: 동기 → 스트리밍 전환
2. **프롬프트 최적화**: 불필요한 정보 제거
3. **모델 변경**: gpt-4o → gpt-3.5-turbo (빠르지만 성능 저하)

#### 문제: 메모리 부족 (OutOfMemoryError)

**원인**: MessageWindowChatMemory의 maxMessages가 너무 큼

**해결:**
```java
// LlmConfig.java
@Bean
public ChatMemory chatMemory() {
    return MessageWindowChatMemory.builder()
        .maxMessages(10)  // ← 20 → 10으로 줄이기
        .build();
}
```

---

## 프론트엔드 관련 문제

### 1. 빌드 오류

#### 문제: "Type error: Property 'role' must be of type 'MessageRole'"

**원인**: ChatMessage 타입 중복 정의

**해결:**
```typescript
// lib/types.ts - 중복 정의 제거
export interface ChatMessage {
  id: string;
  role: MessageRole;  // ← 'user' | 'assistant' 대신 MessageRole 사용
  content: string;
  timestamp: Date;
  isStreaming?: boolean;
  isError?: boolean;
}
```

#### 문제: "Cannot find module '@/lib/llm-api'"

**원인**: 파일이 생성되지 않음 또는 경로 오류

**해결:**
```bash
# 파일 존재 확인
ls frontend/lib/llm-api.ts

# 없으면 생성
# (quick-start.md의 구현 참고)
```

---

### 2. 런타임 오류

#### 문제: "No Project Selected"

**원인**: Playground 페이지가 프로젝트 컨텍스트 없이 접속됨

**해결 1**: URL에서 projectId 파라미터 전달
```typescript
// 현재 구조
/[locale]/playground  // ← projectId 없음

// Week 3-4 구조 (예정)
/[locale]/projects/[projectId]/playground
```

**해결 2**: 임시로 프로젝트 선택 UI 사용
```typescript
// playground/page.tsx
const params = useParams();
const projectId = params.projectId as string | undefined;

if (!projectId) {
  // 프로젝트 선택 UI 표시
}
```

#### 문제: "Chat API error: 401"

**원인**: 인증 토큰 누락 또는 만료

**진단:**
```typescript
// 브라우저 개발자 도구 Console
localStorage.getItem('docst-auth')
// null이면 토큰 없음
```

**해결:**
```typescript
// 로그인 페이지로 리다이렉트
if (!token) {
  router.push('/login');
}
```

#### 문제: "AsyncGenerator is not defined"

**원인**: 구형 브라우저 (IE11 등)

**해결:**
- 최신 브라우저 사용 (Chrome, Firefox, Safari, Edge)
- 또는 Babel polyfill 추가

---

### 3. 스트리밍 문제

#### 문제: 스트리밍이 작동하지 않음 (응답이 한 번에 표시)

**원인**: SSE 파싱 오류 또는 백엔드 설정 문제

**진단:**
```typescript
// 브라우저 개발자 도구 Network 탭
// /api/llm/chat/stream 요청 확인
// Response Headers:
// Content-Type: text/event-stream  ← 이 헤더가 있는지 확인
```

**해결 1**: 백엔드 확인
```java
// LlmController.java
@PostMapping(value = "/chat/stream",
             produces = MediaType.TEXT_EVENT_STREAM_VALUE)  // ← 확인
public Flux<String> chatStream(...) {
    // ...
}
```

**해결 2**: 프론트엔드 파싱 확인
```typescript
// llm-api.ts
if (line.startsWith('data:')) {
  const data = line.slice(5).trim();  // ← "data:" 제거
  if (data && data !== '[DONE]') {
    yield data;
  }
}
```

#### 문제: "reader is locked"

**원인**: ReadableStream을 여러 번 읽으려고 시도

**해결:**
```typescript
// llm-api.ts
try {
  while (true) {
    const { done, value } = await reader.read();
    // ...
  }
} finally {
  reader.releaseLock();  // ← 반드시 호출
}
```

---

### 4. UI/UX 문제

#### 문제: 자동 스크롤이 작동하지 않음

**원인**: ScrollArea ref가 올바르게 설정되지 않음

**해결:**
```typescript
// chat-interface.tsx
const scrollAreaRef = useRef<HTMLDivElement>(null);

useEffect(() => {
  if (scrollAreaRef.current) {
    scrollAreaRef.current.scrollTop = scrollAreaRef.current.scrollHeight;
  }
}, [messages]);  // ← messages 의존성 추가

// JSX
<ScrollArea ref={scrollAreaRef} className="flex-1 p-4">
```

#### 문제: Enter 키로 전송되지 않음

**원인**: onKeyDown 이벤트 핸들러 오류

**해결:**
```typescript
const handleKeyDown = (e: React.KeyboardEvent) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();  // ← 기본 동작 방지
    handleSubmit();
  }
};

<Textarea onKeyDown={handleKeyDown} />
```

---

## 통합 문제

### 1. CORS 오류

#### 문제: "Access-Control-Allow-Origin 헤더 없음"

**원인**: 백엔드 CORS 설정 문제

**해결:**
```yaml
# application.yml
docst:
  cors:
    enabled: true
    allowed-origins: http://localhost:3000,http://localhost:3001
```

---

### 2. WebSocket/SSE 연결 문제

#### 문제: "EventSource failed"

**원인**: 프록시, 방화벽, 또는 타임아웃

**진단:**
```bash
# cURL로 SSE 테스트
curl -N -X POST http://localhost:8342/api/llm/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message":"test","projectId":"...","sessionId":"test"}'

# 정상: data: ... 형식으로 스트리밍
# 오류: 연결 끊김 또는 에러 메시지
```

**해결:**
- Nginx 사용 시: `proxy_buffering off;` 설정
- Timeout 늘리기: `proxy_read_timeout 3600;`

---

## 디버깅 팁

### 1. 백엔드 로그 레벨 조정

```yaml
# application.yml
logging:
  level:
    com.docst: DEBUG  # ← INFO → DEBUG
    org.springframework.ai: DEBUG
```

### 2. 프론트엔드 디버깅

```typescript
// use-llm-chat.ts
const sendMessage = useCallback(async (userMessage: string) => {
  console.log('Sending message:', userMessage);

  for await (const chunk of streamChatMessage(request, signal)) {
    console.log('Received chunk:', chunk);  // ← 디버깅
    assistantContent += chunk;
  }
}, [projectId]);
```

### 3. Network 요청 확인

**Chrome DevTools:**
1. F12 → Network 탭
2. `/api/llm/chat/stream` 요청 클릭
3. Response 탭에서 SSE 스트림 확인

---

## 성능 최적화

### 1. 백엔드

#### Tool 호출 최적화

```java
// 불필요한 Tool 호출 방지
String response = chatClient.prompt()
    .user(userMessage)
    // 필요한 Tool만 등록
    .toolNames("searchDocuments")  // listDocuments, getDocument 제외
    .call()
    .content();
```

#### 캐싱 추가

```java
// Spring Cache 사용
@Cacheable(value = "llm-responses", key = "#message + '-' + #projectId")
public String chat(String message, UUID projectId, String sessionId) {
    // ...
}
```

### 2. 프론트엔드

#### 메시지 가상화 (Virtual Scrolling)

```typescript
// react-window 또는 react-virtualized 사용
import { FixedSizeList } from 'react-window';

<FixedSizeList
  height={600}
  itemCount={messages.length}
  itemSize={100}
>
  {({ index, style }) => (
    <MessageBubble message={messages[index]} style={style} />
  )}
</FixedSizeList>
```

#### Debouncing

```typescript
// 입력 중 불필요한 API 호출 방지
import { useDebouncedCallback } from 'use-debounce';

const debouncedSend = useDebouncedCallback(
  (message) => sendMessage(message),
  300
);
```

---

## 추가 도움 받기

### 1. 로그 수집

```bash
# 백엔드 로그
./gradlew bootRun > backend.log 2>&1

# 프론트엔드 로그
npm run dev > frontend.log 2>&1
```

### 2. 이슈 리포트

GitHub Issues에 다음 정보 포함:
- 환경 (OS, Java/Node 버전)
- 재현 방법
- 에러 메시지
- 로그 파일

### 3. 참고 자료

- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [Next.js Documentation](https://nextjs.org/docs)
- [OpenAI API Status](https://status.openai.com/)
