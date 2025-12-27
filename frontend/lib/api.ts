import type {
  AuthTokenResponse,
  User,
  LoginRequest,
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
} from './types';

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

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = await getAuthToken();

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
    const message = await response.text();
    throw new ApiError(response.status, message || response.statusText);
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

  me: (): Promise<User> => request('/api/auth/me'),
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

export { ApiError };
