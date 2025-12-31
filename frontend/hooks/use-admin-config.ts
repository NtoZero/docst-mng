import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '@/lib/store';
import * as adminApi from '@/lib/admin-api';
import type {
  SystemConfig,
  UpdateSystemConfigRequest,
  SystemCredential,
  CreateSystemCredentialRequest,
  UpdateSystemCredentialRequest,
  ProjectCredential,
  CreateProjectCredentialRequest,
  UpdateProjectCredentialRequest,
  HealthCheckResponse,
} from '@/lib/types';

// ===== System Config Hooks =====

export function useSystemConfigs() {
  const token = useAuthStore((state) => state.token);

  return useQuery({
    queryKey: ['admin', 'configs'],
    queryFn: () => adminApi.getAllSystemConfigs(token!),
    enabled: !!token,
  });
}

export function useSystemConfig(key: string) {
  const token = useAuthStore((state) => state.token);

  return useQuery({
    queryKey: ['admin', 'config', key],
    queryFn: () => adminApi.getSystemConfig(token!, key),
    enabled: !!token && !!key,
  });
}

export function useUpdateSystemConfig() {
  const token = useAuthStore((state) => state.token);
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      key,
      request,
    }: {
      key: string;
      request: UpdateSystemConfigRequest;
    }) => adminApi.updateSystemConfig(token!, key, request),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'configs'] });
      queryClient.invalidateQueries({ queryKey: ['admin', 'config', data.configKey] });
    },
  });
}

export function useRefreshConfigCache() {
  const token = useAuthStore((state) => state.token);
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => adminApi.refreshSystemConfigCache(token!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'configs'] });
    },
  });
}

// ===== System Credential Hooks =====

export function useSystemCredentials() {
  const token = useAuthStore((state) => state.token);

  return useQuery({
    queryKey: ['admin', 'credentials'],
    queryFn: () => adminApi.listSystemCredentials(token!),
    enabled: !!token,
  });
}

export function useSystemCredential(id: string) {
  const token = useAuthStore((state) => state.token);

  return useQuery({
    queryKey: ['admin', 'credential', id],
    queryFn: () => adminApi.getSystemCredential(token!, id),
    enabled: !!token && !!id,
  });
}

export function useCreateSystemCredential() {
  const token = useAuthStore((state) => state.token);
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: CreateSystemCredentialRequest) =>
      adminApi.createSystemCredential(token!, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'credentials'] });
    },
  });
}

export function useUpdateSystemCredential() {
  const token = useAuthStore((state) => state.token);
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      id,
      request,
    }: {
      id: string;
      request: UpdateSystemCredentialRequest;
    }) => adminApi.updateSystemCredential(token!, id, request),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'credentials'] });
      queryClient.invalidateQueries({ queryKey: ['admin', 'credential', data.id] });
    },
  });
}

export function useDeleteSystemCredential() {
  const token = useAuthStore((state) => state.token);
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => adminApi.deleteSystemCredential(token!, id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'credentials'] });
    },
  });
}

// ===== Project Credential Hooks =====

export function useProjectCredentials(projectId: string) {
  const token = useAuthStore((state) => state.token);

  return useQuery({
    queryKey: ['projects', projectId, 'credentials'],
    queryFn: () => adminApi.listProjectCredentials(token!, projectId),
    enabled: !!token && !!projectId,
  });
}

export function useProjectCredential(projectId: string, credentialId: string) {
  const token = useAuthStore((state) => state.token);

  return useQuery({
    queryKey: ['projects', projectId, 'credential', credentialId],
    queryFn: () => adminApi.getProjectCredential(token!, projectId, credentialId),
    enabled: !!token && !!projectId && !!credentialId,
  });
}

export function useCreateProjectCredential() {
  const token = useAuthStore((state) => state.token);
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      projectId,
      request,
    }: {
      projectId: string;
      request: CreateProjectCredentialRequest;
    }) => adminApi.createProjectCredential(token!, projectId, request),
    onSuccess: (data) => {
      queryClient.invalidateQueries({
        queryKey: ['projects', data.projectId, 'credentials'],
      });
    },
  });
}

export function useUpdateProjectCredential() {
  const token = useAuthStore((state) => state.token);
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      projectId,
      credentialId,
      request,
    }: {
      projectId: string;
      credentialId: string;
      request: UpdateProjectCredentialRequest;
    }) =>
      adminApi.updateProjectCredential(token!, projectId, credentialId, request),
    onSuccess: (data) => {
      queryClient.invalidateQueries({
        queryKey: ['projects', data.projectId, 'credentials'],
      });
      queryClient.invalidateQueries({
        queryKey: ['projects', data.projectId, 'credential', data.id],
      });
    },
  });
}

export function useDeleteProjectCredential() {
  const token = useAuthStore((state) => state.token);
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      projectId,
      credentialId,
    }: {
      projectId: string;
      credentialId: string;
    }) => adminApi.deleteProjectCredential(token!, projectId, credentialId),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({
        queryKey: ['projects', variables.projectId, 'credentials'],
      });
    },
  });
}

// ===== Health Check Hooks =====

export function useHealthCheck() {
  const token = useAuthStore((state) => state.token);

  return useQuery({
    queryKey: ['admin', 'health'],
    queryFn: () => adminApi.getHealthCheck(token!),
    enabled: !!token,
    refetchInterval: 30000, // Auto-refresh every 30 seconds
  });
}
