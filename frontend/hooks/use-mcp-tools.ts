'use client';

import { useState, useCallback, useRef } from 'react';
import { mcpClient } from '@/lib/mcp-client';
import type { ChatMessage, ToolCall } from '@/lib/types';

/**
 * MCP Tools를 직접 호출하는 Hook.
 * 간단한 명령어 파싱을 통해 MCP 도구를 테스트합니다.
 *
 * Phase 5 MVP: LLM 통합 없이 도구만 테스트
 */
export function useMcpTools() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const messageIdCounter = useRef(0);

  const addMessage = useCallback(
    (role: 'user' | 'assistant' | 'system', content: string, toolCalls?: ToolCall[], isError?: boolean) => {
      const message: ChatMessage = {
        id: `msg-${++messageIdCounter.current}`,
        role,
        content,
        timestamp: new Date(),
        toolCalls,
        isError,
      };
      setMessages((prev) => [...prev, message]);
      return message;
    },
    []
  );

  const handleMessage = useCallback(
    async (userMessage: string) => {
      // 1. 사용자 메시지 추가
      addMessage('user', userMessage);

      setIsLoading(true);

      try {
        // 2. 간단한 명령어 파싱
        const result = await parseAndExecute(userMessage);

        // 3. 결과 메시지 추가
        addMessage('assistant', result.message, result.toolCalls, result.isError);
      } catch (error) {
        addMessage(
          'assistant',
          `Error: ${error instanceof Error ? error.message : 'Unknown error'}`,
          undefined,
          true
        );
      } finally {
        setIsLoading(false);
      }
    },
    [addMessage]
  );

  return {
    messages,
    sendMessage: handleMessage,
    isLoading,
    clearMessages: () => setMessages([]),
  };
}

/**
 * 간단한 명령어 파싱 및 MCP 도구 실행.
 * 실제 LLM 없이 도구를 테스트하기 위한 임시 구현입니다.
 */
async function parseAndExecute(message: string): Promise<{
  message: string;
  toolCalls?: ToolCall[];
  isError?: boolean;
}> {
  const lowerMsg = message.toLowerCase();

  // ping 명령
  if (lowerMsg.includes('ping')) {
    const startTime = Date.now();
    const result = await mcpClient.ping();
    const duration = Date.now() - startTime;

    return {
      message: 'Pong! Server is alive.',
      toolCalls: [
        {
          toolName: 'ping',
          input: {},
          output: result,
          duration,
        },
      ],
    };
  }

  // list tools 명령
  if (lowerMsg.includes('list tools') || lowerMsg.includes('show tools')) {
    const startTime = Date.now();
    const result = await mcpClient.getTools();
    const duration = Date.now() - startTime;

    const toolNames = result.tools.map((t) => t.name).join(', ');

    return {
      message: `Available tools: ${toolNames}`,
      toolCalls: [
        {
          toolName: 'tools/list',
          input: {},
          output: result,
          duration,
        },
      ],
    };
  }

  // 기타 명령어는 안내 메시지
  return {
    message: `I'm a simple MCP tool tester. Try these commands:
- "ping" - Check server health
- "list tools" - Show available MCP tools

For full LLM integration, please wait for Phase 6.`,
  };
}
