# Phase 6 프론트엔드 구현 상세

> Next.js 16 + React 기반 LLM 채팅 UI

---

## 1. 타입 정의

### frontend/lib/types.ts

**추가된 타입:**

```typescript
// ===== LLM Chat Types (Phase 6) =====

export interface ChatRequest {
  message: string;
  projectId: string;
  sessionId: string;
}

export interface ChatResponse {
  content: string;
}

// 기존 ChatMessage에 isStreaming 속성 추가
export interface ChatMessage {
  id: string;
  role: MessageRole;  // 'user' | 'assistant' | 'system'
  content: string;
  timestamp: Date;
  toolCalls?: ToolCall[];
  isError?: boolean;
  isStreaming?: boolean;  // Phase 6: 스트리밍 중 표시
}
```

**주요 변경:**

1. **ChatMessage.isStreaming**
   - 스트리밍 중인 메시지 표시
   - Loader 아이콘 표시 여부 제어

2. **ChatRequest/ChatResponse**
   - 백엔드 API와 일치하는 구조
   - `sessionId`로 대화 히스토리 관리

---

## 2. LLM API 클라이언트

### frontend/lib/llm-api.ts

**역할:**
- 백엔드 `/api/llm/chat` API 호출
- SSE 스트리밍 처리
- Authorization 헤더 자동 추가

**주요 코드:**

```typescript
const API_BASE = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8342';

async function getAuthToken(): Promise<string | null> {
  if (typeof window === 'undefined') return null;
  const stored = localStorage.getItem('docst-auth');
  if (!stored) return null;
  try {
    const parsed = JSON.parse(stored);
    return parsed.state?.token || null;
  } catch {
    return null;
  }
}

/**
 * LLM Chat API (동기)
 */
export async function sendChatMessage(request: ChatRequest): Promise<ChatResponse> {
  const token = await getAuthToken();

  const headers: HeadersInit = {
    'Content-Type': 'application/json',
  };

  if (token) {
    (headers as Record<string, string>)['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE}/api/llm/chat`, {
    method: 'POST',
    headers,
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || response.statusText);
  }

  return response.json();
}

/**
 * LLM Chat API (스트리밍)
 *
 * AsyncGenerator로 SSE 청크를 반환
 */
export async function* streamChatMessage(
  request: ChatRequest,
  signal?: AbortSignal
): AsyncGenerator<string> {
  const token = await getAuthToken();

  const headers: HeadersInit = {
    'Content-Type': 'application/json',
  };

  if (token) {
    (headers as Record<string, string>)['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE}/api/llm/chat/stream`, {
    method: 'POST',
    headers,
    body: JSON.stringify(request),
    signal,  // AbortController로 취소 가능
  });

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `Chat API error: ${response.statusText}`);
  }

  const reader = response.body?.getReader();
  if (!reader) throw new Error('No response body');

  const decoder = new TextDecoder();
  let buffer = '';

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        if (line.trim() === '') continue;

        // SSE format: "data: <content>"
        if (line.startsWith('data:')) {
          const data = line.slice(5).trim();
          if (data && data !== '[DONE]') {
            yield data;
          }
        }
      }
    }
  } finally {
    reader.releaseLock();
  }
}
```

**기술적 결정:**

1. **AsyncGenerator 사용**
   - `for await (const chunk of streamChatMessage(request))` 패턴
   - 스트리밍 데이터를 순차적으로 처리

2. **SSE 파싱**
   - `data:` prefix 제거
   - 빈 줄과 `[DONE]` 필터링

3. **AbortController 지원**
   - 사용자가 스트리밍 취소 가능
   - `signal` 파라미터로 전달

---

## 3. useLlmChat Hook

### frontend/hooks/use-llm-chat.ts

**역할:**
- LLM 채팅 상태 관리
- 스트리밍 메시지 수신 및 업데이트
- 세션 ID 관리

**주요 코드:**

```typescript
'use client';

import { useState, useCallback, useRef } from 'react';
import { streamChatMessage } from '@/lib/llm-api';
import type { ChatRequest, ChatMessage } from '@/lib/types';

