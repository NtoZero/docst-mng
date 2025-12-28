'use client';

import { FileText, Hash, MapPin } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import Link from 'next/link';
import type { SearchResult } from '@/lib/types';

interface SearchResultCardProps {
  result: SearchResult;
}

export function SearchResultCard({ result }: SearchResultCardProps) {
  const scorePercent = Math.round(result.score * 100);

  return (
    <Link href={`/documents/${result.documentId}`}>
      <Card className="hover:shadow-md transition-shadow cursor-pointer">
        <CardContent className="pt-4">
          {/* 문서 경로 */}
          <div className="flex items-center gap-2 text-sm text-muted-foreground mb-2">
            <FileText className="w-4 h-4" />
            <span>{result.path}</span>
          </div>

          {/* 헤딩 경로 (의미 검색 시) */}
          {result.headingPath && (
            <div className="flex items-center gap-2 text-sm text-blue-600 mb-2">
              <MapPin className="w-4 h-4" />
              <span>{result.headingPath}</span>
            </div>
          )}

          {/* 스니펫 */}
          <div className="text-sm mb-3 line-clamp-3">
            {result.highlights && result.highlights.length > 0 ? (
              <span
                dangerouslySetInnerHTML={{
                  __html: result.highlights.join('...')
                }}
              />
            ) : result.highlightedSnippet ? (
              <span
                dangerouslySetInnerHTML={{
                  __html: result.highlightedSnippet
                }}
              />
            ) : (
              <span>{result.snippet}</span>
            )}
          </div>

          {/* 점수 및 청크 ID */}
          <div className="flex items-center justify-between">
            <Badge variant={scorePercent >= 80 ? 'default' : 'secondary'}>
              {scorePercent}% match
            </Badge>
            {result.chunkId && (
              <span className="text-xs text-muted-foreground flex items-center gap-1">
                <Hash className="w-3 h-3" />
                {result.chunkId.slice(0, 8)}
              </span>
            )}
          </div>
        </CardContent>
      </Card>
    </Link>
  );
}
