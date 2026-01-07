# Playground Citation & Markdown Rendering Plan

## 개요

Playground 채팅 인터페이스에서:
1. RAG 서비스가 검색한 문서를 **Citation 카드** 형태로 응답 하단에 표시
2. 카드 클릭 시 **새 탭에서 문서 페이지로 이동**
3. **Markdown 렌더링** 지원 (현재 plain text로 표시되는 문제 해결)

---

## 현재 상태

### 문제점
1. `chat-interface.tsx:143` - 응답이 `whitespace-pre-wrap`으로 plain text 렌더링
2. `ChatResponse` - content만 반환, citation 메타데이터 없음
3. Tool 호출 결과가 클라이언트에 노출되지 않음

### 기존 인프라
- `MarkdownViewer` 컴포넌트 존재 (react-markdown + remark-gfm)
- `SearchResult` record에 documentId, path, headingPath, chunkId, score, snippet 포함
- `SearchResultCard` 컴포넌트 존재 (재활용 가능)

---

## 구현 계획

### Phase 1: Backend - Citation 수집 및 반환

#### 1.1 CitationCollector 생성
**새 파일**: `backend/src/main/java/com/docst/llm/CitationCollector.java`

```java
public class CitationCollector {
    private static final ThreadLocal<List<Citation>> CITATIONS =
        ThreadLocal.withInitial(ArrayList::new);

    public static void add(Citation citation) { ... }
    public static List<Citation> getAndClear() { ... }
    public static void clear() { ... }

    public record Citation(
        String documentId,
        String path,
        String title,
        String headingPath,
        String chunkId,
        double score,
        String snippet
    ) {}
}
```

#### 1.2 DocumentTools 수정
**파일**: `backend/src/main/java/com/docst/llm/tools/DocumentTools.java`

`searchDocuments` 메서드에서 검색 결과를 CitationCollector에 추가:
```java
results.forEach(r -> CitationCollector.add(new Citation(
    r.documentId().toString(),
    r.path(),
    null,
    r.headingPath(),
    r.chunkId() != null ? r.chunkId().toString() : null,
    r.score(),
    r.snippet()
)));
```

#### 1.3 LlmController 응답 확장
**파일**: `backend/src/main/java/com/docst/api/LlmController.java`

스트리밍 응답 형식 변경:
```
// 기존: data: {"content":"text"}
// 변경:
data: {"type":"content","content":"text"}
data: {"type":"citations","citations":[{...}]}
```

---

### Phase 2: Frontend - 타입 및 API 업데이트

#### 2.1 타입 정의 추가
**파일**: `frontend/lib/types.ts`

```typescript
export interface Citation {
  documentId: string;
  path: string;
  title: string | null;
  headingPath: string | null;
  chunkId: string | null;
  score: number;
  snippet: string;
}

// ChatMessage 확장
export interface ChatMessage {
  // ... 기존 필드
  citations?: Citation[];
}

// SSE 이벤트 타입
export type SSEEvent =
  | { type: 'content'; content: string }
  | { type: 'citations'; citations: Citation[] };
```

#### 2.2 llm-api.ts 수정
**파일**: `frontend/lib/llm-api.ts`

`streamChatMessage`에서 SSE 이벤트 타입 분기 처리:
```typescript
if (parsed.type === 'content') {
  yield { type: 'content', content: parsed.content };
} else if (parsed.type === 'citations') {
  yield { type: 'citations', citations: parsed.citations };
}
```

#### 2.3 use-llm-chat.ts 수정
**파일**: `frontend/hooks/use-llm-chat.ts`

Citation 이벤트 처리하여 메시지에 저장:
```typescript
for await (const event of streamChatMessage(request, signal)) {
  if (event.type === 'content') {
    // 기존 content 누적 로직
  } else if (event.type === 'citations') {
    // citations를 메시지에 추가
    setMessages(prev => prev.map(msg =>
      msg.id === assistantMsgId ? { ...msg, citations: event.citations } : msg
    ));
  }
}
```

---

### Phase 3: Frontend - UI 컴포넌트

#### 3.1 CitationCard 컴포넌트
**새 파일**: `frontend/components/playground/citation-card.tsx`

- 문서 경로, heading path, score, snippet 표시
- 클릭 시 `window.open(\`/${locale}/documents/${documentId}\`, '_blank')`
- hover 시 ExternalLink 아이콘 표시

#### 3.2 CitationsSection 컴포넌트
**새 파일**: `frontend/components/playground/citations-section.tsx`

- Citation 목록을 카드 그리드로 렌더링
- documentId 기준 중복 제거 (높은 score 유지)
- score 내림차순 정렬
- "Sources (N)" 헤더 표시