function generateId(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  // Fallback for older browsers
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export function useLlmChat(projectId: string) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isStreaming, setIsStreaming] = useState(false);
  const sessionIdRef = useRef<string>(generateId());
  const abortControllerRef = useRef<AbortController | null>(null);

  const sendMessage = useCallback(
    async (userMessage: string) => {
      // 1. Add user message
      const userMsgId = generateId();
      setMessages((prev) => [
        ...prev,
        {
          id: userMsgId,
          role: 'user',
          content: userMessage,
          timestamp: new Date(),
        },
      ]);

      // 2. Prepare assistant message placeholder
      const assistantMsgId = generateId();
      setMessages((prev) => [
        ...prev,
        {
          id: assistantMsgId,
          role: 'assistant',
          content: '',
          timestamp: new Date(),
          isStreaming: true,
        },
      ]);

      // 3. Create abort controller
      abortControllerRef.current = new AbortController();
      setIsStreaming(true);

      try {
        // 4. Stream response
        const request: ChatRequest = {
          message: userMessage,
          projectId,
          sessionId: sessionIdRef.current,
        };

        let assistantContent = '';

        for await (const chunk of streamChatMessage(
          request,
          abortControllerRef.current.signal
        )) {
          assistantContent += chunk;

          setMessages((prev) =>
            prev.map((msg) =>
              msg.id === assistantMsgId
                ? { ...msg, content: assistantContent }
                : msg
            )
          );
        }

        // 5. Mark streaming complete
        setMessages((prev) =>
          prev.map((msg) =>
            msg.id === assistantMsgId ? { ...msg, isStreaming: false } : msg
          )
        );
      } catch (error) {
        if ((error as Error).name === 'AbortError') {
          // User cancelled
          setMessages((prev) =>
            prev.map((msg) =>
              msg.id === assistantMsgId
                ? {
                    ...msg,
                    content: msg.content + '\n\n[Cancelled]',
                    isStreaming: false,
                  }
                : msg
            )
          );
        } else {
          // Error occurred
          setMessages((prev) =>
            prev.map((msg) =>
              msg.id === assistantMsgId
                ? {
                    ...msg,
                    content: `Error: ${(error as Error).message}`,
                    isStreaming: false,
                    isError: true,
                  }
                : msg
            )
          );
        }
      } finally {
        setIsStreaming(false);
        abortControllerRef.current = null;
      }
    },
    [projectId]
  );

  const cancelStream = useCallback(() => {
    abortControllerRef.current?.abort();
  }, []);

  const clearMessages = useCallback(() => {
    setMessages([]);
    sessionIdRef.current = generateId();
  }, []);

  const resetSession = useCallback(() => {
    sessionIdRef.current = generateId();
  }, []);

  return {
    messages,
    sendMessage,
    isStreaming,
    cancelStream,
    clearMessages,
    resetSession,
    sessionId: sessionIdRef.current,
  };
}
```

**기술적 결정:**

1. **useState + useCallback**
   - `messages`: 채팅 메시지 배열
   - `isStreaming`: 스트리밍 중 여부
   - `sendMessage`: 메모이제이션된 메시지 전송 함수

2. **useRef for sessionId**
   - 렌더링과 무관하게 세션 ID 유지
   - `clearMessages()` 호출 시 새 세션 ID 생성

3. **AbortController**
   - `cancelStream()` 호출 시 스트리밍 중단
   - Fetch API의 `signal` 파라미터 활용

4. **메시지 업데이트 패턴**
   - `setMessages(prev => prev.map(...))`: 불변성 유지
   - ID로 특정 메시지만 업데이트

---

## 4. StreamingMessage 컴포넌트

### frontend/components/playground/streaming-message.tsx

**역할:**
- 스트리밍 중인 메시지 표시
- Loader 아이콘 애니메이션

**코드:**

```typescript
'use client';

import { Loader2 } from 'lucide-react';

interface StreamingMessageProps {
  content: string;
  isStreaming: boolean;
}

export function StreamingMessage({
  content,
  isStreaming,
}: StreamingMessageProps) {
  return (
    <div className="whitespace-pre-wrap">
      {content}
      {isStreaming && (
        <span className="inline-flex ml-1">
          <Loader2 className="h-3 w-3 animate-spin" />
        </span>
      )}
    </div>
  );
}
```

**기술적 결정:**

1. **whitespace-pre-wrap**
   - 줄바꿈과 공백 보존
   - 마크다운 형식 출력에 유용

2. **Loader2 애니메이션**
   - `lucide-react` 아이콘
   - Tailwind의 `animate-spin` 클래스

---

## 5. ChatInterface 컴포넌트

### frontend/components/playground/chat-interface.tsx

**역할:**
- 채팅 UI 전체 레이아웃
- 메시지 목록, 입력 폼, 헤더

**주요 코드:**

```typescript
'use client';

