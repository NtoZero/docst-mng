import { McpProtocol } from './mcp-protocol';
import type { McpResponse } from './types';

/**
 * MCP Tools 클라이언트.
 * MCP Protocol을 사용하여 각 도구를 타입 안전하게 호출합니다.
 */
export class McpClient {
  private protocol: McpProtocol;

  constructor(serverUrl?: string) {
    this.protocol = new McpProtocol(serverUrl);
  }

  /**
   * MCP 서버에 연결하고 초기화합니다.
   */
  async connect(): Promise<void> {
    await this.protocol.initialize();
  }

  /**
   * 사용 가능한 도구 목록을 가져옵니다.
   */
  async getTools() {
    return this.protocol.listTools();
  }

  /**
   * MCP 도구를 호출합니다 (제네릭 버전).
   *
   * @param toolName 도구 이름
   * @param args 도구 인자
   * @returns 도구 실행 결과
   */
  async callTool<T = unknown>(
    toolName: string,
    args: Record<string, unknown>
  ): Promise<T> {
    return this.protocol.callTool<T>(toolName, args);
  }

  // ===== READ 도구들 =====

  /**
   * 프로젝트 또는 레포지토리의 문서 목록을 조회합니다.
   */
  async listDocuments(params: {
    projectId?: string;
    repositoryId?: string;
    pathPrefix?: string;
    type?: string;
  }): Promise<
    McpResponse<{
      documents: Array<{
        id: string;
        repositoryId: string;
        path: string;
        title: string;
        docType: string;
        latestCommitSha: string;
      }>;
    }>
  > {
    return this.callTool('list_documents', params);
  }

  /**
   * 문서 내용을 조회합니다.
   */
  async getDocument(params: {
    documentId: string;
    commitSha?: string;
  }): Promise<
    McpResponse<{
      id: string;
      repositoryId: string;
      path: string;
      title: string;
      docType: string;
      commitSha: string;
      content: string;
      authorName: string;
      committedAt: string;
    }>
  > {
    return this.callTool('get_document', params);
  }

  /**
   * 문서 버전 목록을 조회합니다.
   */
  async listDocumentVersions(params: { documentId: string }): Promise<
    McpResponse<{
      versions: Array<{
        commitSha: string;
        authorName: string;
        authorEmail: string;
        committedAt: string;
        message: string;
      }>;
    }>
  > {
    return this.callTool('list_document_versions', params);
  }

  /**
   * 두 버전 간 diff를 조회합니다.
   */
  async diffDocument(params: {
    documentId: string;
    fromCommitSha: string;
    toCommitSha: string;
  }): Promise<McpResponse<{ diff: string }>> {
    return this.callTool('diff_document', params);
  }

  /**
   * 문서를 검색합니다.
   */
  async searchDocuments(params: {
    projectId: string;
    query: string;
    mode?: 'keyword' | 'semantic' | 'hybrid';
    topK?: number;
  }): Promise<
    McpResponse<{
      results: Array<{
        documentId: string;
        path: string;
        title: string | null;
        headingPath: string | null;
        score: number;
        snippet: string;
        content: string | null;
      }>;
      metadata: {
        mode: string;
        totalResults: number;
        queryTime: string;
      };
    }>
  > {
    return this.callTool('search_documents', params);
  }

  /**
   * 레포지토리 동기화를 트리거합니다.
   */
  async syncRepository(params: {
    repositoryId: string;
    branch?: string;
  }): Promise<
    McpResponse<{
      jobId: string;
      status: string;
    }>
  > {
    return this.callTool('sync_repository', params);
  }

  // ===== WRITE 도구들 (Phase 5) =====

  /**
   * 새 문서를 생성합니다.
   */
  async createDocument(params: {
    repositoryId: string;
    path: string;
    content: string;
    message?: string;
    branch?: string;
    createCommit?: boolean;
  }): Promise<
    McpResponse<{
      documentId: string | null;
      path: string;
      newCommitSha: string | null;
      committed: boolean;
      message: string;
    }>
  > {
    return this.callTool('create_document', params);
  }

  /**
   * 기존 문서를 수정합니다.
   */
  async updateDocument(params: {
    documentId: string;
    content: string;
    message?: string;
    branch?: string;
    createCommit?: boolean;
  }): Promise<
    McpResponse<{
      documentId: string;
      path: string;
      newCommitSha: string | null;
      committed: boolean;
      message: string;
    }>
  > {
    return this.callTool('update_document', params);
  }

  /**
   * 로컬 커밋을 원격으로 푸시합니다.
   */
  async pushToRemote(params: {
    repositoryId: string;
    branch?: string;
  }): Promise<
    McpResponse<{
      repositoryId: string;
      branch: string;
      success: boolean;
      message: string;
    }>
  > {
    return this.callTool('push_to_remote', params);
  }

  /**
   * 서버 핑 (헬스 체크).
   */
  async ping() {
    return this.protocol.ping();
  }

  /**
   * SSE 스트림에 연결합니다.
   */
  connectStream(onEvent: (eventName: string, data: unknown) => void) {
    this.protocol.connectStream(onEvent);
  }

  /**
   * SSE 스트림 연결을 종료합니다.
   */
  disconnectStream() {
    this.protocol.disconnectStream();
  }

  /**
   * 연결 상태를 확인합니다.
   */
  isConnected(): boolean {
    return this.protocol.isConnected();
  }
}

// 싱글톤 인스턴스 (선택적)
export const mcpClient = new McpClient();
