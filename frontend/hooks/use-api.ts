'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { authApi, apiKeysApi, projectsApi, repositoriesApi, documentsApi, credentialsApi, commitsApi, setupApi, statsApi } from '@/lib/api';
import type {
  CreateProjectRequest,
  UpdateProjectRequest,
  CreateRepositoryRequest,
  UpdateRepositoryRequest,
  SearchRequest,
  SyncRequest,
  LoginRequest,
  RegisterRequest,
  ChangePasswordRequest,
  CreateApiKeyRequest,
  InitializeRequest,
  CreateCredentialRequest,
  UpdateCredentialRequest,
  SetCredentialRequest,
  CommitListParams,
  CommitDiffParams,
  UpdateDocumentRequest,
} from '@/lib/types';
import { useAuthStore } from '@/lib/store';

// ===== Query Keys =====
export const queryKeys = {
  auth: {
    me: ['auth', 'me'] as const,
  },
  apiKeys: {
    all: ['api-keys'] as const,
  },
  projects: {
    all: ['projects'] as const,
    detail: (id: string) => ['projects', id] as const,
    search: (id: string, query: string) => ['projects', id, 'search', query] as const,
  },
  repositories: {
    byProject: (projectId: string) => ['repositories', 'project', projectId] as const,
    detail: (id: string) => ['repositories', id] as const,
    syncStatus: (id: string) => ['repositories', id, 'sync'] as const,
  },
  documents: {
    byRepository: (repositoryId: string) => ['documents', 'repository', repositoryId] as const,
    detail: (id: string) => ['documents', id] as const,
    versions: (id: string) => ['documents', id, 'versions'] as const,
    version: (id: string, commitSha: string) => ['documents', id, 'versions', commitSha] as const,
    diff: (id: string, from: string, to: string) => ['documents', id, 'diff', from, to] as const,
  },
  credentials: {
    all: ['credentials'] as const,
    detail: (id: string) => ['credentials', id] as const,
  },
  commits: {
    byRepository: (repositoryId: string, params?: CommitListParams) =>
      ['commits', 'repository', repositoryId, params] as const,
    detail: (repositoryId: string, sha: string) =>
      ['commits', 'repository', repositoryId, sha] as const,
    diff: (repositoryId: string, from: string, to: string) =>
      ['commits', 'repository', repositoryId, 'diff', from, to] as const,
  },
  stats: {
    all: ['stats'] as const,
  },
};

// ===== Auth Hooks =====
export function useLogin() {
  const setAuth = useAuthStore((state) => state.setAuth);

  return useMutation({
    mutationFn: async (data: LoginRequest) => {
      const tokenResponse = await authApi.localLogin(data);
      // Temporarily store token to fetch user
      localStorage.setItem(
        'docst-auth',
        JSON.stringify({ state: { token: tokenResponse.accessToken } })
      );
      const user = await authApi.me();
      return { token: tokenResponse.accessToken, user };
    },
    onSuccess: ({ token, user }) => {
      setAuth(
        {
          id: user.id,
          email: user.email,
          displayName: user.displayName,
        },
        token
      );
    },
  });
}

export function useRegister() {
  const setAuth = useAuthStore((state) => state.setAuth);

  return useMutation({
    mutationFn: async (data: RegisterRequest) => {
      const tokenResponse = await authApi.register(data);
      // Temporarily store token to fetch user
      localStorage.setItem(
        'docst-auth',
        JSON.stringify({ state: { token: tokenResponse.accessToken } })
      );
      const user = await authApi.me();
      return { token: tokenResponse.accessToken, user };
    },
    onSuccess: ({ token, user }) => {
      setAuth(
        {
          id: user.id,
          email: user.email,
          displayName: user.displayName,
        },
        token
      );
    },
  });
}

export function useChangePassword() {
  return useMutation({
    mutationFn: (data: ChangePasswordRequest) => authApi.changePassword(data),
  });
}

export function useSetupStatus() {
  return useQuery({
    queryKey: ['setup', 'status'],
    queryFn: () => setupApi.getStatus(),
  });
}

export function useInitialize() {
  return useMutation({
    mutationFn: (data: InitializeRequest) => setupApi.initialize(data),
  });
}

