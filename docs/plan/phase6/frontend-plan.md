# Phase 6 Frontend 구현 계획

> LLM 통합 UI 및 고급 기능 (백엔드 프록시 패턴)

---

## 핵심 변경사항 (2025-01 업데이트)

### 백엔드 프록시 패턴 채택

기존 계획에서 프론트엔드가 OpenAI/Anthropic API를 직접 호출하려 했으나, **백엔드 프록시 패턴**으로 변경합니다.

| 기존 계획 | 변경된 계획 |
|----------|------------|
| 프론트엔드에서 OpenAI API 직접 호출 | 백엔드 `/api/llm/chat` 통해 호출 |
| 프론트엔드에서 Anthropic API 직접 호출 | 백엔드 `/api/llm/chat` 통해 호출 |
| 사용자별 API Key 입력 (LocalStorage) | 서버 레벨 API Key 사용 |
| 프론트엔드 LLM 클라이언트 구현 | 백엔드가 Spring AI로 처리 |

### 주요 장점

1. **보안**: API Key가 클라이언트에 노출되지 않음
2. **비용 관리**: 서버에서 Rate Limiting, 사용량 추적 가능
3. **단순화**: 프론트엔드는 REST API만 호출
4. **일관성**: Provider 전환이 백엔드 설정만으로 가능

---

## 신규 파일 구조

```
frontend/
├── lib/
│   ├── llm-api.ts                   # 백엔드 LLM API 클라이언트 (신규)
│   └── github-client.ts             # GitHub API (PR 생성, 선택적)
├── hooks/
│   ├── use-llm-chat.ts              # LLM 대화 Hook (백엔드 프록시)
│   ├── use-session.ts               # 세션 관리 Hook
│   └── use-branches.ts              # Branch 관리 Hook
├── components/playground/
│   ├── chat-interface.tsx           # 채팅 인터페이스 (업그레이드)
│   ├── streaming-message.tsx        # 스트리밍 메시지 표시
│   ├── tool-call-indicator.tsx      # Tool Call 진행 표시
│   ├── branch-selector.tsx          # Branch 선택/생성
│   ├── session-manager.tsx          # 세션 관리 UI
│   └── template-selector.tsx        # 템플릿 선택
└── app/[locale]/playground/
    └── page.tsx                     # Playground 메인 (업그레이드)
```

---

## 1. LLM API 클라이언트 (백엔드 프록시)

### 1.1 llm-api.ts

```typescript
// frontend/lib/llm-api.ts
import { api } from '@/lib/api';

export interface ChatRequest {
  message: string;
  projectId: string;
  sessionId: string;
}

export interface ChatResponse {
  content: string;
}

/**
 * LLM Chat API (동기)
 */
export async function sendChatMessage(request: ChatRequest): Promise<ChatResponse> {
  return api.post<ChatResponse>('/api/llm/chat', request);
}

/**
 * LLM Chat API (스트리밍)
 * Server-Sent Events를 통해 실시간 응답 수신
 */
export async function* streamChatMessage(
  request: ChatRequest,
  signal?: AbortSignal
): AsyncGenerator<string> {
  const response = await fetch('/api/llm/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
    signal,
  });

  if (!response.ok) {
    throw new Error(`Chat API error: ${response.statusText}`);
  }

  const reader = response.body?.getReader();
  if (!reader) throw new Error('No response body');

  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split('\n');
    buffer = lines.pop() || '';

    for (const line of lines) {
      if (line.trim() === '') continue;

      // SSE data 파싱
      if (line.startsWith('data:')) {
        const data = line.slice(5).trim();
        if (data) {
          yield data;
        }
      }
    }
  }
}
```

---

## 2. use-llm-chat Hook

