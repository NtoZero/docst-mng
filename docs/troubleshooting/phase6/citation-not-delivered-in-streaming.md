# Citation이 스트리밍 응답에서 전달되지 않는 문제

## 문제 요약

| 항목 | 내용 |
|------|------|
| **발생일** | 2026-01-09 |
| **증상** | LLM Playground에서 벡터 검색은 성공하지만 Citation이 프론트엔드에 표시되지 않음 |
| **영향 범위** | `/api/llm/chat/stream` 스트리밍 API |
| **해결 상태** | 해결됨 |

---

## 증상 상세

### 로그 분석

```log
# Tool 실행 (boundedElastic-1 스레드)
2026-01-09T23:02:26.023+09:00  INFO [oundedElastic-1] SemanticSearchService : Vector store search completed: found 10 documents
2026-01-09T23:02:26.062+09:00  INFO [oundedElastic-1] DocumentTools : Collected 9 citations from search results

# 스트리밍 응답 (boundedElastic-2 스레드)
2026-01-09T23:02:27.914+09:00 DEBUG [oundedElastic-2] LlmController : SSE chunk: {"type":"content","content":"우리"}
...
# "Sending N citations at end of stream" 로그가 없음!
```

### 관찰된 동작

1. 벡터 스토어에서 문서 10개를 성공적으로 검색
2. `DocumentTools.searchDocuments()`에서 9개의 Citation 수집 완료 로그 출력
3. SSE 스트리밍으로 LLM 응답 텍스트는 정상 전송
4. **Citation 이벤트가 스트림 끝에 전송되지 않음**
5. 프론트엔드에서 Citation 섹션이 비어있음

---

## 원인 분석

### 근본 원인: ThreadLocal과 WebFlux 비동기 스레드 불일치

`CitationCollector`가 `ThreadLocal`을 사용하여 Citation을 저장했으나, WebFlux 리액티브 스트리밍 환경에서는 **Tool 실행과 스트림 완료 처리가 서로 다른 스레드에서 실행**됩니다.

### 문제 발생 흐름

```
Timeline:
┌─────────────────────────────────────────────────────────────────┐
│ 1. HTTP Request Thread                                          │
│    └── citationCollector.clear()                                │
│        (ThreadLocal-HTTP 초기화)                                │
├─────────────────────────────────────────────────────────────────┤
│ 2. boundedElastic-1 Thread (Tool 실행)                          │
│    └── searchDocuments() 호출                                   │
│        └── citationCollector.add(citation)                      │
│            (ThreadLocal-Elastic1에 저장) ← 여기에 저장됨!        │
├─────────────────────────────────────────────────────────────────┤
│ 3. boundedElastic-2 Thread (스트리밍)                           │
│    └── Content 청크 전송                                        │
├─────────────────────────────────────────────────────────────────┤
│ 4. ??? Thread (Mono.fromSupplier)                               │
│    └── citationCollector.getAndClear()                          │
│        (ThreadLocal-??? 읽음 → 비어있음!) ← 다른 스레드!         │
│    └── CitationsEvent(빈 리스트) 전송                           │
└─────────────────────────────────────────────────────────────────┘
```

### 기존 코드 문제점

**CitationCollector.java (수정 전)**
```java
@Component
public class CitationCollector {
    // ThreadLocal은 스레드별로 독립적인 저장소
    private static final ThreadLocal<List<Citation>> CITATIONS =
        ThreadLocal.withInitial(ArrayList::new);

    public void add(Citation citation) {
        // 현재 스레드의 ThreadLocal에만 저장
        CITATIONS.get().add(citation);
    }

    public List<Citation> getAndClear() {
        // 현재 스레드의 ThreadLocal에서만 읽음
        // 다른 스레드에서 저장된 데이터는 접근 불가!
        List<Citation> result = new ArrayList<>(CITATIONS.get());
        CITATIONS.remove();
        return result;
    }
}
```

