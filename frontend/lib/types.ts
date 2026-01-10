// ===== Auth =====
export interface AuthTokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface User {
  id: string;
  provider: string;
  providerUserId: string;
  email: string;
  displayName: string;
  createdAt: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  displayName: string;
}

export interface ChangePasswordRequest {
  oldPassword: string;
  newPassword: string;
}

// ===== API Keys =====
export interface ApiKey {
  id: string;
  name: string;
  keyPrefix: string;
  lastUsedAt: string | null;
  expiresAt: string | null;
  active: boolean;
  createdAt: string;
  defaultProjectId?: string | null;
}

export interface UpdateApiKeyDefaultProjectRequest {
  projectId: string | null;
}

export interface CreateApiKeyRequest {
  name: string;
  expiresInDays?: number;
}

export interface ApiKeyCreationResponse {
  id: string;
  name: string;
  key: string;  // Full key - only shown once!
  keyPrefix: string;
  expiresAt: string | null;
  createdAt: string;
}

export interface SetupStatusResponse {
  needsSetup: boolean;
  message: string;
  existingUserCount: number;
}

export interface InitializeRequest {
  email: string;
  password: string;
  displayName: string;
}

export interface InitializeResponse {
  userId: string;
  email: string;
  displayName: string;
  message: string;
}

// ===== Project =====
export interface Project {
  id: string;
  name: string;
  description: string;
  active: boolean;
  createdAt: string;
}

export interface CreateProjectRequest {
  name: string;
  description: string;
}

export interface UpdateProjectRequest {
  name?: string;
  description?: string;
  active?: boolean;
}

// ===== Repository =====
export type RepoProvider = 'GITHUB' | 'LOCAL';

export interface Repository {
  id: string;
  projectId: string;
  provider: RepoProvider;
  externalId: string;
  owner: string;
  name: string;
  cloneUrl: string;
  defaultBranch: string;
  localMirrorPath: string;
  active: boolean;
  createdAt: string;
  credentialId?: string | null;
}

export interface CreateRepositoryRequest {
  provider: RepoProvider;
  owner: string;
  name: string;
  defaultBranch?: string;
  localPath?: string;
}

export interface UpdateRepositoryRequest {
  active?: boolean;
  defaultBranch?: string;
}

export interface MoveRepositoryRequest {
  targetProjectId: string;
}

// ===== Document =====
export type DocType = 'MD' | 'ADOC' | 'OPENAPI' | 'ADR' | 'OTHER';

export interface Document {
  id: string;
  repositoryId: string;
  path: string;
  title: string;
  docType: DocType;
  latestCommitSha: string;
  createdAt: string;
}

export interface DocumentDetail extends Document {
  content: string;
  authorName: string;
  authorEmail: string;
  committedAt: string;
}

export interface DocumentVersion {
  id: string;
  documentId: string;
  commitSha: string;
  authorName: string;
  authorEmail: string;
  committedAt: string;
  message: string;
  contentHash: string;
}

export interface DocumentVersionDetail extends DocumentVersion {
  content: string;
}

// ===== Search =====
export interface SearchResult {
  documentId: string;
  repositoryId: string;
  path: string;
  documentPath: string;
  title: string;
  commitSha: string;
  chunkId: string | null;
  score: number;
  snippet: string;
  highlightedSnippet: string;
  highlights: string[];
  // Phase 2-C: 의미 검색 추가 필드
  headingPath?: string;
  tokenCount?: number;
  docType?: DocType;
}

export interface SearchRequest {
  q: string;
  mode?: 'keyword' | 'semantic' | 'hybrid';
  topK?: number;
  repositoryId?: string;  // 저장소 필터
  docType?: DocType;      // 문서 타입 필터
}

// ===== Sync =====
export type SyncStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
export type SyncMode = 'FULL_SCAN' | 'INCREMENTAL' | 'SPECIFIC_COMMIT';

export interface SyncJob {
  id: string;
  repositoryId: string;
  status: SyncStatus;
  targetBranch: string;
  lastSyncedCommit: string | null;
  errorMessage: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  createdAt: string;
}

export interface SyncRequest {
  branch?: string;
  mode?: SyncMode;
  targetCommitSha?: string;
  enableEmbedding?: boolean;
}