export function useLogout() {
  const clearAuth = useAuthStore((state) => state.clearAuth);
  const queryClient = useQueryClient();

  return () => {
    clearAuth();
    queryClient.clear();
  };
}

export function useCurrentUser() {
  const token = useAuthStore((state) => state.token);

  return useQuery({
    queryKey: queryKeys.auth.me,
    queryFn: () => authApi.me(),
    enabled: !!token,
    staleTime: 5 * 60 * 1000,
  });
}

// ===== Projects Hooks =====
export function useProjects() {
  return useQuery({
    queryKey: queryKeys.projects.all,
    queryFn: () => projectsApi.list(),
  });
}

export function useProject(id: string) {
  return useQuery({
    queryKey: queryKeys.projects.detail(id),
    queryFn: () => projectsApi.get(id),
    enabled: !!id,
  });
}

export function useCreateProject() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreateProjectRequest) => projectsApi.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.projects.all });
    },
  });
}

export function useUpdateProject() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateProjectRequest }) =>
      projectsApi.update(id, data),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.projects.all });
      queryClient.invalidateQueries({ queryKey: queryKeys.projects.detail(id) });
    },
  });
}

export function useDeleteProject() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => projectsApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.projects.all });
    },
  });
}

export function useSearch(projectId: string, params: SearchRequest, enabled = true) {
  return useQuery({
    queryKey: queryKeys.projects.search(projectId, params.q),
    queryFn: () => projectsApi.search(projectId, params),
    enabled: enabled && !!projectId && !!params.q,
  });
}

// ===== Repositories Hooks =====
export function useRepositories(projectId: string) {
  return useQuery({
    queryKey: queryKeys.repositories.byProject(projectId),
    queryFn: () => repositoriesApi.listByProject(projectId),
    enabled: !!projectId,
  });
}

export function useRepository(id: string) {
  return useQuery({
    queryKey: queryKeys.repositories.detail(id),
    queryFn: () => repositoriesApi.get(id),
    enabled: !!id,
  });
}

export function useCreateRepository() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ projectId, data }: { projectId: string; data: CreateRepositoryRequest }) =>
      repositoriesApi.create(projectId, data),
    onSuccess: (_, { projectId }) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.repositories.byProject(projectId) });
    },
  });
}

export function useUpdateRepository() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateRepositoryRequest }) =>
      repositoriesApi.update(id, data),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.repositories.detail(result.id) });
      queryClient.invalidateQueries({
        queryKey: queryKeys.repositories.byProject(result.projectId),
      });
    },
  });
}

export function useDeleteRepository() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => repositoriesApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['repositories'] });
    },
  });
}

export function useSyncRepository() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data?: SyncRequest }) =>
      repositoriesApi.sync(id, data),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.repositories.syncStatus(id) });
    },
  });
}

export function useSyncStatus(repositoryId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.repositories.syncStatus(repositoryId),
    queryFn: () => repositoriesApi.getSyncStatus(repositoryId),
    enabled: enabled && !!repositoryId,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      if (status === 'RUNNING' || status === 'PENDING') {
        return 2000;
      }
      return false;
    },
  });
}

export function usePushRepository() {
  return useMutation({
    mutationFn: ({ id, branch }: { id: string; branch?: string }) =>
      repositoriesApi.push(id, branch),
  });
}

// ===== Documents Hooks =====
export function useDocuments(repositoryId: string) {
  return useQuery({
    queryKey: queryKeys.documents.byRepository(repositoryId),
    queryFn: () => documentsApi.listByRepository(repositoryId),
    enabled: !!repositoryId,
  });
}

export function useDocument(id: string) {
  return useQuery({
    queryKey: queryKeys.documents.detail(id),
    queryFn: () => documentsApi.get(id),
    enabled: !!id,
  });
}

export function useDocumentVersions(documentId: string) {
  return useQuery({
    queryKey: queryKeys.documents.versions(documentId),
    queryFn: () => documentsApi.getVersions(documentId),
    enabled: !!documentId,
  });
}

export function useDocumentVersion(documentId: string, commitSha: string) {
  return useQuery({
    queryKey: queryKeys.documents.version(documentId, commitSha),
    queryFn: () => documentsApi.getVersion(documentId, commitSha),
    enabled: !!documentId && !!commitSha,
  });
}

