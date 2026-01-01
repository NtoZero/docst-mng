# Phase 5: 프론트엔드 구현 계획

> **작성일**: 2026-01-01
> **기반**: Phase 4 완료 (RAG 설정 UI)
> **목표**: LLM Playground UI 구현

---

## 개요

LLM 에이전트와 대화하면서 문서를 검색, 읽기, 생성, 수정할 수 있는 Playground 인터페이스를 구현합니다. MCP Server와 직접 연결하여 Claude Desktop과 유사한 경험을 제공합니다.

---

## 설계 결정사항

| 항목 | 결정 | 이유 |
|------|------|------|
| **MCP Transport** | **HTTP Streamable + SSE** | 표준 MCP 프로토콜, WebSocket 불필요 |
| LLM 통합 방식 | **MCP Server 직접 연결** | 표준 프로토콜, Claude Desktop 스타일 |
| 채팅 UI | **좌측 채팅 + 우측 문서 미리보기** | 컨텍스트 유지하며 작업 가능 |
| Tool Call 시각화 | **확장 가능한 인스펙터** | 디버깅 및 투명성 |
| 프로젝트 선택 | **Playground 내 드롭다운** | MCP Tools는 프로젝트 스코프 |

---

## MCP Transport 구현

### 지원 Transport

| Transport | 설명 | 프론트엔드 지원 |
|-----------|------|---------------|
| **HTTP Streamable** | POST로 요청, JSON 응답 | ✅ 기본 |
| **SSE** | GET으로 스트림 연결 | ✅ 스트리밍 응답 |
| STDIO | 표준 입출력 | ❌ (CLI 전용) |

### JSON-RPC 2.0 메시지

```typescript
// Request
interface JsonRpcRequest {
  jsonrpc: '2.0';
  id: string;
  method: string;
  params?: Record<string, unknown>;
}

// Response
interface JsonRpcResponse {
  jsonrpc: '2.0';
  id: string;
  result?: unknown;
  error?: { code: number; message: string };
}
```

---

## 신규 파일 구조

```
frontend/
├── app/[locale]/playground/
│   └── page.tsx                      # Playground 메인 페이지
├── components/playground/
│   ├── chat-interface.tsx            # 채팅 UI 컨테이너
│   ├── message-list.tsx              # 메시지 목록
│   ├── message-item.tsx              # 메시지 아이템 (유저/AI)
│   ├── message-input.tsx             # 메시지 입력 필드 ★
│   ├── tool-call-inspector.tsx       # MCP Tool 호출 검사기
│   ├── document-preview.tsx          # 문서 미리보기 (사이드바)
│   ├── playground-settings.tsx       # 설정 (MCP 서버, 프로젝트 등)
│   └── mcp-connection-status.tsx     # MCP 연결 상태 표시 ★
├── hooks/
│   ├── use-mcp-connection.ts         # MCP Server 연결 hook ★
│   └── use-mcp-chat.ts               # MCP를 통한 LLM 채팅 (수정)
└── lib/
    ├── mcp-protocol.ts               # MCP 프로토콜 (HTTP Streamable + SSE) ★
    ├── mcp-client.ts                 # MCP Tools 클라이언트
    └── types.ts                      # 타입 정의 (수정)

★ = 이번 업데이트에서 추가/수정된 파일
```

### Transport 관련 변경사항

- **WebSocket 제거**: `ws://` 연결 코드 삭제
- **HTTP Streamable**: `fetch()` + JSON-RPC 요청
- **SSE**: `EventSource` + 스트리밍 응답

---

## 1. Playground 메인 페이지

**파일**: `frontend/app/[locale]/playground/page.tsx`

### UI 레이아웃

