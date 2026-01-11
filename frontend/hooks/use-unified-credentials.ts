import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { unifiedCredentialsApi } from '@/lib/unified-credentials-api';
import type {
  CredentialScope,
  UnifiedCredential,
  CreateUnifiedCredentialRequest,
  UpdateUnifiedCredentialRequest,
} from '@/lib/types';

/**
 * Query key factory for unified credentials
 */
export const credentialKeys = {
  all: ['unified-credentials'] as const,
  lists: () => [...credentialKeys.all, 'list'] as const,
  list: (scope: CredentialScope, projectId?: string) =>
    [...credentialKeys.lists(), scope, projectId] as const,
};

/**
 * Hook to fetch credentials for a specific scope
 */
export function useUnifiedCredentials(
  scope: CredentialScope,
  projectId?: string
) {
  return useQuery<UnifiedCredential[], Error>({
    queryKey: credentialKeys.list(scope, projectId),
    queryFn: () => unifiedCredentialsApi.list(scope, projectId),
    // Only enable for PROJECT scope when projectId is provided
    enabled: scope !== 'PROJECT' || !!projectId,
  });
}

/**
 * Hook to create a new credential
 */
export function useCreateUnifiedCredential() {
  const queryClient = useQueryClient();

  return useMutation<
    UnifiedCredential,
    Error,
    {
      scope: CredentialScope;
      request: CreateUnifiedCredentialRequest;
      projectId?: string;
    }
  >({
    mutationFn: ({ scope, request, projectId }) =>
      unifiedCredentialsApi.create(scope, request, projectId),
    onSuccess: (_, { scope, projectId }) => {
      // Invalidate the list query for this scope
      queryClient.invalidateQueries({
        queryKey: credentialKeys.list(scope, projectId),
      });
    },
  });
}

/**
 * Hook to update an existing credential
 */
export function useUpdateUnifiedCredential() {
  const queryClient = useQueryClient();

  return useMutation<
    UnifiedCredential,
    Error,
    {
      scope: CredentialScope;
      id: string;
      request: UpdateUnifiedCredentialRequest;
      projectId?: string;
    }
  >({
    mutationFn: ({ scope, id, request, projectId }) =>
      unifiedCredentialsApi.update(scope, id, request, projectId),
    onSuccess: (_, { scope, projectId }) => {
      // Invalidate the list query for this scope
      queryClient.invalidateQueries({
        queryKey: credentialKeys.list(scope, projectId),
      });
    },
  });
}

/**
 * Hook to delete a credential
 */
export function useDeleteUnifiedCredential() {
  const queryClient = useQueryClient();

  return useMutation<
    void,
    Error,
    {
      scope: CredentialScope;
      id: string;
      projectId?: string;
    }
  >({
    mutationFn: ({ scope, id, projectId }) =>
      unifiedCredentialsApi.delete(scope, id, projectId),
    onSuccess: (_, { scope, projectId }) => {
      // Invalidate the list query for this scope
      queryClient.invalidateQueries({
        queryKey: credentialKeys.list(scope, projectId),
      });
    },
  });
}
