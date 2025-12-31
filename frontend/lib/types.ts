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
export type CredentialType = 'GITHUB_PAT' | 'BASIC_AUTH' | 'SSH_KEY';

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