```tsx
'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { ChatInterface } from '@/components/playground/chat-interface';
import { DocumentPreview } from '@/components/playground/document-preview';
import { PlaygroundSettings } from '@/components/playground/playground-settings';
import { McpConnectionStatus } from '@/components/playground/mcp-connection-status';
import { useMcpChat } from '@/hooks/use-mcp-chat';

export default function PlaygroundPage() {
  const t = useTranslations('playground');
  const [selectedProjectId, setSelectedProjectId] = useState<string>();

  const { messages, sendMessage, isLoading, connectionStatus } = useMcpChat(selectedProjectId);

  return (
    <div className="flex h-screen flex-col">
      {/* 헤더 */}
      <div className="border-b p-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold">{t('title')}</h1>
            <p className="text-sm text-muted-foreground">{t('description')}</p>
          </div>
          <McpConnectionStatus status={connectionStatus} />
        </div>
      </div>

      {/* 메인 콘텐츠 */}
      <div className="flex flex-1 overflow-hidden">
        {/* 좌측: 설정 + 채팅 */}
        <div className="flex flex-1 flex-col">
          <PlaygroundSettings
            projectId={selectedProjectId}
            onProjectChange={setSelectedProjectId}
          />
          <ChatInterface
            messages={messages}
            onSendMessage={sendMessage}
            isLoading={isLoading}
          />
        </div>

        {/* 우측: 문서 미리보기 */}
        <div className="w-96 border-l">
          <DocumentPreview projectId={selectedProjectId} />
        </div>
      </div>
    </div>
  );
}
```

---

## 2. 채팅 인터페이스

**파일**: `frontend/components/playground/chat-interface.tsx`

```tsx
'use client';

import { MessageList } from './message-list';
import { MessageInput } from './message-input';
import type { ChatMessage } from '@/lib/types';

interface ChatInterfaceProps {
  messages: ChatMessage[];
  onSendMessage: (content: string) => void;
  isLoading: boolean;
}

export function ChatInterface({ messages, onSendMessage, isLoading }: ChatInterfaceProps) {
  return (
    <div className="flex flex-1 flex-col overflow-hidden">
      <MessageList messages={messages} />
      <MessageInput
        onSend={onSendMessage}
        disabled={isLoading}
      />
    </div>
  );
}
```

---

## 3. 메시지 목록

**파일**: `frontend/components/playground/message-list.tsx`

```tsx
'use client';

import { useEffect, useRef } from 'react';
import { MessageItem } from './message-item';
import type { ChatMessage } from '@/lib/types';

interface MessageListProps {
  messages: ChatMessage[];
}

export function MessageList({ messages }: MessageListProps) {
  const bottomRef = useRef<HTMLDivElement>(null);

  // 새 메시지 추가 시 스크롤
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  if (messages.length === 0) {
    return (
      <div className="flex flex-1 items-center justify-center text-muted-foreground">
        <div className="text-center">
          <p className="text-lg font-medium">Start a conversation</p>
          <p className="mt-2 text-sm">
            Ask me to search, read, or edit documents in your project.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 overflow-y-auto p-4 space-y-4">
      {messages.map((message, index) => (
        <MessageItem key={index} message={message} />
      ))}
      <div ref={bottomRef} />
    </div>
  );
}
```

---

## 4. 메시지 아이템

**파일**: `frontend/components/playground/message-item.tsx`

```tsx
'use client';

import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import { Card, CardContent } from '@/components/ui/card';
import { User, Bot } from 'lucide-react';
import { ToolCallInspector } from './tool-call-inspector';
import type { ChatMessage } from '@/lib/types';
import ReactMarkdown from 'react-markdown';

interface MessageItemProps {
  message: ChatMessage;
}

export function MessageItem({ message }: MessageItemProps) {
  const isUser = message.role === 'user';

  return (
    <div className={`flex gap-3 ${isUser ? 'justify-end' : 'justify-start'}`}>
      {!isUser && (
        <Avatar>
          <AvatarFallback>
            <Bot className="h-4 w-4" />
          </AvatarFallback>
        </Avatar>
      )}

      <Card className={`max-w-[80%] ${isUser ? 'bg-primary text-primary-foreground' : ''}`}>
        <CardContent className="p-4">
          {/* 메시지 내용 */}
          <div className="prose dark:prose-invert">
            <ReactMarkdown>{message.content}</ReactMarkdown>
          </div>

          {/* Tool Calls */}
          {message.toolCalls && message.toolCalls.length > 0 && (
            <div className="mt-4 space-y-2">
              {message.toolCalls.map((call, index) => (
                <ToolCallInspector key={index} toolCall={call} />
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {isUser && (
        <Avatar>
          <AvatarFallback>
            <User className="h-4 w-4" />
          </AvatarFallback>
        </Avatar>
      )}
    </div>
  );
}
```