export interface PushResult {
  success: boolean;
  message: string;
  branch: string | null;
}

// ===== SSE Events =====
export interface SyncEvent {
  jobId: string;
  status: string;
  message: string;
  progress: number;
  totalDocs: number;
  processedDocs: number;
}

// ===== Credential =====
export type CredentialType =
  | 'GITHUB_PAT'
  | 'BASIC_AUTH'
  | 'SSH_KEY'
  | 'OPENAI_API_KEY'
  | 'NEO4J_AUTH'
  | 'PGVECTOR_AUTH'
  | 'ANTHROPIC_API_KEY'
  | 'CUSTOM_API_KEY';

export type CredentialScope = 'USER' | 'SYSTEM' | 'PROJECT';

export interface Credential {
  id: string;
  name: string;
  type: CredentialType;
  username: string | null;
  description: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string | null;
}

export interface CreateCredentialRequest {
  name: string;
  type: CredentialType;
  username?: string;
  secret: string;
  description?: string;
}

export interface UpdateCredentialRequest {
  username?: string;
  secret?: string;
  description?: string;
  active?: boolean;
}

export interface SetCredentialRequest {
  credentialId: string | null;
}

// ===== System Config (Phase 4-E, ADMIN only) =====
export interface SystemConfig {
  id: string;
  configKey: string;
  configValue: string;
  configType: string;
  description: string | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface UpdateSystemConfigRequest {
  configValue: string;
  configType?: string;
  description?: string;
}

// ===== System & Project Credentials (Phase 4-E) =====
export interface SystemCredential {
  id: string;
  name: string;
  type: CredentialType;
  scope: CredentialScope;
  description: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string | null;
}

export interface CreateSystemCredentialRequest {
  name: string;
  type: CredentialType;
  secret: string;
  description?: string;
}

export interface UpdateSystemCredentialRequest {
  secret?: string;
  description?: string;
}

export interface ProjectCredential {
  id: string;
  projectId: string;
  name: string;
  type: CredentialType;
  scope: CredentialScope;
  description: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string | null;
}

export interface CreateProjectCredentialRequest {
  name: string;
  type: CredentialType;
  secret: string;
  description?: string;
}

export interface UpdateProjectCredentialRequest {
  secret?: string;
  description?: string;
}

// ===== Commit =====
export type ChangeType = 'ADDED' | 'MODIFIED' | 'DELETED' | 'RENAMED';

export interface Commit {
  sha: string;
  message: string;
  authorName: string;
  authorEmail: string;
  committedAt: string;
}

export interface ChangedFile {
  path: string;
  oldPath: string | null;
  changeType: ChangeType;
}

export interface CommitDetail extends Commit {
  changedFiles: ChangedFile[];
}

export interface CommitListParams {
  skip?: number;
  limit?: number;
}

export interface CommitDiffParams {
  from: string;
  to: string;
}

export interface UnpushedCommitsResponse {
  branch: string;
  commits: Commit[];
  totalCount: number;
  hasPushableCommits: boolean;
}

// ===== Stats =====
export interface StatsResponse {
  totalProjects: number;
  totalRepositories: number;
  totalDocuments: number;
}

// ===== RAG Config Types =====

export interface RagConfigResponse {
  projectId: string;
  embedding: EmbeddingConfigResponse;
  pgvector: PgVectorConfigResponse;
  neo4j: Neo4jConfigResponse;
  hybrid: HybridConfigResponse;
  updatedAt: string;
}

export interface EmbeddingConfigResponse {
  provider: string;
  model: string;
  dimensions: number;
}

export interface PgVectorConfigResponse {
  enabled: boolean;
  similarityThreshold: number;
}

export interface Neo4jConfigResponse {
  enabled: boolean;
  maxHop: number;
  entityExtractionModel: string;
}

export interface HybridConfigResponse {
  fusionStrategy: string;
  rrfK: number;
  vectorWeight: number;
  graphWeight: number;
}

export interface UpdateRagConfigRequest {
  embedding?: EmbeddingConfigRequest;
  pgvector?: PgVectorConfigRequest;
  neo4j?: Neo4jConfigRequest;
  hybrid?: HybridConfigRequest;
}

export interface EmbeddingConfigRequest {
  provider?: string;
  model?: string;
  dimensions?: number;
}

export interface PgVectorConfigRequest {
  enabled?: boolean;
  similarityThreshold?: number;
}

export interface Neo4jConfigRequest {
  enabled?: boolean;
  maxHop?: number;
  entityExtractionModel?: string;
}

export interface HybridConfigRequest {
  fusionStrategy?: string;
  rrfK?: number;
  vectorWeight?: number;
  graphWeight?: number;
}

export interface RagConfigValidationResponse {
  valid: boolean;
  errors: string[];
  warnings: string[];
}

export interface RagDefaults {
  embedding: EmbeddingConfigResponse;
  pgvector: PgVectorConfigResponse;
  neo4j: Neo4jConfigResponse;
  hybrid: HybridConfigResponse;
}

export interface ReEmbeddingTriggerResponse {
  projectId: string;
  message: string;
  inProgress: boolean;
}

export interface ReEmbeddingStatusResponse {
  projectId: string;
  inProgress: boolean;
  totalVersions: number;
  processedVersions: number;
  progress: number;
  deletedEmbeddings: number;
  embeddedCount: number;
  failedCount: number;
  errorMessage?: string;
}

// ===== Health Check =====
export interface HealthCheckResponse {
  status: 'UP' | 'DEGRADED' | 'DOWN';
  timestamp: string;
  services: Record<string, ServiceHealth>;
}

export interface ServiceHealth {
  status: 'UP' | 'DOWN' | 'UNKNOWN';
  message: string;
  details: Record<string, any>;
}

// ===== Phase 5: MCP & Playground =====

// JSON-RPC 2.0
export interface JsonRpcRequest {
  jsonrpc: '2.0';
  id: string | number;
  method: string;
  params?: unknown;
}

export interface JsonRpcResponse<T = unknown> {
  jsonrpc: '2.0';
  id: string | number;
  result?: T;
  error?: JsonRpcError;
}

export interface JsonRpcError {
  code: number;
  message: string;
  data?: unknown;
}

// MCP Tool Definitions
export interface McpToolDefinition {
  name: string;
  description: string;
  inputSchema: Record<string, unknown>;
}

export interface McpToolsListResult {
  tools: McpToolDefinition[];
}

// Chat Messages
export type MessageRole = 'user' | 'assistant' | 'system';

export interface ChatMessage {
  id: string;
  role: MessageRole;
  content: string;
  timestamp: Date;
  toolCalls?: ToolCall[];
  isError?: boolean;
  isStreaming?: boolean;  // Phase 6: streaming indicator
  citations?: Citation[];  // Phase 6: RAG citations
}

export interface ToolCall {
  toolName: string;
  input: Record<string, unknown>;
  output?: unknown;
  error?: string;
  duration?: number;
}

// MCP Tool Call Request/Response
export interface McpToolCallRequest {
  name: string;
  arguments: Record<string, unknown>;
}

export interface McpResponse<T = unknown> {
  result: T | null;
  error: { message: string } | null;
}

// Playground State
export interface PlaygroundState {
  projectId?: string;
  connected: boolean;
  tools: McpToolDefinition[];
}

// ===== LLM Chat Types (Phase 6) =====

export interface ChatRequest {
  message: string;
  projectId: string;
  sessionId: string;
}

export interface ChatResponse {
  content: string;
}

export interface TemplateVariable {
  name: string;
  label: string;
  placeholder: string;
  defaultValue?: string | null;
}

export interface PromptTemplate {
  id: string;
  name: string;
  description: string;
  category: string;
  template: string;
  variables: TemplateVariable[];
}

// RAG Citation (document source referenced during LLM response)
export interface Citation {
  documentId: string;
  repositoryId: string | null;
  path: string;
  title: string | null;
  headingPath: string | null;
  chunkId: string | null;
  score: number;
  snippet: string;
}

// SSE Stream Event types for LLM chat
export type SSEEvent =
  | { type: 'content'; content: string }
  | { type: 'citations'; citations: Citation[] };

// ===== Document Editor (Phase 8) =====

export type EditorViewMode = 'source' | 'split';

export interface UpdateDocumentRequest {
  content: string;
  commitMessage: string;
  branch?: string;
}

export interface UpdateDocumentResponse {
  documentId: string;
  path: string;
  commitSha: string;
  message: string;
}