**LlmService.java (수정 전)**
```java
public Flux<StreamEvent> streamChatWithCitations(...) {
    citationCollector.clear();  // HTTP 스레드에서 실행

    Flux<StreamEvent> contentFlux = chatClient.prompt()
        .tools(documentTools)  // Tool은 boundedElastic-N에서 실행
        .stream()
        .content();

    // Mono.fromSupplier는 또 다른 스레드에서 실행될 수 있음
    return contentFlux.concatWith(Mono.fromSupplier(() -> {
        List<Citation> citations = citationCollector.getAndClear();  // 빈 리스트!
        return new StreamEvent.CitationsEvent(citations);
    }));
}
```

---

## 해결 방법

### 변경 전략

ThreadLocal 대신 **세션 ID를 키로 사용하는 ConcurrentHashMap**으로 변경하여 스레드 독립적인 Citation 저장/조회 구현.

### 수정된 코드

#### 1. CitationCollector.java

```java
@Component
@Slf4j
public class CitationCollector {
    // ThreadLocal 대신 ConcurrentHashMap 사용
    private final Map<String, List<Citation>> sessionCitations = new ConcurrentHashMap<>();

    /**
     * 세션 ID 기반으로 Citation 저장
     */
    public void add(String sessionId, Citation citation) {
        if (sessionId != null && citation != null) {
            sessionCitations
                .computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(citation);
        }
    }

    /**
     * 세션 ID 기반으로 Citation 조회 및 삭제
     */
    public List<Citation> getAndClear(String sessionId) {
        if (sessionId == null) {
            return Collections.emptyList();
        }
        List<Citation> citations = sessionCitations.remove(sessionId);
        return citations != null ? new ArrayList<>(citations) : Collections.emptyList();
    }

    public void clear(String sessionId) {
        if (sessionId != null) {
            sessionCitations.remove(sessionId);
        }
    }
}
```

#### 2. LlmService.java

```java
public Flux<StreamEvent> streamChatWithCitations(String userMessage, UUID projectId, String sessionId) {
    // sessionId 기반으로 초기화
    citationCollector.clear(sessionId);

    // 프롬프트에 sessionId 포함하여 Tool이 사용할 수 있도록 지시
    String contextualizedMessage = String.format(
        "[Context: projectId=%s, sessionId=%s]\n\nUser Question: %s\n\n" +
        "IMPORTANT: When using tools, you MUST ALWAYS include:\n" +
        "- projectId=\"%s\"\n" +
        "- sessionId=\"%s\"",
        projectId, sessionId, userMessage, projectId, sessionId
    );

    Flux<StreamEvent> contentFlux = chatClient.prompt()
        .user(contextualizedMessage)
        .tools(documentTools)
        .stream()
        .content();

    // sessionId 기반으로 Citation 조회 (어떤 스레드에서 실행되든 동일한 결과)
    return contentFlux.concatWith(Mono.fromSupplier(() -> {
        List<Citation> citations = citationCollector.getAndClear(sessionId);
        log.info("Sending {} citations at end of stream for session {}", citations.size(), sessionId);
        return new StreamEvent.CitationsEvent(citations);
    }));
}
```

#### 3. DocumentTools.java

```java
@Tool(description = "Search documents using semantic search. " +
      "IMPORTANT: You MUST always provide both projectId and sessionId parameters.")
public List<DocumentSearchResult> searchDocuments(
    @ToolParam(description = "The search query") String query,
    @ToolParam(description = "The project ID (from context)") String projectId,
    @ToolParam(description = "The session ID for citation tracking (from context)") String sessionId,
    @ToolParam(description = "Max results (default: 10)", required = false) Integer topK
) {
    // ... 검색 로직 ...

    // sessionId 기반으로 Citation 저장
    results.forEach(r -> citationCollector.add(sessionId, Citation.withHeading(
        r.documentId().toString(),
        r.repositoryId() != null ? r.repositoryId().toString() : null,
        r.path(),
        r.headingPath(),
        r.chunkId() != null ? r.chunkId().toString() : null,
        r.score(),
        r.snippet()
    )));

    return results.stream()
        .map(r -> new DocumentSearchResult(...))
        .toList();
}
```

