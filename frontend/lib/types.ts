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
  commitSha: string;
  chunkId: string | null;
  score: number;
  snippet: string;
  highlightedSnippet: string;
}

export interface SearchRequest {
  q: string;
  mode?: 'keyword' | 'semantic';
  topK?: number;
}

// ===== Sync =====
export type SyncStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';

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