```typescript
// frontend/hooks/use-llm-chat.ts
'use client';

import { useState, useCallback, useRef } from 'react';
import { streamChatMessage, ChatRequest } from '@/lib/llm-api';

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
  isStreaming?: boolean;
  isError?: boolean;
}

export function useLlmChat(projectId: string) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isStreaming, setIsStreaming] = useState(false);
  const sessionIdRef = useRef<string>(crypto.randomUUID());
  const abortControllerRef = useRef<AbortController | null>(null);

  const sendMessage = useCallback(async (userMessage: string) => {
    // 1. Add user message
    const userMsgId = crypto.randomUUID();
    const newMessages: ChatMessage[] = [
      ...messages,
      {
        id: userMsgId,
        role: 'user',
        content: userMessage,
        timestamp: new Date(),
      },
    ];
    setMessages(newMessages);

    // 2. Prepare assistant message placeholder
    const assistantMsgId = crypto.randomUUID();
    setMessages([
      ...newMessages,
      {
        id: assistantMsgId,
        role: 'assistant',
        content: '',
        timestamp: new Date(),
        isStreaming: true,
      },
    ]);

    // 3. Create abort controller for cancellation
    abortControllerRef.current = new AbortController();
    setIsStreaming(true);

    try {
      // 4. Stream response from backend
      const request: ChatRequest = {
        message: userMessage,
        projectId,
        sessionId: sessionIdRef.current,
      };

      let assistantContent = '';

      for await (const chunk of streamChatMessage(request, abortControllerRef.current.signal)) {
        assistantContent += chunk;

        setMessages(prev => prev.map(msg =>
          msg.id === assistantMsgId
            ? { ...msg, content: assistantContent }
            : msg
        ));
      }

      // 5. Mark streaming complete
      setMessages(prev => prev.map(msg =>
        msg.id === assistantMsgId
          ? { ...msg, isStreaming: false }
          : msg
      ));

    } catch (error) {
      if ((error as Error).name === 'AbortError') {
        // User cancelled
        setMessages(prev => prev.map(msg =>
          msg.id === assistantMsgId
            ? { ...msg, content: assistantContent + '\n\n[Cancelled]', isStreaming: false }
            : msg
        ));
      } else {
        // Error occurred
        console.error('LLM chat error:', error);
        setMessages(prev => prev.map(msg =>
          msg.id === assistantMsgId
            ? {
                ...msg,
                content: `Error: ${(error as Error).message}`,
                isStreaming: false,
                isError: true,
              }
            : msg
        ));
      }
    } finally {
      setIsStreaming(false);
      abortControllerRef.current = null;
    }
  }, [messages, projectId]);

  const cancelStream = useCallback(() => {
    abortControllerRef.current?.abort();
  }, []);

  const clearMessages = useCallback(() => {
    setMessages([]);
    sessionIdRef.current = crypto.randomUUID();
  }, []);

  const resetSession = useCallback(() => {
    sessionIdRef.current = crypto.randomUUID();
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

---

## 3. Playground UI 컴포넌트

### 3.1 Chat Interface

```typescript
// frontend/components/playground/chat-interface.tsx
'use client';

