import type {
  AuthTokenResponse,
  User,
  LoginRequest,
  RegisterRequest,
  ChangePasswordRequest,
  ApiKey,
  CreateApiKeyRequest,
  ApiKeyCreationResponse,
  SetupStatusResponse,
  InitializeRequest,
  InitializeResponse,
  Project,
  CreateProjectRequest,
  UpdateProjectRequest,
  Repository,
  CreateRepositoryRequest,
  UpdateRepositoryRequest,
  Document,
  DocumentDetail,
  DocumentVersion,
  DocumentVersionDetail,
  SearchResult,
  SearchRequest,
  SyncJob,
  SyncRequest,
  Credential,
  CreateCredentialRequest,
  UpdateCredentialRequest,
  SetCredentialRequest,
  Commit,
  CommitDetail,
  CommitListParams,
  CommitDiffParams,
  StatsResponse,
  RagConfigResponse,
  UpdateRagConfigRequest,
  RagConfigValidationResponse,
  RagDefaults,
  ReEmbeddingTriggerResponse,
  ReEmbeddingStatusResponse,
  UpdateDocumentRequest,
  UpdateDocumentResponse,
  PushResult,
  UnpushedCommitsResponse,
  RepositorySyncConfig,
  UpdateRepositorySyncConfigRequest,
  FolderTreeResponse,
} from './types';
import { getAuthTokenAsync } from './auth-utils';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8342';