---

## 5. Tool Call 검사기

**파일**: `frontend/components/playground/tool-call-inspector.tsx`

```tsx
'use client';

import { useState } from 'react';
import { ChevronDown, ChevronRight, Wrench, Check, X } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import type { ToolCall } from '@/lib/types';

interface ToolCallInspectorProps {
  toolCall: ToolCall;
}

export function ToolCallInspector({ toolCall }: ToolCallInspectorProps) {
  const [isExpanded, setIsExpanded] = useState(false);

  const statusIcon = {
    success: <Check className="h-3 w-3 text-green-500" />,
    error: <X className="h-3 w-3 text-red-500" />,
    pending: <Wrench className="h-3 w-3 text-yellow-500 animate-spin" />,
  }[toolCall.status];

  return (
    <div className="rounded-lg border bg-muted/50 p-3">
      {/* 헤더 */}
      <button
        onClick={() => setIsExpanded(!isExpanded)}
        className="flex w-full items-center justify-between text-sm"
      >
        <div className="flex items-center gap-2">
          {statusIcon}
          <Wrench className="h-4 w-4" />
          <span className="font-medium">{toolCall.toolName}</span>
          <Badge variant="outline" className="text-xs">
            {toolCall.status}
          </Badge>
        </div>
        {isExpanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
      </button>

      {/* 상세 정보 (확장 시) */}
      {isExpanded && (
        <div className="mt-3 space-y-2 text-xs">
          {/* Input */}
          <div>
            <div className="font-medium text-muted-foreground">Input:</div>
            <pre className="mt-1 rounded bg-muted p-2 overflow-x-auto">
              {JSON.stringify(toolCall.input, null, 2)}
            </pre>
          </div>

          {/* Output */}
          {toolCall.output && (
            <div>
              <div className="font-medium text-muted-foreground">Output:</div>
              <pre className="mt-1 rounded bg-muted p-2 overflow-x-auto">
                {JSON.stringify(toolCall.output, null, 2)}
              </pre>
            </div>
          )}

          {/* Error */}
          {toolCall.error && (
            <div className="text-red-500">
              <div className="font-medium">Error:</div>
              <p className="mt-1">{toolCall.error}</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
```

---

## 5.5 메시지 입력

**파일**: `frontend/components/playground/message-input.tsx`

```tsx
'use client';

import { useState, useRef, KeyboardEvent } from 'react';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Send, Loader2 } from 'lucide-react';

interface MessageInputProps {
  onSend: (content: string) => void;
  disabled?: boolean;
  placeholder?: string;
}

export function MessageInput({ onSend, disabled, placeholder }: MessageInputProps) {
  const [value, setValue] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const handleSend = () => {
    const trimmed = value.trim();
    if (!trimmed || disabled) return;

    onSend(trimmed);
    setValue('');
    textareaRef.current?.focus();
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    // Shift+Enter: 줄바꿈, Enter: 전송
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="border-t p-4">
      <div className="flex gap-2">
        <Textarea
          ref={textareaRef}
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={placeholder || 'Type your message... (Enter to send, Shift+Enter for new line)'}
          disabled={disabled}
          className="min-h-[60px] resize-none"
          rows={2}
        />
        <Button
          onClick={handleSend}
          disabled={disabled || !value.trim()}
          size="icon"
          className="h-auto"
        >
          {disabled ? (
            <Loader2 className="h-4 w-4 animate-spin" />
          ) : (
            <Send className="h-4 w-4" />
          )}
        </Button>
      </div>
    </div>
  );
}
```

