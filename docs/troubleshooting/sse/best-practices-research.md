# SSE 스트리밍 Best Practice 조사 (2025)

## 결론: JSON 인코딩이 업계 표준

모든 주요 LLM API 제공업체(OpenAI, Anthropic, Google, AWS)와 프레임워크(Vercel AI SDK, LangChain, Spring AI)가 **SSE 데이터를 JSON으로 인코딩**하여 전송합니다.

---

## 주요 LLM API 제공업체의 SSE 형식

### 1. OpenAI (ChatGPT, GPT-4)

**형식**: `data: {JSON}\n\n`

```
data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","choices":[{"delta":{"content":" Hello"}}]}

data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","choices":[{"delta":{"content":" world"}}]}

data: [DONE]
```

- 각 청크를 JSON 객체로 전송
- `choices[0].delta.content`에 실제 텍스트 포함
- `[DONE]` 마커로 스트림 종료 표시
- 마지막 청크에 usage 통계 포함

**출처**: [OpenAI Streaming API Reference](https://platform.openai.com/docs/api-reference/chat-streaming)

---

### 2. Anthropic (Claude)

**형식**: `event: {type}\ndata: {JSON}\n\n`

```
event: message_start
data: {"type":"message_start","message":{"id":"msg_xxx","model":"claude-3-opus"}}

event: content_block_delta
data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" Hello"}}

event: content_block_delta
data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" world"}}

event: message_stop
data: {"type":"message_stop"}
```

- **event 라인 추가**: OpenAI와 달리 명시적 이벤트 타입 제공
- JSON 내에도 `type` 필드로 이벤트 종류 명시
- `ping` 이벤트로 연결 유지
- Tool Use 시 partial JSON 스트리밍 지원

**출처**: [Anthropic Streaming Messages](https://docs.anthropic.com/en/docs/build-with-claude/streaming)

---

### 3. Google (Gemini)

**형식**: `data: {JSON}\n\n`

```
data: {"candidates":[{"content":{"parts":[{"text":" Hello"}]}}],"usageMetadata":{...}}

data: {"candidates":[{"content":{"parts":[{"text":" world"}]}}],"usageMetadata":{...}}
```

- `?alt=sse` 쿼리 파라미터로 SSE 모드 활성화
- `candidates[0].content.parts[0].text`에 텍스트 포함
- 각 청크에 safety ratings 포함
- 더 큰 단위의 토큰 청크 전송

**출처**: [Gemini API Streaming REST](https://github.com/google-gemini/cookbook/blob/main/quickstarts/rest/Streaming_REST.ipynb)

---

### 4. AWS Bedrock

**형식**: `data: {JSON}\n\n`

```
data: {"type":"content_block_delta","delta":{"type":"text_delta","text":" Hello"}}

data: {"type":"message_stop"}
```

- InvokeModelWithResponseStream, ConverseStream API 제공
- Anthropic 모델 사용 시 Claude 형식과 유사
- API Gateway 연동 시 SSE 스트리밍 지원

**출처**: [AWS Bedrock ConverseStream API](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ConverseStream.html)

---

## 주요 프레임워크의 SSE 구현

### 5. Vercel AI SDK

**형식**: `data: {JSON}\n\n`

```
data: {"type":"text-delta","textDelta":" Hello"}

data: {"type":"text-delta","textDelta":" world"}

data: {"type":"finish","finishReason":"stop"}

data: [DONE]
```

- AI SDK 5.0부터 SSE를 표준으로 채택
- `type` 필드로 메시지 종류 구분
- `x-vercel-ai-ui-message-stream: v1` 헤더 사용
- Tool calls, reasoning blocks 등 다양한 이벤트 타입 지원

**출처**: [Vercel AI SDK Stream Protocol](https://ai-sdk.dev/docs/ai-sdk-ui/stream-protocol)

---

### 6. LangChain

**형식**: `data: {JSON}\n\n`

```python
# Python FastAPI 예시
async def stream_response():
    async for chunk in chain.astream(input):
        yield f"data: {json.dumps({'content': chunk})}\n\n"
```

- `streamEvents` API로 SSE 형식 출력 지원
- `JsonOutputParser`로 구조화된 JSON 스트리밍
- `encoding: "text/event-stream"` 옵션 제공

**출처**: [LangChain Streaming Documentation](https://python.langchain.com/v0.1/docs/expression_language/streaming/)

---

### 7. Spring AI

**형식**: `data: {content}\n\n` (기본) 또는 `data: {JSON}\n\n` (권장)

```java
// 기본 (plain text) - 공백 문제 발생 가능
@PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> stream() {
    return chatModel.stream(prompt);
}

// 권장 (JSON 인코딩)
@PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> stream() {
    return chatModel.stream(prompt)
        .map(chunk -> "{\"content\":\"" + escapeJson(chunk) + "\"}");
}
```

- `StreamingChatModel` 인터페이스로 `Flux<String>` 반환
- 기본 설정은 plain text 전송 (공백 문제 발생 가능)
- 복잡한 데이터 전송 시 JSON 인코딩 권장

**출처**: [Spring AI Chat Model API](https://docs.spring.io/spring-ai/reference/api/chatmodel.html), [Baeldung Spring AI Streaming](https://www.baeldung.com/spring-ai-chatclient-stream-response)

---

## 업계 표준 분석

### 모든 주요 제공업체가 JSON을 사용하는 이유

| 이유 | 설명 |
|------|------|
| **공백 보존** | JSON 문자열 내 모든 공백이 명시적으로 보존됨 |
| **메타데이터 전송** | 텍스트 외에 토큰 수, 모델 정보, 에러 등 부가 정보 전송 |
| **타입 구분** | `type` 필드로 텍스트, 도구 호출, 에러 등 이벤트 구분 |
| **특수 문자 처리** | 줄바꿈(`\n`), 탭(`\t`) 등 이스케이프 처리 자동화 |
| **구조화된 데이터** | Tool Use 시 복잡한 JSON 인자 전송 가능 |
| **호환성** | 모든 언어에서 JSON 파싱 라이브러리 사용 가능 |

### SSE 스펙의 공백 처리 문제

```
# SSE 스펙 (HTML Living Standard)
If the first character of value is a space (U+0020),
strip the leading space from value.
```

Plain text로 전송 시 `data: Hello` (content=" Hello")가 `data:  Hello`로 전송되면,
첫 번째 공백이 구분자로 오인되어 제거됩니다.

**JSON 인코딩으로 해결**:
```
data: {"content":" Hello"}
```
→ 공백이 JSON 문자열 내부에 있어 SSE 구분자와 충돌 없음

---

## 권장 사항

### Docst 프로젝트에 적용

현재 구현한 JSON 인코딩 방식은 **업계 표준과 일치**합니다:

```java
// LlmController.java
return llmService.streamChat(...)
    .map(chunk -> {
        String escaped = chunk
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n");
        return "{\"content\":\"" + escaped + "\"}";
    });
```

### 추가 개선 가능 사항

1. **이벤트 타입 추가** (Anthropic 스타일)
```
event: text_delta
data: {"type":"text_delta","content":" Hello"}
```

2. **메타데이터 포함** (OpenAI 스타일)
```
data: {"content":" Hello","index":0,"finish_reason":null}
```

3. **Tool Call 지원** (Vercel AI SDK 스타일)
```
data: {"type":"tool-call","toolCallId":"xxx","toolName":"searchDocuments"}
data: {"type":"tool-result","toolCallId":"xxx","result":{...}}
```

---

## 참고 자료

### 공식 문서
- [OpenAI Streaming API Reference](https://platform.openai.com/docs/api-reference/chat-streaming)
- [Anthropic Streaming Messages](https://docs.anthropic.com/en/docs/build-with-claude/streaming)
- [Google Gemini Streaming REST](https://github.com/google-gemini/cookbook/blob/main/quickstarts/rest/Streaming_REST.ipynb)
- [AWS Bedrock ConverseStream](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ConverseStream.html)
- [Vercel AI SDK Stream Protocol](https://ai-sdk.dev/docs/ai-sdk-ui/stream-protocol)

### 프레임워크 문서
- [Spring AI Chat Model API](https://docs.spring.io/spring-ai/reference/api/chatmodel.html)
- [LangChain Streaming](https://python.langchain.com/v0.1/docs/expression_language/streaming/)
- [Baeldung: Spring AI Streaming Response](https://www.baeldung.com/spring-ai-chatclient-stream-response)

### 기술 블로그
- [Simon Willison: How streaming LLM APIs work](https://til.simonwillison.net/llms/streaming-llm-apis)
- [DEV.to: Complete Guide to Streaming LLM Responses](https://dev.to/hobbada/the-complete-guide-to-streaming-llm-responses-in-web-applications-from-sse-to-real-time-ui-3534)
- [Speakeasy: Server Sent Events in OpenAPI](https://www.speakeasy.com/openapi/content/server-sent-events)

### 표준 스펙
- [HTML Living Standard: Server-sent events](https://html.spec.whatwg.org/multipage/server-sent-events.html)
- [MDN: Using server-sent events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events)