import { useState, useRef, useEffect } from 'react';
import { Send, Square, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { ScrollArea } from '@/components/ui/scroll-area';
import { useLlmChat } from '@/hooks/use-llm-chat';
import { StreamingMessage } from './streaming-message';
import { cn } from '@/lib/utils';
import type { ChatMessage } from '@/lib/types';

interface ChatInterfaceProps {
  projectId: string;
}

export function ChatInterface({ projectId }: ChatInterfaceProps) {
  const { messages, sendMessage, isStreaming, cancelStream, clearMessages } =
    useLlmChat(projectId);

  const [input, setInput] = useState('');
  const scrollAreaRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // Auto-scroll to bottom when new messages arrive
  useEffect(() => {
    if (scrollAreaRef.current) {
      scrollAreaRef.current.scrollTop = scrollAreaRef.current.scrollHeight;
    }
  }, [messages]);

  const handleSubmit = async (e?: React.FormEvent) => {
    e?.preventDefault();
    if (!input.trim() || isStreaming) return;

    const message = input.trim();
    setInput('');
    textareaRef.current?.focus();

    await sendMessage(message);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSubmit();
    }
  };

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b">
        <h2 className="text-lg font-semibold">AI Chat</h2>
        <Button
          variant="ghost"
          size="sm"
          onClick={clearMessages}
          disabled={messages.length === 0}
        >
          <Trash2 className="h-4 w-4 mr-2" />
          Clear
        </Button>
      </div>

      {/* Messages */}
      <ScrollArea ref={scrollAreaRef} className="flex-1 p-4">
        <div className="space-y-4">
          {messages.length === 0 && (
            <div className="text-center text-muted-foreground py-8">
              <p>Start a conversation with the AI assistant.</p>
              <p className="text-sm mt-2">
                You can search documents, read content, and ask questions using
                natural language.
              </p>
            </div>
          )}

          {messages.map((message) => (
            <MessageBubble key={message.id} message={message} />
          ))}
        </div>
      </ScrollArea>

      {/* Input */}
      <form onSubmit={handleSubmit} className="p-4 border-t">
        <div className="flex gap-2">
          <Textarea
            ref={textareaRef}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Ask about your documents..."
            className="min-h-[60px] resize-none"
            disabled={isStreaming}
          />

          {isStreaming ? (
            <Button
              type="button"
              variant="destructive"
              size="icon"
              onClick={cancelStream}
            >
              <Square className="h-4 w-4" />
            </Button>
          ) : (
            <Button type="submit" size="icon" disabled={!input.trim()}>
              <Send className="h-4 w-4" />
            </Button>
          )}
        </div>
      </form>
    </div>
  );
}

function MessageBubble({ message }: { message: ChatMessage }) {
  const isUser = message.role === 'user';

  return (
    <div className={cn('flex', isUser ? 'justify-end' : 'justify-start')}>
      <div
        className={cn(
          'max-w-[80%] rounded-lg px-4 py-2',
          isUser ? 'bg-primary text-primary-foreground' : 'bg-muted',
          message.isError && 'bg-destructive text-destructive-foreground'
        )}
      >
        {message.isStreaming ? (
          <StreamingMessage content={message.content} isStreaming={true} />
        ) : (
          <div className="whitespace-pre-wrap">{message.content}</div>
        )}

        <div className="text-xs opacity-70 mt-1">
          {message.timestamp.toLocaleTimeString()}
        </div>
      </div>
    </div>
  );
}
```

**기술적 결정:**

1. **Auto-scroll**
   - `useEffect`로 메시지 변경 감지
   - `scrollTop = scrollHeight`로 하단 스크롤

2. **Enter vs Shift+Enter**
   - Enter: 전송
   - Shift+Enter: 줄바꿈
   - `handleKeyDown`에서 처리

3. **조건부 버튼**
   - `isStreaming ? Stop : Send`
   - 스트리밍 중에는 입력 비활성화

4. **MessageBubble**
   - 사용자/어시스턴트 구분 (좌우 정렬)
   - 에러 메시지는 빨간색 배경

---

## 6. Playground 페이지

### frontend/app/[locale]/playground/page.tsx

**역할:**
- Playground 메인 페이지
- ChatInterface 통합
- Quick Tips 사이드바

**주요 코드:**

```typescript
'use client';

import { useParams } from 'next/navigation';
import { ChatInterface } from '@/components/playground/chat-interface';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';