---

## 5.6 MCP 연결 상태 표시

**파일**: `frontend/components/playground/mcp-connection-status.tsx`

```tsx
'use client';

import { Badge } from '@/components/ui/badge';
import { Wifi, WifiOff, AlertCircle } from 'lucide-react';
import type { McpConnectionStatus } from '@/lib/types';

interface McpConnectionStatusProps {
  status: McpConnectionStatus;
}

export function McpConnectionStatus({ status }: McpConnectionStatusProps) {
  const config = {
    connected: {
      icon: Wifi,
      label: 'Connected',
      variant: 'default' as const,
      className: 'bg-green-500/10 text-green-500 border-green-500/20'
    },
    disconnected: {
      icon: WifiOff,
      label: 'Disconnected',
      variant: 'secondary' as const,
      className: 'bg-gray-500/10 text-gray-500 border-gray-500/20'
    },
    connecting: {
      icon: Wifi,
      label: 'Connecting...',
      variant: 'outline' as const,
      className: 'bg-yellow-500/10 text-yellow-500 border-yellow-500/20 animate-pulse'
    },
    error: {
      icon: AlertCircle,
      label: 'Error',
      variant: 'destructive' as const,
      className: 'bg-red-500/10 text-red-500 border-red-500/20'
    }
  }[status];

  const Icon = config.icon;

  return (
    <Badge variant={config.variant} className={config.className}>
      <Icon className="mr-1 h-3 w-3" />
      {config.label}
    </Badge>
  );
}
```

---

## 6. MCP 연결 Hook

**파일**: `frontend/hooks/use-mcp-connection.ts`

```typescript
import { useState, useEffect, useCallback } from 'react';
import { mcpProtocol } from '@/lib/mcp-protocol';
import type { McpConnectionStatus } from '@/lib/types';

interface UseMcpConnectionOptions {
  serverUrl?: string;
  projectId?: string;
  autoConnect?: boolean;
}

export function useMcpConnection(options: UseMcpConnectionOptions) {
  const [status, setStatus] = useState<McpConnectionStatus>('disconnected');
  const [error, setError] = useState<Error | null>(null);

  const connect = useCallback(async () => {
    if (!options.serverUrl || !options.projectId) {
      return;
    }

    setStatus('connecting');
    setError(null);

    try {
      await mcpProtocol.connect({
        serverUrl: options.serverUrl,
        projectId: options.projectId
      });
      setStatus('connected');
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Connection failed'));
      setStatus('error');
    }
  }, [options.serverUrl, options.projectId]);

  const disconnect = useCallback(() => {
    mcpProtocol.disconnect();
    setStatus('disconnected');
  }, []);

  // 자동 연결
  useEffect(() => {
    if (options.autoConnect && options.serverUrl && options.projectId) {
      connect();
    }

    return () => {
      disconnect();
    };
  }, [options.autoConnect, options.serverUrl, options.projectId, connect, disconnect]);

  return {
    status,
    error,
    connect,
    disconnect,
    isConnected: status === 'connected'
  };
}
```

---

## 6.1 MCP 채팅 Hook (중복 메시지 버그 수정)

**파일**: `frontend/hooks/use-mcp-chat.ts`

