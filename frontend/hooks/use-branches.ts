'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8342';

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

async function apiFetch<T>(path: string, options?: RequestInit): Promise<T> {
  const token = await getAuthToken();
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
    ...(options?.headers || {}),
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
    throw new Error(message || response.statusText);
  }

  return response.json();
}

interface BranchResult {
  branchName: string;
  ref: string | null;
  success: boolean;
}

interface CreateBranchRequest {
  branchName: string;
  fromBranch?: string;
}

/**
 * 브랜치 관리 Hook
 *
 * 레포지토리의 브랜치 목록 조회, 생성, 전환 기능 제공
 */
export function useBranches(repositoryId: string) {
  const queryClient = useQueryClient();

  // 브랜치 목록 조회
  const branchesQuery = useQuery({
    queryKey: ['branches', repositoryId],
    queryFn: () => apiFetch<string[]>(`/api/repositories/${repositoryId}/branches`),
    enabled: !!repositoryId,
  });

  // 현재 브랜치 조회
  const currentBranchQuery = useQuery({
    queryKey: ['current-branch', repositoryId],
    queryFn: () =>
      apiFetch<{ branchName: string }>(`/api/repositories/${repositoryId}/branches/current`)
        .then(res => res.branchName),
    enabled: !!repositoryId,
  });

  // 브랜치 생성
  const createBranchMutation = useMutation({
    mutationFn: async (request: CreateBranchRequest) => {
      return apiFetch<BranchResult>(`/api/repositories/${repositoryId}/branches`, {
        method: 'POST',
        body: JSON.stringify(request),
      });
    },
    onSuccess: () => {
      // 브랜치 목록 다시 조회
      queryClient.invalidateQueries({ queryKey: ['branches', repositoryId] });
    },
  });

  // 브랜치 전환
  const switchBranchMutation = useMutation({
    mutationFn: async (branchName: string) => {
      return apiFetch<BranchResult>(
        `/api/repositories/${repositoryId}/branches/${branchName}/switch`,
        { method: 'POST', body: JSON.stringify({}) }
      );
    },
    onSuccess: () => {
      // 현재 브랜치 정보 다시 조회
      queryClient.invalidateQueries({ queryKey: ['current-branch', repositoryId] });
      // 문서 목록도 다시 조회 (브랜치가 바뀌면 문서 목록도 변경될 수 있음)
      queryClient.invalidateQueries({ queryKey: ['documents', repositoryId] });
    },
  });

  return {
    // 브랜치 목록
    branches: branchesQuery.data,
    isLoadingBranches: branchesQuery.isLoading,
    branchesError: branchesQuery.error,

    // 현재 브랜치
    currentBranch: currentBranchQuery.data,
    isLoadingCurrentBranch: currentBranchQuery.isLoading,

    // 브랜치 생성
    createBranch: (branchName: string, fromBranch?: string) =>
      createBranchMutation.mutateAsync({ branchName, fromBranch }),
    isCreatingBranch: createBranchMutation.isPending,

    // 브랜치 전환
    switchBranch: (branchName: string) =>
      switchBranchMutation.mutateAsync(branchName),
    isSwitchingBranch: switchBranchMutation.isPending,
  };
}