export default function PlaygroundPage() {
  const params = useParams();
  const projectId = params.projectId as string | undefined;

  if (!projectId) {
    return (
      <div className="container mx-auto p-6 h-[calc(100vh-4rem)]">
        <div className="flex items-center justify-center h-full">
          <div className="text-center">
            <h2 className="text-2xl font-bold mb-2">No Project Selected</h2>
            <p className="text-muted-foreground">
              Please select a project from the sidebar to use the AI Playground.
            </p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="container mx-auto p-6 h-[calc(100vh-4rem)]">
      <div className="flex flex-col h-full gap-6">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold">AI Playground</h1>
            <p className="text-muted-foreground mt-1">
              Chat with AI to search, read, and analyze your documents
            </p>
          </div>
          <Badge variant="default" className="text-sm">
            Phase 6 LLM
          </Badge>
        </div>

        {/* Main Content */}
        <div className="flex-1 flex gap-6 min-h-0">
          {/* Chat Area */}
          <Card className="flex-1 flex flex-col min-h-0">
            <CardContent className="flex-1 p-0 flex flex-col min-h-0">
              <ChatInterface projectId={projectId} />
            </CardContent>
          </Card>

          {/* Sidebar */}
          <Card className="w-80 flex flex-col">
            <CardHeader className="border-b">
              <CardTitle>Quick Tips</CardTitle>
              <CardDescription>How to use the AI assistant</CardDescription>
            </CardHeader>
            <CardContent className="flex-1 p-4">
              <div className="space-y-4">
                <div className="p-4 bg-muted rounded-lg text-sm">
                  <p className="font-semibold mb-2">Example Questions:</p>
                  <ul className="space-y-2 text-muted-foreground">
                    <li>• Find all README files</li>
                    <li>• Search for authentication documentation</li>
                    <li>• List all documents in this project</li>
                    <li>• What does the API.md file say?</li>
                  </ul>
                </div>

                <div className="p-4 bg-muted rounded-lg text-sm">
                  <p className="font-semibold mb-2">Available Tools:</p>
                  <ul className="space-y-1 text-muted-foreground text-xs">
                    <li>• <strong>searchDocuments</strong>: Search by keywords</li>
                    <li>• <strong>listDocuments</strong>: List all documents</li>
                    <li>• <strong>getDocument</strong>: Read document content</li>
                  </ul>
                </div>

                <div className="p-4 bg-blue-50 dark:bg-blue-950 rounded-lg text-sm">
                  <p className="font-semibold mb-1 text-blue-900 dark:text-blue-100">
                    Powered by Spring AI
                  </p>
                  <p className="text-xs text-blue-700 dark:text-blue-300">
                    Using OpenAI GPT-4o with real-time streaming
                  </p>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
```

**기술적 결정:**

1. **useParams() for projectId**
   - URL 파라미터에서 projectId 추출
   - 프로젝트 선택 여부 검증

2. **2-Column Layout**
   - 좌측: ChatInterface (flex-1)
   - 우측: Quick Tips 사이드바 (w-80)

3. **Example Questions**
   - 사용자에게 프롬프트 예시 제공
   - 자연어 쿼리 유도

---

## 7. 빌드 및 테스트

### TypeScript 컴파일

```bash
npm run build
```

**결과:**
```
✓ Compiled successfully in 2.8s
✓ Generating static pages (23/23)

Route (app)
├ ƒ /[locale]/playground   # ← 생성됨
```

**Type Errors 해결:**

1. **ChatMessage 중복 정의**
   - 기존: `ChatMessage` (MCP용)
   - 추가: `ChatMessage` (LLM용)
   - 해결: 기존 타입에 `isStreaming` 속성 추가, 중복 제거

---

## 8. 사용자 플로우

### 1. Playground 접속

```
http://localhost:3000/ko/playground
```

### 2. 채팅 시작

**사용자 입력:**
> 프로젝트의 모든 문서를 나열해줘

**내부 처리:**
1. `useLlmChat.sendMessage()` 호출
2. `streamChatMessage()` API 호출
3. 백엔드에서 Tool Calling: `listDocuments(projectId)`
4. SSE로 응답 스트리밍

**AI 응답 (실시간):**
> 프로젝트에 총 15개의 문서가 있습니다:
> 1. README.md - Project Overview
> 2. docs/architecture.md - System Architecture
> ...

### 3. 스트리밍 취소

- Stop 버튼 클릭
- `abortController.abort()` 호출
- 메시지 끝에 `[Cancelled]` 추가

---

## 9. 스타일링

### Tailwind CSS 클래스

**메시지 버블:**
```tsx
// 사용자 메시지
className="bg-primary text-primary-foreground"

// AI 메시지
className="bg-muted"

// 에러 메시지
className="bg-destructive text-destructive-foreground"
```

**레이아웃:**
```tsx
// 전체 높이
h-[calc(100vh-4rem)]

// Flex 레이아웃
flex flex-col h-full

// Auto-scroll
overflow-hidden
```

---

## 10. 알려진 이슈 및 제약

### 1. 프로젝트 선택

**현재**: URL 파라미터에서 `projectId` 추출 시도
**문제**: Playground 라우팅이 프로젝트와 독립적
**해결 방안**: Week 3-4에 프로젝트 선택 UI 추가

### 2. 세션 영속화

**현재**: LocalStorage 미사용 (메모리만)
**문제**: 페이지 새로고침 시 대화 히스토리 손실
**계획**: Week 3-4에 Session Manager 구현

### 3. 마크다운 렌더링

**현재**: `whitespace-pre-wrap` (일반 텍스트)
**개선**: Week 5-6에 마크다운 렌더러 통합

---

## 참고 자료

- [Next.js 16 App Router](https://nextjs.org/docs/app)
- [React Hooks](https://react.dev/reference/react)
- [Server-Sent Events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events)
- [AsyncGenerator](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/AsyncGenerator)
