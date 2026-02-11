'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { glossaryApi } from '@/lib/api';
import type {
  CreateGlossaryTermRequest,
  UpdateGlossaryTermRequest,
} from '@/lib/types';

// ===== Query Keys =====
export const glossaryQueryKeys = {
  all: (projectId: string) => ['glossary', projectId] as const,
  detail: (projectId: string, termId: string) => ['glossary', projectId, termId] as const,
  categories: (projectId: string) => ['glossary', projectId, 'categories'] as const,
  search: (projectId: string, query: string, mode: string) =>
    ['glossary', projectId, 'search', query, mode] as const,
};

// ===== Glossary Hooks =====

/**
 * 프로젝트의 용어 목록을 조회한다.
 */
export function useGlossaryTerms(projectId: string, category?: string) {
  return useQuery({
    queryKey: [...glossaryQueryKeys.all(projectId), category],
    queryFn: () => glossaryApi.list(projectId, category),
    enabled: !!projectId,
  });
}

/**
 * 용어 상세 정보를 조회한다.
 */
export function useGlossaryTerm(projectId: string, termId: string) {
  return useQuery({
    queryKey: glossaryQueryKeys.detail(projectId, termId),
    queryFn: () => glossaryApi.get(projectId, termId),
    enabled: !!projectId && !!termId,
  });
}

/**
 * 프로젝트의 카테고리 목록을 조회한다.
 */
export function useGlossaryCategories(projectId: string) {
  return useQuery({
    queryKey: glossaryQueryKeys.categories(projectId),
    queryFn: () => glossaryApi.getCategories(projectId),
    enabled: !!projectId,
  });
}

/**
 * 용어를 검색한다.
 */
export function useGlossarySearch(
  projectId: string,
  query: string,
  mode: 'keyword' | 'semantic' = 'keyword',
  topK: number = 10,
  enabled = true
) {
  return useQuery({
    queryKey: glossaryQueryKeys.search(projectId, query, mode),
    queryFn: () => glossaryApi.search(projectId, query, mode, topK),
    enabled: enabled && !!projectId && !!query,
  });
}

/**
 * 새 용어를 생성한다.
 */
export function useCreateGlossaryTerm() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ projectId, data }: { projectId: string; data: CreateGlossaryTermRequest }) =>
      glossaryApi.create(projectId, data),
    onSuccess: (_, { projectId }) => {
      queryClient.invalidateQueries({ queryKey: glossaryQueryKeys.all(projectId) });
      queryClient.invalidateQueries({ queryKey: glossaryQueryKeys.categories(projectId) });
    },
  });
}

/**
 * 용어를 수정한다.
 */
export function useUpdateGlossaryTerm() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      projectId,
      termId,
      data,
    }: {
      projectId: string;
      termId: string;
      data: UpdateGlossaryTermRequest;
    }) => glossaryApi.update(projectId, termId, data),
    onSuccess: (_, { projectId, termId }) => {
      queryClient.invalidateQueries({ queryKey: glossaryQueryKeys.all(projectId) });
      queryClient.invalidateQueries({ queryKey: glossaryQueryKeys.detail(projectId, termId) });
      queryClient.invalidateQueries({ queryKey: glossaryQueryKeys.categories(projectId) });
    },
  });
}

/**
 * 용어를 삭제한다.
 */
export function useDeleteGlossaryTerm() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ projectId, termId }: { projectId: string; termId: string }) =>
      glossaryApi.delete(projectId, termId),
    onSuccess: (_, { projectId }) => {
      queryClient.invalidateQueries({ queryKey: glossaryQueryKeys.all(projectId) });
      queryClient.invalidateQueries({ queryKey: glossaryQueryKeys.categories(projectId) });
    },
  });
}
