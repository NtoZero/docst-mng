'use client';

import { BookOpen } from 'lucide-react';
import { CitationCard } from './citation-card';
import type { Citation } from '@/lib/types';

interface CitationsSectionProps {
  citations: Citation[];
}

/**
 * RAG Citation 섹션 컴포넌트
 *
 * 문서 출처를 카드 형태로 표시합니다.
 * - documentId 기준 중복 제거 (높은 score 유지)
 * - score 내림차순 정렬
 */
export function CitationsSection({ citations }: CitationsSectionProps) {
  if (!citations || citations.length === 0) {
    return null;
  }

  // documentId 기준 중복 제거 (높은 score 유지)
  const uniqueCitations = citations.reduce<Citation[]>((acc, citation) => {
    const existing = acc.find((c) => c.documentId === citation.documentId);
    if (existing) {
      // 기존 것보다 score가 높으면 교체
      if (citation.score > existing.score) {
        return acc.map((c) =>
          c.documentId === citation.documentId ? citation : c
        );
      }
      return acc;
    }
    return [...acc, citation];
  }, []);

  // score 내림차순 정렬
  const sortedCitations = uniqueCitations.sort((a, b) => b.score - a.score);

  return (
    <div className="mt-4 pt-3 border-t">
      {/* 헤더 */}
      <div className="flex items-center gap-2 text-sm font-medium text-muted-foreground mb-3">
        <BookOpen className="w-4 h-4" />
        <span>Sources ({sortedCitations.length})</span>
      </div>

      {/* Citation 카드 그리드 */}
      <div className="grid gap-2">
        {sortedCitations.map((citation, index) => (
          <CitationCard
            key={`${citation.documentId}-${citation.chunkId || index}`}
            citation={citation}
            index={index + 1}
          />
        ))}
      </div>
    </div>
  );
}