### 수정 후 흐름

```
Timeline:
┌─────────────────────────────────────────────────────────────────┐
│ 1. HTTP Request Thread                                          │
│    └── citationCollector.clear("session-123")                   │
│        (ConcurrentHashMap에서 "session-123" 키 제거)            │
├─────────────────────────────────────────────────────────────────┤
│ 2. boundedElastic-1 Thread (Tool 실행)                          │
│    └── searchDocuments(sessionId="session-123")                 │
│        └── citationCollector.add("session-123", citation)       │
│            (ConcurrentHashMap["session-123"]에 저장)            │
├─────────────────────────────────────────────────────────────────┤
│ 3. boundedElastic-2 Thread (스트리밍)                           │
│    └── Content 청크 전송                                        │
├─────────────────────────────────────────────────────────────────┤
│ 4. Any Thread (Mono.fromSupplier)                               │
│    └── citationCollector.getAndClear("session-123")             │
│        (ConcurrentHashMap["session-123"] 읽음 → 9개 Citation!)  │
│    └── CitationsEvent(9개 Citation) 전송 ✓                      │
└─────────────────────────────────────────────────────────────────┘
```

---

## 검증 방법

### 1. 로그 확인

수정 후 아래 두 로그가 모두 출력되어야 함:

```log
INFO DocumentTools : Collected 9 citations from search results for session abc-123
INFO LlmService    : Sending 9 citations at end of stream for session abc-123
```

### 2. 프론트엔드 확인

- Playground에서 문서 관련 질문 입력
- 응답 완료 후 Citation 섹션에 참조 문서 목록 표시 확인

### 3. SSE 이벤트 확인

브라우저 개발자 도구 Network 탭에서 SSE 응답 확인:

```
data: {"type":"content","content":"..."}
data: {"type":"content","content":"..."}
...
data: {"type":"citations","citations":[{"documentId":"...","path":"...","snippet":"..."},...]}
```

---

## 관련 파일

| 파일 | 변경 내용 |
|------|----------|
| `CitationCollector.java` | ThreadLocal → ConcurrentHashMap<sessionId, List> |
| `LlmService.java` | sessionId 전달, 프롬프트에 sessionId 포함 |
| `DocumentTools.java` | sessionId 파라미터 추가, sessionId 기반 저장 |

---

## 교훈 및 주의사항

### 1. WebFlux/Reactive 환경에서 ThreadLocal 사용 금지

리액티브 스트리밍 환경에서는 작업이 여러 스레드에 걸쳐 실행되므로 ThreadLocal은 데이터 손실을 유발합니다.

**대안:**
- Reactor Context (`contextWrite`/`contextRead`)
- ConcurrentHashMap with request/session key
- Request-scoped beans with proper propagation

### 2. Spring AI Tool에서 컨텍스트 전달

Spring AI의 `@Tool` 메서드는 기본적으로 요청 컨텍스트에 직접 접근할 수 없습니다. 필요한 컨텍스트(sessionId 등)는:
- Tool 파라미터로 명시적 전달
- 프롬프트에 포함하여 LLM이 Tool 호출 시 전달하도록 지시

### 3. 비동기 디버깅 시 스레드 이름 확인

로그에서 스레드 이름(`[boundedElastic-1]`, `[boundedElastic-2]` 등)을 확인하여 어떤 스레드에서 어떤 작업이 실행되는지 추적.

---

## 참고 자료

- [Project Reactor Context](https://projectreactor.io/docs/core/release/reference/#context)
- [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Java ThreadLocal in Async Context](https://www.baeldung.com/java-threadlocal)
