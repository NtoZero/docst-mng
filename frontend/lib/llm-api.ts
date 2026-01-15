import type { ChatRequest, ChatResponse, PromptTemplate, SSEEvent } from './types';
import { getAuthTokenAsync } from './auth-utils';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8342';

/**
 * LLM Chat API (동기)
 *
 * 전체 응답을 한 번에 반환합니다.
 */
export async function sendChatMessage(request: ChatRequest): Promise<ChatResponse> {
  const token = await getAuthTokenAsync();

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
 * Server-Sent Events를 통해 실시간 응답을 수신합니다.
 * AsyncGenerator로 SSEEvent를 반환합니다.
 *
 * SSE Event Types:
 * - { type: 'content', content: string } - 텍스트 청크
 * - { type: 'citations', citations: Citation[] } - RAG 출처 정보 (스트리밍 완료 후)
 *
 * @example
 * ```ts
 * const request = { message: "Hello", projectId: "...", sessionId: "..." };
 * for await (const event of streamChatMessage(request)) {
 *   if (event.type === 'content') {
 *     console.log(event.content); // "Hello", " world", "!"
 *   } else if (event.type === 'citations') {
 *     console.log(event.citations); // [{documentId, path, ...}, ...]
 *   }
 * }
 * ```
 */
export async function* streamChatMessage(
  request: ChatRequest,
  signal?: AbortSignal
): AsyncGenerator<SSEEvent> {
  const token = await getAuthTokenAsync();

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
    signal,
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
        // 빈 줄 체크 - SSE에서 빈 줄은 이벤트 구분자
        if (line === '') continue;

        // SSE format: "data: <content>" 또는 "data:<content>"
        if (line.startsWith('data:')) {
          // "data:" 제거 (5글자)
          const rawData = line.substring(5);

          // SSE 스펙: "data:" 바로 뒤의 선행 공백 하나만 선택적 제거
          const data = rawData.startsWith(' ') ? rawData.substring(1) : rawData;

          // [DONE] 마커는 스킵
          if (data === '[DONE]') continue;

          // 빈 문자열 스킵
          if (data === '') continue;

          // JSON 형식 파싱
          // Format: {"type":"content","content":"..."} or {"type":"citations","citations":[...]}
          try {
            const parsed = JSON.parse(data);

            if (parsed.type === 'content' && typeof parsed.content === 'string') {
              // Content 이벤트
              console.log('SSE content:', JSON.stringify(parsed.content).slice(0, 50));
              yield { type: 'content', content: parsed.content };
            } else if (parsed.type === 'citations' && Array.isArray(parsed.citations)) {
              // Citations 이벤트
              console.log('SSE citations:', parsed.citations.length, 'items');
              yield { type: 'citations', citations: parsed.citations };
            } else if (typeof parsed.content === 'string') {
              // Legacy format fallback: {"content":"..."}
              yield { type: 'content', content: parsed.content };
            }
          } catch {
            // JSON 파싱 실패 시 raw data를 content로 사용 (fallback)
            console.log('SSE chunk (raw):', JSON.stringify(data).slice(0, 50));
            yield { type: 'content', content: data };
          }
        }
      }
    }
  } finally {
    reader.releaseLock();
  }
}

/**
 * 프롬프트 템플릿 목록 조회
 *
 * 시스템 기본 템플릿을 가져옵니다.
 */
export async function getPromptTemplates(): Promise<PromptTemplate[]> {
  const token = await getAuthTokenAsync();

  const headers: HeadersInit = {};

  if (token) {
    (headers as Record<string, string>)['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE}/api/llm/templates`, {
    method: 'GET',
    headers,
  });

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || response.statusText);
  }

  return response.json();
}