#### 3.3 MessageBubble 수정
**파일**: `frontend/components/playground/chat-interface.tsx`

```tsx
function MessageBubble({ message }) {
  const isUser = message.role === 'user';

  return (
    <div className={cn('flex', isUser ? 'justify-end' : 'justify-start')}>
      <div className={cn('max-w-[80%] rounded-lg px-4 py-2', ...)}>
        {isUser ? (
          // User: plain text
          <div className="whitespace-pre-wrap">{message.content}</div>
        ) : message.isStreaming ? (
          // Streaming: markdown + spinner
          <StreamingMessage content={message.content} isStreaming={true} />
        ) : (
          // Complete: markdown + citations
          <div>
            <MarkdownViewer content={message.content} className="prose-sm" />
            {message.citations?.length > 0 && (
              <CitationsSection citations={message.citations} />
            )}
          </div>
        )}
      </div>
    </div>
  );
}
```

#### 3.4 StreamingMessage 수정
**파일**: `frontend/components/playground/streaming-message.tsx`

`MarkdownViewer` 통합:
```tsx
export function StreamingMessage({ content, isStreaming }) {
  return (
    <div>
      <MarkdownViewer content={content} className="prose-sm" />
      {isStreaming && <Loader2 className="h-3 w-3 animate-spin ml-1" />}
    </div>
  );
}
```

---

## 수정 파일 목록

### Backend (4 files)
| 파일 | 작업 |
|-----|------|
| `llm/CitationCollector.java` | **신규** - ThreadLocal citation 수집기 |
| `llm/tools/DocumentTools.java` | 수정 - CitationCollector.add() 호출 추가 |
| `llm/LlmService.java` | 수정 - chat 메서드에서 citation 수집/반환 |
| `api/LlmController.java` | 수정 - 스트리밍 응답에 citation 이벤트 추가 |

### Frontend (6 files)
| 파일 | 작업 |
|-----|------|
| `lib/types.ts` | 수정 - Citation 타입, ChatMessage 확장 |
| `lib/llm-api.ts` | 수정 - SSE 이벤트 타입 분기 처리 |
| `hooks/use-llm-chat.ts` | 수정 - citation 이벤트 처리 |
| `components/playground/citation-card.tsx` | **신규** - citation 카드 컴포넌트 |
| `components/playground/citations-section.tsx` | **신규** - citation 섹션 컴포넌트 |
| `components/playground/chat-interface.tsx` | 수정 - MarkdownViewer 통합, CitationsSection 추가 |
| `components/playground/streaming-message.tsx` | 수정 - MarkdownViewer 사용 |

---

## 구현 순서

1. **Backend Phase**
   - CitationCollector 생성
   - DocumentTools 수정
   - LlmController 스트리밍 응답 형식 변경

2. **Frontend Types Phase**
   - types.ts에 Citation, SSEEvent 타입 추가

3. **Frontend API Phase**
   - llm-api.ts SSE 파싱 로직 수정
   - use-llm-chat.ts citation 처리 추가

4. **Frontend UI Phase**
   - citation-card.tsx 생성
   - citations-section.tsx 생성
   - chat-interface.tsx MessageBubble 수정
   - streaming-message.tsx MarkdownViewer 통합

5. **Testing**
   - 마크다운 렌더링 확인 (헤딩, 코드블록, 테이블, 리스트)
   - Citation 카드 표시 확인
   - 문서 링크 새 탭 열기 확인
   - 스트리밍 중 UI 동작 확인

---

## 예상 결과

### Before (현재)
```
[AI Response - Plain Text]
Master 티어는 프로젝트의 유료 구독 체계 중 하나로...
### Master 티어 요금
- **월 요금**: ₩29,800
...
```

### After (구현 후)
```
[AI Response - Rendered Markdown]
Master 티어는 프로젝트의 유료 구독 체계 중 하나로...

### Master 티어 요금
| 항목 | 금액 |
|------|------|
| 월 요금 | ₩29,800 |
| 연 요금 | ₩286,000 |

────────────────────────────
📚 Sources (2)

┌─────────────────────────────────┐
│ [1] 📄 pricing-guide.md        │
│     docs/guides/pricing.md      │
│     📍 # Pricing > ## Master    │
│     "Master 티어는 본격 학습자를..."│
│     [87%]              🔗       │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│ [2] 📄 subscription-faq.md     │
│     docs/faq/subscription.md    │
│     📍 # FAQ > ## 요금제        │
│     "Master 티어의 특징은..."    │
│     [72%]              🔗       │
└─────────────────────────────────┘
```