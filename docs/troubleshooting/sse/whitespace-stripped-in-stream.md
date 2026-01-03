# SSE 스트리밍에서 공백이 제거되는 문제

## 증상

AI Playground에서 LLM 응답이 스트리밍될 때 공백과 띄어쓰기가 모두 제거되어 표시됨.

**예시:**
- 예상: `프로젝트 내 문서에서 "퀴즈셋"에 대한 정보를 찾을 수 없습니다.`
- 실제: `프로젝트내문서에서"퀴즈셋"에대한정보를찾을수없습니다.`

백엔드 로그에서는 공백이 정상적으로 포함되어 있음:
```
SSE chunk: [ 주] (length=2)
SSE chunk: [ 더] (length=2)
SSE chunk: [ 도움] (length=3)
```

## 원인

### SSE 스펙의 공백 처리 규칙

SSE(Server-Sent Events) 스펙에 따르면, `data:` 필드 바로 뒤의 첫 번째 공백(U+0020)은 **선택적 구분자**로 간주되어 제거됩니다.

> If value is not the empty string, and the first character of value is a space (0x20), strip the leading space from value.
> — [HTML Living Standard, Server-sent events](https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation)

### 문제 발생 시나리오

LLM이 토큰 단위로 텍스트를 생성할 때, 일부 청크는 공백으로 시작합니다:

```
청크 1: "안녕"
청크 2: " 하세요"  ← 공백으로 시작
청크 3: "!"
```

Spring WebFlux가 SSE로 전송할 때:
```
data:안녕

data: 하세요

data:!
```

프론트엔드에서 SSE 스펙에 따라 `data:` 뒤 첫 공백을 제거:
```
청크 1: "안녕"     ✓
청크 2: "하세요"   ✗ (공백 손실)
청크 3: "!"        ✓
```

결과: `"안녕하세요!"` → `"안녕하세요!"` (공백 유지) 대신 `"안녕하세요!"` (원래 공백 손실)

실제로 한국어에서는:
- 예상: `"해 주시면 더 도움을"`
- 실제: `"해주시면더도움을"` (각 청크의 선행 공백 손실)

## 해결 방법

### 접근 방식: JSON 인코딩

공백을 포함한 모든 문자를 안전하게 전송하기 위해 JSON 형식으로 감싸서 전송.

### Backend 수정 (`LlmController.java`)

```java
return llmService.streamChat(request.message(), request.projectId(), request.sessionId())
    .map(chunk -> {
        // 공백 보존을 위해 JSON 형식으로 인코딩
        String escaped = chunk
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
        return "{\"content\":\"" + escaped + "\"}";
    });
```

**전송 형식 변경:**
```
# Before (plain text)
data: 하세요

# After (JSON)
data:{"content":" 하세요"}
```

### Frontend 수정 (`llm-api.ts`)

```typescript
// JSON 형식 파싱 (공백 보존을 위해 백엔드에서 JSON으로 전송)
try {
  const parsed = JSON.parse(data);
  if (parsed && typeof parsed.content === 'string') {
    yield parsed.content;
  }
} catch {
  // JSON 파싱 실패 시 raw data 그대로 사용 (fallback)
  yield data;
}
```

## 대안적 해결 방법

### 1. ServerSentEvent 래퍼 사용 (Spring)

```java
return llmService.streamChat(...)
    .map(chunk -> ServerSentEvent.<String>builder()
        .data(chunk)
        .build());
```

Spring의 `ServerSentEvent` 빌더를 사용하면 데이터 인코딩을 더 명시적으로 제어할 수 있음.

### 2. Base64 인코딩

바이너리 안전한 전송이 필요한 경우 Base64 인코딩 사용.

```java
// Backend
.map(chunk -> Base64.getEncoder().encodeToString(chunk.getBytes(StandardCharsets.UTF_8)))

// Frontend
const decoded = atob(data);
```

### 3. 커스텀 구분자 사용

SSE의 `data:` 대신 커스텀 이벤트 타입 사용:

```java
return Flux.just(ServerSentEvent.<String>builder()
    .event("chunk")
    .data(chunk)
    .build());
```

## 관련 파일

- `backend/src/main/java/com/docst/api/LlmController.java` - SSE 스트리밍 엔드포인트
- `frontend/lib/llm-api.ts` - SSE 파싱 로직

## 참고 자료

- [HTML Living Standard - Server-sent events](https://html.spec.whatwg.org/multipage/server-sent-events.html)
- [MDN - Using server-sent events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events)
- [Spring WebFlux SSE](https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html#webflux-codecs-streaming)
