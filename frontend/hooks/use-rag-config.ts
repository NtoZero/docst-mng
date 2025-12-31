import { useQuery, useMutation, useQueryClient, type UseQueryOptions } from '@tanstack/react-query';
import { ragConfigApi } from '@/lib/api';
import type { UpdateRagConfigRequest } from '@/lib/types';

export const ragConfigKeys = {
  all: ['rag-config'] as const,
  config: (projectId: string) => [...ragConfigKeys.all, projectId] as const,
  defaults: (projectId: string) => [...ragConfigKeys.all, projectId, 'defaults'] as const,
  reEmbedStatus: (projectId: string) => [...ragConfigKeys.all, projectId, 're-embed-status'] as const,
};

export function useRagConfig(projectId: string) {
  return useQuery({
    queryKey: ragConfigKeys.config(projectId),
    queryFn: () => ragConfigApi.getConfig(projectId),
    enabled: !!projectId,
  });
}

export function useRagDefaults(projectId: string) {
  return useQuery({
    queryKey: ragConfigKeys.defaults(projectId),
    queryFn: () => ragConfigApi.getDefaults(projectId),
    enabled: !!projectId,
  });
}

export function useUpdateRagConfig(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: UpdateRagConfigRequest) => ragConfigApi.updateConfig(projectId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ragConfigKeys.config(projectId) });
    },
  });
}

export function useValidateRagConfig(projectId: string) {
  return useMutation({
    mutationFn: (data: UpdateRagConfigRequest) => ragConfigApi.validateConfig(projectId, data),
  });
}

export function useTriggerReEmbed(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => ragConfigApi.triggerReEmbed(projectId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ragConfigKeys.reEmbedStatus(projectId) });
    },
  });
}

export function useReEmbedStatus(projectId: string, options?: { enabled?: boolean; refetchInterval?: number | false }) {
  return useQuery({
    queryKey: ragConfigKeys.reEmbedStatus(projectId),
    queryFn: () => ragConfigApi.getReEmbedStatus(projectId),
    enabled: options?.enabled ?? false,
    refetchInterval: options?.refetchInterval,
  });
}