class ApiError extends Error {
  constructor(
    public status: number,
    message: string
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = await getAuthTokenAsync();

  const headers: HeadersInit = {
    'Content-Type': 'application/json',
    ...options.headers,
  };

  if (token) {
    (headers as Record<string, string>)['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  });

  if (!response.ok) {
    let errorMessage = response.statusText;
    try {
      const text = await response.text();
      if (text) {
        try {
          const errorData = JSON.parse(text);
          errorMessage = errorData.message || errorData.error || errorMessage;
        } catch {
          // If JSON parsing fails, use text as-is
          errorMessage = text;
        }
      }
    } catch {
      // Keep default statusText
    }
    throw new ApiError(response.status, errorMessage);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json();
}

// ===== Auth API =====
export const authApi = {
  localLogin: (data: LoginRequest): Promise<AuthTokenResponse> =>
    request('/api/auth/local/login', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  register: (data: RegisterRequest): Promise<AuthTokenResponse> =>
    request('/api/auth/local/register', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  changePassword: (data: ChangePasswordRequest): Promise<{ message: string }> =>
    request('/api/auth/change-password', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  me: (): Promise<User> => request('/api/auth/me'),
};

// ===== API Keys API =====
export const apiKeysApi = {
  list: (): Promise<ApiKey[]> => request('/api/auth/api-keys'),

  create: (data: CreateApiKeyRequest): Promise<ApiKeyCreationResponse> =>
    request('/api/auth/api-keys', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  revoke: (id: string): Promise<void> =>
    request(`/api/auth/api-keys/${id}`, {
      method: 'DELETE',
    }),

  updateDefaultProject: (id: string, projectId: string | null): Promise<ApiKey> =>
    request(`/api/auth/api-keys/${id}/default-project`, {
      method: 'PATCH',
      body: JSON.stringify({ projectId }),
    }),
};

// ===== Setup API =====
export const setupApi = {
  getStatus: (): Promise<SetupStatusResponse> => request('/api/setup/status'),

  initialize: (data: InitializeRequest): Promise<InitializeResponse> =>
    request('/api/setup/initialize', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
};

// ===== Projects API =====
export const projectsApi = {
  list: (): Promise<Project[]> => request('/api/projects'),

  get: (id: string): Promise<Project> => request(`/api/projects/${id}`),

  create: (data: CreateProjectRequest): Promise<Project> =>
    request('/api/projects', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  update: (id: string, data: UpdateProjectRequest): Promise<Project> =>
    request(`/api/projects/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  delete: (id: string): Promise<void> =>
    request(`/api/projects/${id}`, {
      method: 'DELETE',
    }),

  search: (id: string, params: SearchRequest): Promise<SearchResult[]> => {
    const query = new URLSearchParams();
    query.set('q', params.q);
    if (params.mode) query.set('mode', params.mode);
    if (params.topK) query.set('topK', String(params.topK));
    return request(`/api/projects/${id}/search?${query.toString()}`);
  },
};

// ===== Repositories API =====
export const repositoriesApi = {
  listByProject: (projectId: string): Promise<Repository[]> =>
    request(`/api/projects/${projectId}/repositories`),

  get: (id: string): Promise<Repository> => request(`/api/repositories/${id}`),

  create: (projectId: string, data: CreateRepositoryRequest): Promise<Repository> =>
    request(`/api/projects/${projectId}/repositories`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  update: (id: string, data: UpdateRepositoryRequest): Promise<Repository> =>
    request(`/api/repositories/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  delete: (id: string): Promise<void> =>
    request(`/api/repositories/${id}`, {
      method: 'DELETE',
    }),

  sync: (id: string, data: SyncRequest = {}): Promise<SyncJob> =>
    request(`/api/repositories/${id}/sync`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  getSyncStatus: (id: string): Promise<SyncJob> => request(`/api/repositories/${id}/sync/status`),

  getSyncStreamUrl: (id: string): string => `${API_BASE}/api/repositories/${id}/sync/stream`,

  setCredential: (id: string, data: SetCredentialRequest): Promise<Repository> =>
    request(`/api/repositories/${id}/credential`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  move: (id: string, targetProjectId: string): Promise<Repository> =>
    request(`/api/repositories/${id}/move`, {
      method: 'POST',
      body: JSON.stringify({ targetProjectId }),
    }),

  push: (id: string, branch?: string): Promise<PushResult> =>
    request(`/api/repositories/${id}/push`, {
      method: 'POST',
      body: JSON.stringify({ branch }),
    }),

  getUnpushedCommits: (id: string, branch?: string): Promise<UnpushedCommitsResponse> => {
    const params = branch ? `?branch=${encodeURIComponent(branch)}` : '';
    return request(`/api/repositories/${id}/commits/unpushed${params}`);
  },

  // Phase 12: Sync Config APIs
  getSyncConfig: (id: string): Promise<RepositorySyncConfig> =>
    request(`/api/repositories/${id}/sync-config`),

  updateSyncConfig: (id: string, data: UpdateRepositorySyncConfigRequest): Promise<RepositorySyncConfig> =>
    request(`/api/repositories/${id}/sync-config`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  getFolderTree: (id: string, depth: number = 4): Promise<FolderTreeResponse> =>
    request(`/api/repositories/${id}/folder-tree?depth=${depth}`),
};

// ===== Documents API =====
export const documentsApi = {
  listByRepository: (repositoryId: string): Promise<Document[]> =>
    request(`/api/repositories/${repositoryId}/documents`),

  get: (id: string): Promise<DocumentDetail> => request(`/api/documents/${id}`),

  getVersions: (id: string): Promise<DocumentVersion[]> => request(`/api/documents/${id}/versions`),

  getVersion: (id: string, commitSha: string): Promise<DocumentVersionDetail> =>
    request(`/api/documents/${id}/versions/${commitSha}`),

  getDiff: (id: string, from: string, to: string): Promise<string> => {
    const query = new URLSearchParams({ from, to });
    return request(`/api/documents/${id}/diff?${query.toString()}`);
  },

  update: (id: string, data: UpdateDocumentRequest): Promise<UpdateDocumentResponse> =>
    request(`/api/documents/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
};

// ===== Credentials API =====
export const credentialsApi = {
  list: (): Promise<Credential[]> => request('/api/credentials'),

  get: (id: string): Promise<Credential> => request(`/api/credentials/${id}`),

  create: (data: CreateCredentialRequest): Promise<Credential> =>
    request('/api/credentials', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  update: (id: string, data: UpdateCredentialRequest): Promise<Credential> =>
    request(`/api/credentials/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  delete: (id: string): Promise<void> =>
    request(`/api/credentials/${id}`, {
      method: 'DELETE',
    }),
};

// ===== Commits API =====
export const commitsApi = {
  // 레포지토리의 커밋 목록을 조회 (페이지네이션 지원)
  list: (repositoryId: string, params?: CommitListParams): Promise<Commit[]> => {
    const query = new URLSearchParams();
    if (params?.skip !== undefined) query.set('skip', String(params.skip));
    if (params?.limit !== undefined) query.set('limit', String(params.limit));
    const queryString = query.toString();
    return request(`/api/repositories/${repositoryId}/commits${queryString ? `?${queryString}` : ''}`);
  },

  // 특정 커밋의 상세 정보 조회 (변경된 파일 목록 포함)
  get: (repositoryId: string, sha: string): Promise<CommitDetail> =>
    request(`/api/repositories/${repositoryId}/commits/${sha}`),

  // 두 커밋 간 변경된 파일 목록 조회
  getDiff: (repositoryId: string, params: CommitDiffParams): Promise<CommitDetail> => {
    const query = new URLSearchParams({ from: params.from, to: params.to });
    return request(`/api/repositories/${repositoryId}/commits/diff?${query.toString()}`);
  },
};

// ===== Stats API =====
export const statsApi = {
  get: (): Promise<StatsResponse> => request('/api/stats'),
};

// ===== RAG Config API =====
export const ragConfigApi = {
  getConfig: (projectId: string): Promise<RagConfigResponse> =>
    request(`/api/projects/${projectId}/rag-config`),

  updateConfig: (projectId: string, data: UpdateRagConfigRequest): Promise<RagConfigResponse> =>
    request(`/api/projects/${projectId}/rag-config`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  validateConfig: (projectId: string, data: UpdateRagConfigRequest): Promise<RagConfigValidationResponse> =>
    request(`/api/projects/${projectId}/rag-config/validate`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  getDefaults: (projectId: string): Promise<RagDefaults> =>
    request(`/api/projects/${projectId}/rag-config/defaults`),

  triggerReEmbed: (projectId: string): Promise<ReEmbeddingTriggerResponse> =>
    request(`/api/projects/${projectId}/rag-config/re-embed`, {
      method: 'POST',
    }),

  getReEmbedStatus: (projectId: string): Promise<ReEmbeddingStatusResponse> =>
    request(`/api/projects/${projectId}/rag-config/re-embed/status`),
};

export { ApiError };
