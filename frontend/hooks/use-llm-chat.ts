'use client';

import { useState, useCallback, useRef } from 'react';
import { streamChatMessage } from '@/lib/llm-api';
import type { ChatRequest, ChatMessage, Citation } from '@/lib/types';

// Generate UUID compatible with browser
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
        let citations: Citation[] = [];

        for await (const event of streamChatMessage(
          request,
          abortControllerRef.current.signal
        )) {
          if (event.type === 'content') {
            // Content event: 텍스트 청크 누적
            assistantContent += event.content;

            setMessages((prev) =>
              prev.map((msg) =>
                msg.id === assistantMsgId
                  ? { ...msg, content: assistantContent }
                  : msg
              )
            );
          } else if (event.type === 'citations') {
            // Citations event: RAG 출처 정보 저장
            citations = event.citations;

            setMessages((prev) =>
              prev.map((msg) =>
                msg.id === assistantMsgId
                  ? { ...msg, citations }
                  : msg
              )
            );
          }
        }

        // 5. Mark streaming complete
        setMessages((prev) =>
          prev.map((msg) =>
            msg.id === assistantMsgId
              ? { ...msg, isStreaming: false, citations }
              : msg
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
          console.error('LLM chat error:', error);
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
