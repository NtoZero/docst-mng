'use client';

import { FileText, MapPin, ExternalLink } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import type { Citation } from '@/lib/types';

interface CitationCardProps {
  citation: Citation;
  index: number;
}

export function CitationCard({ citation, index }: CitationCardProps) {
  const scorePercent = Math.round(citation.score * 100);

  const handleClick = () => {
    // 새 탭에서 문서 페이지 열기
    window.open(`/documents/${citation.documentId}`, '_blank');
  };

  return (
    <Card
      className="hover:shadow-md transition-shadow cursor-pointer group"
      onClick={handleClick}
    >
      <CardContent className="p-3">
        {/* 인덱스 + 문서 경로 */}
        <div className="flex items-center gap-2 text-sm text-muted-foreground mb-1.5">
          <span className="font-medium text-foreground">[{index}]</span>
          <FileText className="w-3.5 h-3.5" />
          <span className="truncate flex-1">{citation.path}</span>
          <ExternalLink className="w-3.5 h-3.5 opacity-0 group-hover:opacity-100 transition-opacity" />
        </div>

        {/* 헤딩 경로 */}
        {citation.headingPath && (
          <div className="flex items-center gap-2 text-xs text-blue-600 mb-1.5">
            <MapPin className="w-3 h-3" />
            <span className="truncate">{citation.headingPath}</span>
          </div>
        )}

        {/* 스니펫 */}
        <p className="text-xs text-muted-foreground line-clamp-2 mb-2">
          {citation.snippet}
        </p>

        {/* 점수 배지 */}
        <Badge
          variant={scorePercent >= 80 ? 'default' : 'secondary'}
          className="text-xs"
        >
          {scorePercent}%
        </Badge>
      </CardContent>
    </Card>
  );
}