```typescript
import { useState, useCallback, useRef } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useMcpConnection } from './use-mcp-connection';
import { mcpProtocol } from '@/lib/mcp-protocol';
import type { ChatMessage } from '@/lib/types';

export function useMcpChat(projectId?: string) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  // useRef로 현재 메시지 상태 추적 (클로저 문제 방지)
  const messagesRef = useRef<ChatMessage[]>([]);

  const { status: connectionStatus } = useMcpConnection({
    serverUrl: process.env.NEXT_PUBLIC_MCP_SERVER_URL || 'http://localhost:8342/mcp',
    projectId,
    autoConnect: !!projectId
  });

  // 메시지 전송
  const sendMutation = useMutation({
    mutationFn: async (content: string) => {
      // 사용자 메시지를 먼저 추가 (UI 즉시 반영)
      const userMessage: ChatMessage = {
        id: crypto.randomUUID(),
        role: 'user',
        content,
        timestamp: new Date()
      };

      // 상태 업데이트 (중복 방지: ID 기반)
      setMessages(prev => {
        const updated = [...prev, userMessage];
        messagesRef.current = updated;
        return updated;
      });

      // MCP를 통해 LLM에 전송 (ref 사용으로 최신 상태 보장)
      const response = await mcpProtocol.sendMessageWithStreaming(
        messagesRef.current,
        content,
        (chunk) => {
          // 스트리밍 청크 처리 (옵션)
          console.log('Streaming chunk:', chunk);
        }
      );

      return response;
    },
    onSuccess: (response) => {
      // AI 응답 추가 (ID 기반 중복 방지)
      const assistantMessage: ChatMessage = {
        id: crypto.randomUUID(),
        role: 'assistant',
        content: response.content,
        toolCalls: response.toolCalls,
        timestamp: new Date()
      };

      setMessages(prev => {
        const updated = [...prev, assistantMessage];
        messagesRef.current = updated;
        return updated;
      });
    },
    onError: (error) => {
      console.error('Failed to send message:', error);

      // 에러 메시지 추가
      const errorMessage: ChatMessage = {
        id: crypto.randomUUID(),
        role: 'assistant',
        content: `Error: ${error instanceof Error ? error.message : 'Unknown error'}`,
        isError: true,
        timestamp: new Date()
      };

      setMessages(prev => {
        const updated = [...prev, errorMessage];
        messagesRef.current = updated;
        return updated;
      });
    }
  });

  const sendMessage = useCallback((content: string) => {
    if (!projectId) {
      console.warn('No project selected');
      return;
    }
    if (sendMutation.isPending) {
      console.warn('Already sending a message');
      return;
    }
    sendMutation.mutate(content);
  }, [projectId, sendMutation]);

  const clearMessages = useCallback(() => {
    setMessages([]);
    messagesRef.current = [];
  }, []);

  return {
    messages,
    sendMessage,
    clearMessages,
    isLoading: sendMutation.isPending,
    connectionStatus
  };
}
```

---

## 7. MCP 프로토콜 구현 (HTTP Streamable + SSE)

**파일**: `frontend/lib/mcp-protocol.ts`

