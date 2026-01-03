import type { ChatRequest, ChatResponse, PromptTemplate } from './types';

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
 *
 * 전체 응답을 한 번에 반환합니다.
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
 * Server-Sent Events를 통해 실시간 응답을 수신합니다.
 * AsyncGenerator로 청크 단위 텍스트를 반환합니다.
 *
 * @example
 * ```ts
 * const request = { message: "Hello", projectId: "...", sessionId: "..." };
 * for await (const chunk of streamChatMessage(request)) {
 *   console.log(chunk); // "Hello", " world", "!"
 * }
 * ```
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
        // 빈 줄 체크 (trim 사용)
        if (line.trim() === '') continue;

        // SSE format: "data: <content>" 또는 "data:<content>"
        if (line.startsWith('data:')) {
          // "data:" 제거 (5글자)
          let data = line.substring(5);

          // "data: " 형식인 경우 첫 번째 공백 하나만 제거
          if (data.startsWith(' ')) {
            data = data.substring(1);
          }

          // 디버깅: 실제 청크 내용 로깅
          if (data !== '' && data !== '[DONE]') {
            console.log('SSE chunk:', JSON.stringify(data), 'length:', data.length);
            yield data;
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
  const token = await getAuthToken();

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
