import type {
  JsonRpcRequest,
  JsonRpcResponse,
  McpToolsListResult,
  McpToolCallRequest,
} from './types';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8342';

/**
 * MCP Protocol 클라이언트.
 * JSON-RPC 2.0 프로토콜을 사용하여 MCP 서버와 통신합니다.
 *
 * 지원 Transport:
 * - HTTP Streamable (POST /mcp)
 * - SSE (GET /mcp/stream)
 */
export class McpProtocol {
  private requestId = 0;
  private readonly serverUrl: string;
  private eventSource: EventSource | null = null;
  private eventHandlers: Map<string, (data: unknown) => void> = new Map();

  constructor(serverUrl?: string) {
    this.serverUrl = serverUrl || `${API_BASE}/mcp`;
  }

  /**
   * JSON-RPC 요청을 서버로 전송합니다 (HTTP Streamable).
   *
   * @param method JSON-RPC 메서드 이름
   * @param params 메서드 파라미터
   * @returns JSON-RPC 응답 결과
   */
  private async sendJsonRpc<T = unknown>(
    method: string,
    params?: unknown
  ): Promise<T> {
    const request: JsonRpcRequest = {
      jsonrpc: '2.0',
      id: ++this.requestId,
      method,
      params,
    };

    const response = await fetch(this.serverUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }

    const jsonRpcResponse: JsonRpcResponse<T> = await response.json();

    if (jsonRpcResponse.error) {
      throw new Error(
        `JSON-RPC Error ${jsonRpcResponse.error.code}: ${jsonRpcResponse.error.message}`
      );
    }

    return jsonRpcResponse.result as T;
  }

  /**
   * MCP 서버 초기화.
   *
   * @returns 서버 정보
   */
  async initialize(): Promise<{
    protocolVersion: string;
    serverInfo: {
      name: string;
      version: string;
    };
    capabilities: Record<string, unknown>;
  }> {
    return this.sendJsonRpc('initialize', {
      protocolVersion: '2024-11-05',
      clientInfo: {
        name: 'Docst Playground',
        version: '1.0.0',
      },
    });
  }

  /**
   * 사용 가능한 MCP 도구 목록을 조회합니다.
   *
   * @returns 도구 목록
   */
  async listTools(): Promise<McpToolsListResult> {
    return this.sendJsonRpc<McpToolsListResult>('tools/list');
  }

  /**
   * MCP 도구를 호출합니다.
   *
   * @param toolName 도구 이름
   * @param args 도구 인자
   * @returns 도구 실행 결과
   */
  async callTool<T = unknown>(
    toolName: string,
    args: Record<string, unknown>
  ): Promise<T> {
    const request: McpToolCallRequest = {
      name: toolName,
      arguments: args,
    };

    return this.sendJsonRpc<T>('tools/call', request);
  }

  /**
   * 서버 핑 (헬스 체크).
   *
   * @returns pong 응답
   */
  async ping(): Promise<{ status: string }> {
    return this.sendJsonRpc('ping');
  }

  /**
   * SSE 스트림에 연결합니다.
   *
   * @param onEvent 이벤트 핸들러
   * @param clientId 클라이언트 ID (선택)
   */
  connectStream(
    onEvent: (eventName: string, data: unknown) => void,
    clientId?: string
  ): void {
    const streamUrl = clientId
      ? `${this.serverUrl}/stream?clientId=${clientId}`
      : `${this.serverUrl}/stream`;

    this.eventSource = new EventSource(streamUrl);

    this.eventSource.addEventListener('connected', (event) => {
      const data = JSON.parse(event.data);
      onEvent('connected', data);
    });

    this.eventSource.addEventListener('message', (event) => {
      onEvent('message', JSON.parse(event.data));
    });

    this.eventSource.addEventListener('error', (error) => {
      console.error('SSE connection error:', error);
      onEvent('error', error);
    });

    // 커스텀 이벤트 핸들러 등록
    this.eventHandlers.forEach((handler, eventName) => {
      this.eventSource!.addEventListener(eventName, (event) => {
        handler(JSON.parse((event as MessageEvent).data));
      });
    });
  }

  /**
   * SSE 이벤트 핸들러를 등록합니다.
   *
   * @param eventName 이벤트 이름
   * @param handler 핸들러 함수
   */
  on(eventName: string, handler: (data: unknown) => void): void {
    this.eventHandlers.set(eventName, handler);
  }

  /**
   * SSE 스트림 연결을 종료합니다.
   */
  disconnectStream(): void {
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
  }

  /**
   * 연결 상태를 확인합니다.
   *
   * @returns 연결 여부
   */
  isConnected(): boolean {
    return (
      this.eventSource !== null &&
      this.eventSource.readyState === EventSource.OPEN
    );
  }
}