```typescript
import type { ChatMessage, ToolCall, JsonRpcRequest, JsonRpcResponse } from './types';

/**
 * MCP Protocol 클라이언트.
 * HTTP Streamable + SSE 지원 (WebSocket 미사용).
 */
class McpProtocol {
  private serverUrl: string | null = null;
  private projectId: string | null = null;
  private sseConnection: EventSource | null = null;
  private requestId = 0;

  /**
   * MCP Server에 연결한다.
   */
  async connect(config: { serverUrl: string; projectId: string }): Promise<void> {
    this.serverUrl = config.serverUrl;
    this.projectId = config.projectId;

    // 초기화 요청 (JSON-RPC)
    const initResponse = await this.sendJsonRpc('initialize', {
      protocolVersion: '2024-11-05',
      capabilities: {},
      clientInfo: { name: 'docst-playground', version: '1.0.0' }
    });

    console.log('MCP initialized:', initResponse);

    // SSE 스트림 연결 (서버 → 클라이언트 푸시용)
    this.sseConnection = new EventSource(`${this.serverUrl}`);
    this.sseConnection.onopen = () => console.log('SSE connected');
    this.sseConnection.onerror = (e) => console.error('SSE error:', e);
  }

  /**
   * 연결 해제한다.
   */
  disconnect(): void {
    this.sseConnection?.close();
    this.sseConnection = null;
    this.serverUrl = null;
    this.projectId = null;
  }

  /**
   * JSON-RPC 요청 전송.
   */
  private async sendJsonRpc(method: string, params?: Record<string, unknown>): Promise<unknown> {
    if (!this.serverUrl) {
      throw new Error('Not connected to MCP server');
    }

    const request: JsonRpcRequest = {
      jsonrpc: '2.0',
      id: String(++this.requestId),
      method,
      params
    };

    const response = await fetch(this.serverUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request)
    });

    if (!response.ok) {
      throw new Error(`HTTP error: ${response.status}`);
    }

    const jsonRpcResponse: JsonRpcResponse = await response.json();

    if (jsonRpcResponse.error) {
      throw new Error(`JSON-RPC error: ${jsonRpcResponse.error.message}`);
    }

    return jsonRpcResponse.result;
  }

  /**
   * Tool 호출.
   */
  async callTool(name: string, args: Record<string, unknown>): Promise<ToolCall> {
    const result = await this.sendJsonRpc('tools/call', { name, arguments: args });
    return {
      toolName: name,
      input: args,
      output: result as Record<string, unknown>,
      status: 'success'
    };
  }

  /**
   * Tool 목록 조회.
   */
  async listTools(): Promise<{ tools: Array<{ name: string; description: string }> }> {
    return this.sendJsonRpc('tools/list') as Promise<{ tools: Array<{ name: string; description: string }> }>;
  }

  /**
   * SSE 스트림으로 응답 수신 (스트리밍 지원).
   */
  async sendMessageWithStreaming(
    history: ChatMessage[],
    content: string,
    onChunk: (chunk: string) => void
  ): Promise<{ content: string; toolCalls?: ToolCall[] }> {
    if (!this.serverUrl || !this.projectId) {
      throw new Error('Not connected to MCP server');
    }

    const request: JsonRpcRequest = {
      jsonrpc: '2.0',
      id: String(++this.requestId),
      method: 'chat/completions',
      params: {
        projectId: this.projectId,
        messages: [...history, { role: 'user', content }]
      }
    };

    const response = await fetch(this.serverUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream' // SSE 요청
      },
      body: JSON.stringify(request)
    });

    if (!response.body) {
      throw new Error('No response body');
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let fullContent = '';
    const toolCalls: ToolCall[] = [];

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      const chunk = decoder.decode(value, { stream: true });
      const lines = chunk.split('\n');

      for (const line of lines) {
        if (line.startsWith('data: ')) {
          const data = JSON.parse(line.slice(6));
          if (data.content) {
            fullContent += data.content;
            onChunk(data.content);
          }
          if (data.toolCall) {
            toolCalls.push(data.toolCall);
          }
        }
      }
    }

    return { content: fullContent, toolCalls };
  }
}

export const mcpProtocol = new McpProtocol();
```

---

## 8. MCP Client (Tools)

**파일**: `frontend/lib/mcp-client.ts`