export function useDocumentDiff(documentId: string, from: string, to: string) {
  return useQuery({
    queryKey: queryKeys.documents.diff(documentId, from, to),
    queryFn: () => documentsApi.getDiff(documentId, from, to),
    enabled: !!documentId && !!from && !!to,
  });
}

export function useUpdateDocument() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateDocumentRequest }) =>
      documentsApi.update(id, data),
    onSuccess: (result) => {
      // Invalidate document detail and versions
      queryClient.invalidateQueries({ queryKey: queryKeys.documents.detail(result.documentId) });
      queryClient.invalidateQueries({ queryKey: queryKeys.documents.versions(result.documentId) });
    },
  });
}

// ===== Credentials Hooks =====
export function useCredentials() {
  return useQuery({
    queryKey: queryKeys.credentials.all,
    queryFn: () => credentialsApi.list(),
  });
}

export function useCredential(id: string) {
  return useQuery({
    queryKey: queryKeys.credentials.detail(id),
    queryFn: () => credentialsApi.get(id),
    enabled: !!id,
  });
}

export function useCreateCredential() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreateCredentialRequest) => credentialsApi.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.credentials.all });
    },
  });
}

export function useUpdateCredential() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateCredentialRequest }) =>
      credentialsApi.update(id, data),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.credentials.all });
      queryClient.invalidateQueries({ queryKey: queryKeys.credentials.detail(id) });
    },
  });
}

export function useDeleteCredential() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => credentialsApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.credentials.all });
    },
  });
}

export function useSetRepositoryCredential() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ repoId, data }: { repoId: string; data: SetCredentialRequest }) =>
      repositoriesApi.setCredential(repoId, data),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.repositories.detail(result.id) });
      queryClient.invalidateQueries({
        queryKey: queryKeys.repositories.byProject(result.projectId),
      });
    },
  });
}

export function useMoveRepository() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, targetProjectId }: { id: string; targetProjectId: string }) =>
      repositoriesApi.move(id, targetProjectId),
    onSuccess: () => {
      // Invalidate all repository queries since the repo moved between projects
      queryClient.invalidateQueries({ queryKey: ['repositories'] });
      queryClient.invalidateQueries({ queryKey: queryKeys.projects.all });
    },
  });
}

// ===== Commits Hooks =====
// 레포지토리의 커밋 목록을 조회 (페이지네이션 지원)
export function useCommits(repositoryId: string, params?: CommitListParams, enabled = true) {
  return useQuery({
    queryKey: queryKeys.commits.byRepository(repositoryId, params),
    queryFn: () => commitsApi.list(repositoryId, params),
    enabled: enabled && !!repositoryId,
  });
}

// 특정 커밋의 상세 정보 조회 (변경된 파일 목록 포함)
export function useCommitDetail(repositoryId: string, sha: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.commits.detail(repositoryId, sha),
    queryFn: () => commitsApi.get(repositoryId, sha),
    enabled: enabled && !!repositoryId && !!sha,
  });
}

// 두 커밋 간의 차이 조회
export function useCommitDiff(repositoryId: string, params: CommitDiffParams, enabled = true) {
  return useQuery({
    queryKey: queryKeys.commits.diff(repositoryId, params.from, params.to),
    queryFn: () => commitsApi.getDiff(repositoryId, params),
    enabled: enabled && !!repositoryId && !!params.from && !!params.to,
  });
}

// ===== Stats Hooks =====
export function useStats() {
  return useQuery({
    queryKey: queryKeys.stats.all,
    queryFn: () => statsApi.get(),
  });
}

// ===== API Keys Hooks =====
export function useApiKeys() {
  return useQuery({
    queryKey: queryKeys.apiKeys.all,
    queryFn: () => apiKeysApi.list(),
  });
}

export function useCreateApiKey() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreateApiKeyRequest) => apiKeysApi.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.apiKeys.all });
    },
  });
}

export function useRevokeApiKey() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => apiKeysApi.revoke(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.apiKeys.all });
    },
  });
}

export function useUpdateApiKeyDefaultProject() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, projectId }: { id: string; projectId: string | null }) =>
      apiKeysApi.updateDefaultProject(id, projectId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.apiKeys.all });
    },
  });
}