import { useState, useRef, useEffect } from 'react';
import { Send, Square, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { ScrollArea } from '@/components/ui/scroll-area';
import { useLlmChat, ChatMessage } from '@/hooks/use-llm-chat';
import { StreamingMessage } from './streaming-message';
import { cn } from '@/lib/utils';

interface ChatInterfaceProps {
  projectId: string;
}

export function ChatInterface({ projectId }: ChatInterfaceProps) {
  const {
    messages,
    sendMessage,
    isStreaming,
    cancelStream,
    clearMessages,
  } = useLlmChat(projectId);

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
                You can search documents, read content, and make changes using natural language.
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
            <Button
              type="submit"
              size="icon"
              disabled={!input.trim()}
            >
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
    <div
      className={cn(
        'flex',
        isUser ? 'justify-end' : 'justify-start'
      )}
    >
      <div
        className={cn(
          'max-w-[80%] rounded-lg px-4 py-2',
          isUser
            ? 'bg-primary text-primary-foreground'
            : 'bg-muted',
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

### 3.2 Streaming Message

```typescript
// frontend/components/playground/streaming-message.tsx
'use client';

import { Loader2 } from 'lucide-react';

interface StreamingMessageProps {
  content: string;
  isStreaming: boolean;
}

export function StreamingMessage({ content, isStreaming }: StreamingMessageProps) {
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

---

## 4. Branch 관리 UI

### 4.1 use-branches Hook

```typescript
// frontend/hooks/use-branches.ts
'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';

interface BranchResult {
  branchName: string;
  ref: string | null;
  success: boolean;
}

export function useBranches(repositoryId: string) {
  const queryClient = useQueryClient();

  const branchesQuery = useQuery({
    queryKey: ['branches', repositoryId],
    queryFn: () => api.get<string[]>(`/api/repositories/${repositoryId}/branches`),
    enabled: !!repositoryId,
  });

  const createBranchMutation = useMutation({
    mutationFn: async ({ branchName, fromBranch }: { branchName: string; fromBranch: string }) => {
      return api.post<BranchResult>(`/api/repositories/${repositoryId}/branches`, {
        branchName,
        fromBranch,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['branches', repositoryId] });
    },
  });

  const switchBranchMutation = useMutation({
    mutationFn: async (branchName: string) => {
      return api.post<BranchResult>(`/api/repositories/${repositoryId}/branches/${branchName}/switch`);
    },
  });

  return {
    data: branchesQuery.data,
    isLoading: branchesQuery.isLoading,
    error: branchesQuery.error,
    createBranch: (branchName: string, fromBranch: string) =>
      createBranchMutation.mutateAsync({ branchName, fromBranch }),
    switchBranch: (branchName: string) =>
      switchBranchMutation.mutateAsync(branchName),
    isCreating: createBranchMutation.isPending,
    isSwitching: switchBranchMutation.isPending,
  };
}
```

### 4.2 Branch Selector

```typescript
// frontend/components/playground/branch-selector.tsx
'use client';

import { useState } from 'react';
import { GitBranch, Plus, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Input } from '@/components/ui/input';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { useBranches } from '@/hooks/use-branches';

interface BranchSelectorProps {
  repositoryId: string;
  onBranchChange?: (branch: string) => void;
}

export function BranchSelector({ repositoryId, onBranchChange }: BranchSelectorProps) {
  const { data: branches, isLoading, createBranch, switchBranch, isCreating, isSwitching } = useBranches(repositoryId);
  const [selectedBranch, setSelectedBranch] = useState('main');
  const [newBranchName, setNewBranchName] = useState('');
  const [isDialogOpen, setIsDialogOpen] = useState(false);

  const handleSwitch = async (branch: string) => {
    if (branch === '__create__') {
      setIsDialogOpen(true);
    } else {
      await switchBranch(branch);
      setSelectedBranch(branch);
      onBranchChange?.(branch);
    }
  };

  const handleCreate = async () => {
    if (!newBranchName.trim()) return;

    await createBranch(newBranchName, selectedBranch);
    setSelectedBranch(newBranchName);
    onBranchChange?.(newBranchName);
    setNewBranchName('');
    setIsDialogOpen(false);
  };

  if (isLoading) {
    return (
      <Button variant="outline" disabled className="w-[200px]">
        <Loader2 className="h-4 w-4 mr-2 animate-spin" />
        Loading...
      </Button>
    );
  }

  return (
    <>
      <Select value={selectedBranch} onValueChange={handleSwitch} disabled={isSwitching}>
        <SelectTrigger className="w-[200px]">
          <GitBranch className="h-4 w-4 mr-2" />
          <SelectValue />
          {isSwitching && <Loader2 className="h-3 w-3 ml-2 animate-spin" />}
        </SelectTrigger>
        <SelectContent>
          {branches?.map((branch) => (
            <SelectItem key={branch} value={branch}>
              {branch}
            </SelectItem>
          ))}
          <SelectItem value="__create__">
            <div className="flex items-center">
              <Plus className="h-4 w-4 mr-2" />
              Create new branch...
            </div>
          </SelectItem>
        </SelectContent>
      </Select>

      <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create New Branch</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="branch-name">Branch Name</Label>
              <Input
                id="branch-name"
                value={newBranchName}
                onChange={(e) => setNewBranchName(e.target.value)}
                placeholder="feature/my-new-feature"
              />
            </div>
            <div className="space-y-2">
              <Label>From Branch</Label>
              <Input value={selectedBranch} disabled />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsDialogOpen(false)}>
              Cancel
            </Button>
            <Button onClick={handleCreate} disabled={!newBranchName.trim() || isCreating}>
              {isCreating && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
              Create Branch
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
```

---

## 5. 세션 관리

### 5.1 use-session Hook

```typescript
// frontend/hooks/use-session.ts
'use client';

import { useState, useEffect, useCallback } from 'react';
import type { ChatMessage } from './use-llm-chat';

export interface Session {
  id: string;
  title: string;
  messages: ChatMessage[];
  projectId: string;
  createdAt: Date;
  updatedAt: Date;
}

const SESSIONS_STORAGE_KEY = 'docst-chat-sessions';
const MAX_SESSIONS = 50;

export function useSession(projectId: string) {
  const [sessions, setSessions] = useState<Session[]>([]);
  const [currentSessionId, setCurrentSessionId] = useState<string | null>(null);

  // Load sessions from localStorage on mount
  useEffect(() => {
    const stored = localStorage.getItem(SESSIONS_STORAGE_KEY);
    if (stored) {
      try {
        const parsed = JSON.parse(stored) as Session[];
        // Filter sessions for current project
        const projectSessions = parsed
          .filter(s => s.projectId === projectId)
          .map(s => ({
            ...s,
            createdAt: new Date(s.createdAt),
            updatedAt: new Date(s.updatedAt),
            messages: s.messages.map(m => ({
              ...m,
              timestamp: new Date(m.timestamp),
            })),
          }));
        setSessions(projectSessions);
      } catch (e) {
        console.error('Failed to load sessions:', e);
      }
    }
  }, [projectId]);

  // Save sessions to localStorage
  const persistSessions = useCallback((newSessions: Session[]) => {
    const stored = localStorage.getItem(SESSIONS_STORAGE_KEY);
    let allSessions: Session[] = [];

    if (stored) {
      try {
        allSessions = JSON.parse(stored);
        // Remove sessions for current project
        allSessions = allSessions.filter(s => s.projectId !== projectId);
      } catch (e) {
        console.error('Failed to parse stored sessions:', e);
      }
    }

    // Add new sessions and limit total
    allSessions = [...allSessions, ...newSessions].slice(-MAX_SESSIONS);
    localStorage.setItem(SESSIONS_STORAGE_KEY, JSON.stringify(allSessions));
  }, [projectId]);

  const saveSession = useCallback((messages: ChatMessage[]): string => {
    const sessionId = crypto.randomUUID();
    const title = messages[0]?.content.slice(0, 50) || 'New Session';

    const newSession: Session = {
      id: sessionId,
      title,
      messages,
      projectId,
      createdAt: new Date(),
      updatedAt: new Date(),
    };

    const newSessions = [...sessions, newSession];
    setSessions(newSessions);
    persistSessions(newSessions);
    setCurrentSessionId(sessionId);

    return sessionId;
  }, [sessions, projectId, persistSessions]);

  const updateSession = useCallback((sessionId: string, messages: ChatMessage[]) => {
    const newSessions = sessions.map(s =>
      s.id === sessionId
        ? { ...s, messages, updatedAt: new Date() }
        : s
    );
    setSessions(newSessions);
    persistSessions(newSessions);
  }, [sessions, persistSessions]);

  const loadSession = useCallback((sessionId: string): ChatMessage[] | null => {
    const session = sessions.find(s => s.id === sessionId);
    if (session) {
      setCurrentSessionId(sessionId);
      return session.messages;
    }
    return null;
  }, [sessions]);

  const deleteSession = useCallback((sessionId: string) => {
    const newSessions = sessions.filter(s => s.id !== sessionId);
    setSessions(newSessions);
    persistSessions(newSessions);

    if (currentSessionId === sessionId) {
      setCurrentSessionId(null);
    }
  }, [sessions, currentSessionId, persistSessions]);

  const shareSession = useCallback((sessionId: string): string => {
    const url = `${window.location.origin}/playground?session=${sessionId}`;
    navigator.clipboard.writeText(url);
    return url;
  }, []);

  return {
    sessions,
    currentSessionId,
    saveSession,
    updateSession,
    loadSession,
    deleteSession,
    shareSession,
  };
}
```

### 5.2 Session Manager UI

```typescript
// frontend/components/playground/session-manager.tsx
'use client';

import { History, Trash2, Share2, Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet';
import { ScrollArea } from '@/components/ui/scroll-area';
import { useSession, Session } from '@/hooks/use-session';
import { cn } from '@/lib/utils';

interface SessionManagerProps {
  projectId: string;
  onLoadSession: (messages: any[]) => void;
  onNewSession: () => void;
}

export function SessionManager({
  projectId,
  onLoadSession,
  onNewSession,
}: SessionManagerProps) {
  const {
    sessions,
    currentSessionId,
    loadSession,
    deleteSession,
    shareSession,
  } = useSession(projectId);

  const handleLoadSession = (sessionId: string) => {
    const messages = loadSession(sessionId);
    if (messages) {
      onLoadSession(messages);
    }
  };

  const handleShare = (e: React.MouseEvent, sessionId: string) => {
    e.stopPropagation();
    const url = shareSession(sessionId);
    alert(`Link copied to clipboard:\n${url}`);
  };

  const handleDelete = (e: React.MouseEvent, sessionId: string) => {
    e.stopPropagation();
    if (confirm('Delete this session?')) {
      deleteSession(sessionId);
    }
  };

  return (
    <Sheet>
      <SheetTrigger asChild>
        <Button variant="outline" size="sm">
          <History className="h-4 w-4 mr-2" />
          History
          {sessions.length > 0 && (
            <span className="ml-2 text-xs bg-muted px-1.5 py-0.5 rounded-full">
              {sessions.length}
            </span>
          )}
        </Button>
      </SheetTrigger>
      <SheetContent>
        <SheetHeader>
          <SheetTitle>Chat History</SheetTitle>
        </SheetHeader>

        <div className="mt-4">
          <Button onClick={onNewSession} className="w-full mb-4">
            <Plus className="h-4 w-4 mr-2" />
            New Conversation
          </Button>

          <ScrollArea className="h-[calc(100vh-200px)]">
            <div className="space-y-2">
              {sessions.length === 0 && (
                <p className="text-center text-muted-foreground py-8">
                  No saved sessions yet
                </p>
              )}

              {sessions
                .sort((a, b) => b.updatedAt.getTime() - a.updatedAt.getTime())
                .map((session) => (
                  <SessionItem
                    key={session.id}
                    session={session}
                    isActive={session.id === currentSessionId}
                    onLoad={() => handleLoadSession(session.id)}
                    onShare={(e) => handleShare(e, session.id)}
                    onDelete={(e) => handleDelete(e, session.id)}
                  />
                ))}
            </div>
          </ScrollArea>
        </div>
      </SheetContent>
    </Sheet>
  );
}

function SessionItem({
  session,
  isActive,
  onLoad,
  onShare,
  onDelete,
}: {
  session: Session;
  isActive: boolean;
  onLoad: () => void;
  onShare: (e: React.MouseEvent) => void;
  onDelete: (e: React.MouseEvent) => void;
}) {
  return (
    <div
      onClick={onLoad}
      className={cn(
        'p-3 rounded-lg border cursor-pointer hover:bg-muted/50 transition-colors',
        isActive && 'border-primary bg-muted/50'
      )}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="flex-1 min-w-0">
          <p className="font-medium truncate">{session.title}</p>
          <p className="text-xs text-muted-foreground">
            {session.messages.length} messages
          </p>
          <p className="text-xs text-muted-foreground">
            {session.updatedAt.toLocaleDateString()}
          </p>
        </div>
        <div className="flex gap-1">
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7"
            onClick={onShare}
          >
            <Share2 className="h-3 w-3" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7 text-destructive"
            onClick={onDelete}
          >
            <Trash2 className="h-3 w-3" />
          </Button>
        </div>
      </div>
    </div>
  );
}
```

---

## 6. 템플릿

### 6.1 템플릿 정의

```typescript
// frontend/lib/templates.ts
export interface Template {
  id: string;
  name: string;
  description: string;
  prompt: string;
  variables: string[];
  category: 'search' | 'create' | 'update' | 'git';
}

export const TOOL_TEMPLATES: Template[] = [
  {
    id: 'search-readme',
    name: 'Search README Files',
    description: 'Find all README files in the project',
    prompt: 'Find all README files in this project and list them with their paths.',
    variables: [],
    category: 'search',
  },
  {
    id: 'search-by-topic',
    name: 'Search by Topic',
    description: 'Search documents by a specific topic',
    prompt: 'Search for documents related to "{{topic}}" and summarize what you find.',
    variables: ['topic'],
    category: 'search',
  },
  {
    id: 'create-doc',
    name: 'Create Document',
    description: 'Create a new document with content',
    prompt: 'Create a new document at "{{path}}" with the title "{{title}}". The content should be:\n\n{{content}}',
    variables: ['path', 'title', 'content'],
    category: 'create',
  },
  {
    id: 'update-doc-section',
    name: 'Update Document Section',
    description: 'Update a specific section in a document',
    prompt: 'Find the document "{{path}}" and update the "{{section}}" section with the following content:\n\n{{content}}',
    variables: ['path', 'section', 'content'],
    category: 'update',
  },
  {
    id: 'create-branch-pr',
    name: 'Create Branch & PR',
    description: 'Create a new branch and make changes',
    prompt: 'Create a new branch named "{{branch_name}}" from main, then {{description}}.',
    variables: ['branch_name', 'description'],
    category: 'git',
  },
  {
    id: 'compare-versions',
    name: 'Compare Versions',
    description: 'Compare two versions of a document',
    prompt: 'Show me the differences between the current version and the previous version of "{{path}}".',
    variables: ['path'],
    category: 'git',
  },
];

export function applyTemplate(template: Template, variables: Record<string, string>): string {
  let prompt = template.prompt;

  for (const [key, value] of Object.entries(variables)) {
    prompt = prompt.replace(new RegExp(`\\{\\{${key}\\}\\}`, 'g'), value);
  }

  return prompt;
}
```

### 6.2 Template Selector UI

```typescript
// frontend/components/playground/template-selector.tsx
'use client';

import { useState } from 'react';
import { Wand2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { TOOL_TEMPLATES, Template, applyTemplate } from '@/lib/templates';

interface TemplateSelectorProps {
  onApply: (prompt: string) => void;
}

export function TemplateSelector({ onApply }: TemplateSelectorProps) {
  const [selectedTemplate, setSelectedTemplate] = useState<Template | null>(null);
  const [variables, setVariables] = useState<Record<string, string>>({});
  const [isOpen, setIsOpen] = useState(false);

  const categories = ['search', 'create', 'update', 'git'] as const;

  const handleSelectTemplate = (template: Template) => {
    setSelectedTemplate(template);
    setVariables({});
  };

  const handleApply = () => {
    if (!selectedTemplate) return;

    const prompt = applyTemplate(selectedTemplate, variables);
    onApply(prompt);
    setIsOpen(false);
    setSelectedTemplate(null);
    setVariables({});
  };

  const canApply = selectedTemplate &&
    selectedTemplate.variables.every(v => variables[v]?.trim());

  return (
    <Popover open={isOpen} onOpenChange={setIsOpen}>
      <PopoverTrigger asChild>
        <Button variant="outline" size="sm">
          <Wand2 className="h-4 w-4 mr-2" />
          Templates
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[400px]" align="start">
        {!selectedTemplate ? (
          <Tabs defaultValue="search">
            <TabsList className="grid w-full grid-cols-4">
              {categories.map(cat => (
                <TabsTrigger key={cat} value={cat} className="capitalize">
                  {cat}
                </TabsTrigger>
              ))}
            </TabsList>

            {categories.map(category => (
              <TabsContent key={category} value={category} className="mt-4">
                <div className="space-y-2">
                  {TOOL_TEMPLATES
                    .filter(t => t.category === category)
                    .map(template => (
                      <div
                        key={template.id}
                        onClick={() => handleSelectTemplate(template)}
                        className="p-3 border rounded-lg cursor-pointer hover:bg-muted/50"
                      >
                        <p className="font-medium">{template.name}</p>
                        <p className="text-sm text-muted-foreground">
                          {template.description}
                        </p>
                      </div>
                    ))}
                </div>
              </TabsContent>
            ))}
          </Tabs>
        ) : (
          <div className="space-y-4">
            <div>
              <h4 className="font-medium">{selectedTemplate.name}</h4>
              <p className="text-sm text-muted-foreground">
                {selectedTemplate.description}
              </p>
            </div>

            {selectedTemplate.variables.length > 0 && (
              <div className="space-y-3">
                {selectedTemplate.variables.map(variable => (
                  <div key={variable}>
                    <Label htmlFor={variable} className="capitalize">
                      {variable.replace(/_/g, ' ')}
                    </Label>
                    <Input
                      id={variable}
                      value={variables[variable] || ''}
                      onChange={(e) => setVariables(prev => ({
                        ...prev,
                        [variable]: e.target.value,
                      }))}
                      placeholder={`Enter ${variable.replace(/_/g, ' ')}`}
                    />
                  </div>
                ))}
              </div>
            )}

            <div className="flex gap-2">
              <Button
                variant="outline"
                onClick={() => setSelectedTemplate(null)}
                className="flex-1"
              >
                Back
              </Button>
              <Button
                onClick={handleApply}
                disabled={!canApply}
                className="flex-1"
              >
                Apply
              </Button>
            </div>
          </div>
        )}
      </PopoverContent>
    </Popover>
  );
}
```

---

## 7. Playground Page 통합

```typescript
// frontend/app/[locale]/playground/page.tsx
'use client';

import { useParams } from 'next/navigation';
import { useState } from 'react';
import { ChatInterface } from '@/components/playground/chat-interface';
import { BranchSelector } from '@/components/playground/branch-selector';
import { SessionManager } from '@/components/playground/session-manager';
import { TemplateSelector } from '@/components/playground/template-selector';
import { useCurrentProject } from '@/hooks/use-current-project';

export default function PlaygroundPage() {
  const params = useParams();
  const { project, repository } = useCurrentProject();
  const [chatKey, setChatKey] = useState(0);

  const handleNewSession = () => {
    setChatKey(prev => prev + 1);
  };

  const handleLoadSession = (messages: any[]) => {
    // TODO: Implement session loading into ChatInterface
    console.log('Loading session with messages:', messages);
  };

  const handleTemplateApply = (prompt: string) => {
    // TODO: Send prompt to chat
    console.log('Applying template prompt:', prompt);
  };

  if (!project) {
    return (
      <div className="flex items-center justify-center h-full">
        <p className="text-muted-foreground">Please select a project first.</p>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b bg-background">
        <div className="flex items-center gap-4">
          <h1 className="text-xl font-semibold">AI Playground</h1>
          {repository && (
            <BranchSelector repositoryId={repository.id} />
          )}
        </div>

        <div className="flex items-center gap-2">
          <TemplateSelector onApply={handleTemplateApply} />
          <SessionManager
            projectId={project.id}
            onLoadSession={handleLoadSession}
            onNewSession={handleNewSession}
          />
        </div>
      </div>

      {/* Chat */}
      <div className="flex-1 overflow-hidden">
        <ChatInterface key={chatKey} projectId={project.id} />
      </div>
    </div>
  );
}
```

---

## 구현 체크리스트

### Week 1-2: 기초 구현
- [ ] llm-api.ts (백엔드 프록시 클라이언트)
- [ ] use-llm-chat Hook
- [ ] ChatInterface 컴포넌트
- [ ] StreamingMessage 컴포넌트

### Week 3-4: 고급 UI
- [ ] use-branches Hook
- [ ] BranchSelector 컴포넌트
- [ ] use-session Hook
- [ ] SessionManager 컴포넌트

### Week 5-6: 템플릿 & 통합
- [ ] templates.ts (템플릿 정의)
- [ ] TemplateSelector 컴포넌트
- [ ] Playground 페이지 통합
- [ ] Tool Call 진행 표시 UI

### Week 7-8: 테스트 & 마무리
- [ ] E2E 테스트
- [ ] 모바일 반응형
- [ ] i18n (한국어/영어)
- [ ] 문서화

---

## API 엔드포인트 (백엔드 프록시)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/llm/chat` | 동기 채팅 |
| POST | `/api/llm/chat/stream` | 스트리밍 채팅 (SSE) |
| GET | `/api/repositories/{id}/branches` | 브랜치 목록 |
| POST | `/api/repositories/{id}/branches` | 브랜치 생성 |
| POST | `/api/repositories/{id}/branches/{name}/switch` | 브랜치 전환 |

---

## 참고 자료

- [Spring AI ChatClient API](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Server-Sent Events (SSE)](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events)
- [TanStack Query Mutations](https://tanstack.com/query/latest/docs/react/guides/mutations)
- [shadcn/ui Components](https://ui.shadcn.com/)