```typescript
import { request } from './api';
import type {
  ListDocumentsInput,
  GetDocumentInput,
  SearchDocumentsInput,
  UpdateDocumentInput,
  PushToRemoteInput
} from './types';

/**
 * MCP Tools 클라이언트.
 * 백엔드 MCP 엔드포인트와 직접 통신.
 */
export const mcpClient = {
  // ===== 읽기 도구 =====

  listDocuments: (input: ListDocumentsInput) =>
    request('/mcp/tools/list_documents', {
      method: 'POST',
      body: JSON.stringify(input)
    }),

  getDocument: (input: GetDocumentInput) =>
    request('/mcp/tools/get_document', {
      method: 'POST',
      body: JSON.stringify(input)
    }),

  listDocumentVersions: (documentId: string) =>
    request('/mcp/tools/list_document_versions', {
      method: 'POST',
      body: JSON.stringify({ documentId })
    }),

  diffDocument: (documentId: string, fromCommitSha: string, toCommitSha: string) =>
    request('/mcp/tools/diff_document', {
      method: 'POST',
      body: JSON.stringify({ documentId, fromCommitSha, toCommitSha })
    }),

  searchDocuments: (input: SearchDocumentsInput) =>
    request('/mcp/tools/search_documents', {
      method: 'POST',
      body: JSON.stringify(input)
    }),

  syncRepository: (repositoryId: string, branch?: string) =>
    request('/mcp/tools/sync_repository', {
      method: 'POST',
      body: JSON.stringify({ repositoryId, branch })
    }),

  // ===== 쓰기 도구 (신규) =====

  createDocument: (input: CreateDocumentInput) =>
    request('/mcp/tools/create_document', {
      method: 'POST',
      body: JSON.stringify(input)
    }),

  updateDocument: (input: UpdateDocumentInput) =>
    request('/mcp/tools/update_document', {
      method: 'POST',
      body: JSON.stringify(input)
    }),

  pushToRemote: (input: PushToRemoteInput) =>
    request('/mcp/tools/push_to_remote', {
      method: 'POST',
      body: JSON.stringify(input)
    })
};
```

---

## 9. 타입 정의

**파일**: `frontend/lib/types.ts` (추가)

```typescript
// ===== Chat Types =====

export interface ChatMessage {
  id: string;                    // 중복 방지용 고유 ID
  role: 'user' | 'assistant';
  content: string;
  toolCalls?: ToolCall[];
  timestamp: Date;               // 메시지 시간
  isError?: boolean;             // 에러 메시지 여부
}

export interface ToolCall {
  toolName: string;
  input: Record<string, unknown>;
  output?: Record<string, unknown>;
  error?: string;
  status: 'pending' | 'success' | 'error';
}

export type McpConnectionStatus = 'connected' | 'disconnected' | 'connecting' | 'error';

// ===== JSON-RPC Types =====

export interface JsonRpcRequest {
  jsonrpc: '2.0';
  id: string;
  method: string;
  params?: Record<string, unknown>;
}

export interface JsonRpcResponse {
  jsonrpc: '2.0';
  id: string;
  result?: unknown;
  error?: { code: number; message: string };
}

// ===== MCP Tool Input Types =====

export interface ListDocumentsInput {
  repositoryId?: string;
  projectId?: string;
  pathPrefix?: string;
  type?: string;
}

export interface GetDocumentInput {
  documentId: string;
  commitSha?: string;
}

export interface SearchDocumentsInput {
  projectId: string;
  query: string;
  mode?: 'keyword' | 'semantic' | 'hybrid';
  topK?: number;
}

// 문서 생성 (신규)
export interface CreateDocumentInput {
  repositoryId: string;
  path: string;
  content: string;
  message?: string;
  branch?: string;
  createCommit: boolean;
}

export interface UpdateDocumentInput {
  documentId: string;
  content: string;
  message?: string;
  branch?: string;
  createCommit: boolean;
}

export interface PushToRemoteInput {
  repositoryId: string;
  branch?: string;
}
```

---

## 10. Playground 설정

**파일**: `frontend/components/playground/playground-settings.tsx`

```tsx
'use client';

import { useProjects } from '@/hooks/use-api';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

interface PlaygroundSettingsProps {
  projectId?: string;
  onProjectChange: (projectId: string) => void;
}

export function PlaygroundSettings({ projectId, onProjectChange }: PlaygroundSettingsProps) {
  const { data: projects } = useProjects();

  return (
    <div className="border-b p-4">
      <div className="flex items-center gap-4">
        <div className="flex-1">
          <Label>Project</Label>
          <Select value={projectId} onValueChange={onProjectChange}>
            <SelectTrigger>
              <SelectValue placeholder="Select a project" />
            </SelectTrigger>
            <SelectContent>
              {projects?.map((project) => (
                <SelectItem key={project.id} value={project.id}>
                  {project.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>
    </div>
  );
}
```

---

## 11. 문서 미리보기

**파일**: `frontend/components/playground/document-preview.tsx`

```tsx
'use client';

import { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { FileText } from 'lucide-react';
import ReactMarkdown from 'react-markdown';

interface DocumentPreviewProps {
  projectId?: string;
}

export function DocumentPreview({ projectId }: DocumentPreviewProps) {
  const [selectedDocument, setSelectedDocument] = useState<any>(null);

  if (!projectId) {
    return (
      <div className="flex h-full items-center justify-center p-4 text-muted-foreground">
        <p>Select a project to preview documents</p>
      </div>
    );
  }

  if (!selectedDocument) {
    return (
      <div className="flex h-full items-center justify-center p-4 text-muted-foreground">
        <p>No document selected</p>
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col">
      {/* 헤더 */}
      <div className="border-b p-4">
        <div className="flex items-center gap-2">
          <FileText className="h-5 w-5" />
          <div className="flex-1">
            <div className="font-medium">{selectedDocument.title}</div>
            <div className="text-xs text-muted-foreground">{selectedDocument.path}</div>
          </div>
          <Badge variant="outline">{selectedDocument.docType}</Badge>
        </div>
      </div>

      {/* 내용 */}
      <div className="flex-1 overflow-y-auto p-4">
        <div className="prose dark:prose-invert max-w-none">
          <ReactMarkdown>{selectedDocument.content}</ReactMarkdown>
        </div>
      </div>
    </div>
  );
}
```

---

## 필요한 shadcn/ui 컴포넌트

```bash
npx shadcn-ui@latest add avatar
npx shadcn-ui@latest add badge
npx shadcn-ui@latest add card
```

---

## 다국어 메시지

**파일**: `frontend/messages/en.json`

```json
{
  "playground": {
    "title": "LLM Playground",
    "description": "Chat with AI to search, read, and edit documents",
    "connectionStatus": {
      "connected": "Connected to MCP Server",
      "disconnected": "Disconnected",
      "error": "Connection Error"
    }
  }
}
```

**파일**: `frontend/messages/ko.json`

```json
{
  "playground": {
    "title": "LLM플레이그라운드",
    "description": "AI와 대화하면서 문서를 검색, 읽기, 수정할 수 있습니다",
    "connectionStatus": {
      "connected": "MCP 서버 연결됨",
      "disconnected": "연결 끊김",
      "error": "연결 오류"
    }
  }
}
```

---

## 구현 순서

1. **기본 UI 구조**
   - [ ] Playground 페이지 레이아웃
   - [ ] PlaygroundSettings 컴포넌트
   - [ ] ChatInterface 컴포넌트

2. **메시지 시스템**
   - [ ] MessageList 컴포넌트
   - [ ] MessageItem 컴포넌트
   - [ ] MessageInput 컴포넌트

3. **MCP 통합**
   - [ ] MCP Protocol 구현
   - [ ] MCP Client 구현
   - [ ] use-mcp-chat Hook

4. **Tool Call 시각화**
   - [ ] ToolCallInspector 컴포넌트

5. **문서 미리보기**
   - [ ] DocumentPreview 컴포넌트

6. **다국어 및 마무리**
   - [ ] 메시지 추가
   - [ ] 사이드바 메뉴
   - [ ] 테스트

---

## 완료 기준

- [ ] Playground 페이지 렌더링
- [ ] 프로젝트 선택 가능
- [ ] 메시지 입력 및 표시
- [ ] MCP Server 연결 동작
- [ ] Tool Call 시각화 동작
- [ ] 문서 미리보기 동작
- [ ] 다국어 지원