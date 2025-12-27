'use client';

import { useState } from 'react';
import { GitCommit, FileText, FilePlus, FileMinus, FileEdit, Loader2 } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { useCommits, useCommitDetail } from '@/hooks/use-api';
import type { ChangeType } from '@/lib/types';

interface CommitHistoryDialogProps {
  repositoryId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSelectCommit?: (sha: string) => void; // Sync Mode에서 커밋 선택 시 콜백
}

// 변경 타입에 따른 아이콘 반환
function getChangeIcon(changeType: ChangeType) {
  switch (changeType) {
    case 'ADDED':
      return <FilePlus className="h-4 w-4 text-green-600" />;
    case 'DELETED':
      return <FileMinus className="h-4 w-4 text-red-600" />;
    case 'MODIFIED':
      return <FileEdit className="h-4 w-4 text-blue-600" />;
    case 'RENAMED':
      return <FileText className="h-4 w-4 text-yellow-600" />;
    default:
      return <FileText className="h-4 w-4" />;
  }
}

// 변경 타입에 따른 배지 스타일
function getChangeBadge(changeType: ChangeType) {
  const variants = {
    ADDED: 'success',
    DELETED: 'destructive',
    MODIFIED: 'default',
    RENAMED: 'warning',
  } as const;

  return <Badge variant={variants[changeType] || 'secondary'}>{changeType}</Badge>;
}

export function CommitHistoryDialog({
  repositoryId,
  open,
  onOpenChange,
  onSelectCommit,
}: CommitHistoryDialogProps) {
  const [selectedSha, setSelectedSha] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const limit = 20;

  // 커밋 목록 조회 (페이지네이션)
  const { data: commits, isLoading: commitsLoading } = useCommits(
    repositoryId,
    { skip: page * limit, limit },
    open
  );

  // 선택한 커밋 상세 조회
  const { data: commitDetail, isLoading: detailLoading } = useCommitDetail(
    repositoryId,
    selectedSha || '',
    !!selectedSha
  );

  const handleCommitClick = (sha: string) => {
    setSelectedSha(sha === selectedSha ? null : sha);
  };

  const handleSelectCommit = (sha: string) => {
    if (onSelectCommit) {
      onSelectCommit(sha);
      onOpenChange(false);
    }
  };

  // 날짜 포맷팅
  const formatDate = (dateStr: string) => {
    const date = new Date(dateStr);
    return new Intl.DateTimeFormat('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    }).format(date);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-4xl max-h-[80vh] overflow-hidden flex flex-col">
        <DialogHeader>
          <DialogTitle>Commit History</DialogTitle>
          <DialogDescription>레포지토리의 커밋 히스토리를 조회합니다.</DialogDescription>
        </DialogHeader>

        <div className="flex-1 overflow-y-auto space-y-2">
          {commitsLoading ? (
            <div className="flex items-center justify-center py-8">
              <Loader2 className="h-6 w-6 animate-spin" />
            </div>
          ) : !commits || commits.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">커밋이 없습니다.</div>
          ) : (
            commits.map((commit) => (
              <div
                key={commit.sha}
                className="border rounded-lg p-3 hover:bg-accent/50 transition-colors"
              >
                {/* 커밋 요약 */}
                <div
                  className="flex items-start gap-3 cursor-pointer"
                  onClick={() => handleCommitClick(commit.sha)}
                >
                  <GitCommit className="h-5 w-5 mt-0.5 flex-shrink-0" />
                  <div className="flex-1 min-w-0">
                    <div className="font-medium text-sm truncate">{commit.message}</div>
                    <div className="text-xs text-muted-foreground mt-1">
                      <span className="font-mono">{commit.sha.substring(0, 7)}</span>
                      <span className="mx-2">•</span>
                      <span>{commit.authorName}</span>
                      <span className="mx-2">•</span>
                      <span>{formatDate(commit.committedAt)}</span>
                    </div>
                  </div>
                  {onSelectCommit && (
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleSelectCommit(commit.sha);
                      }}
                    >
                      선택
                    </Button>
                  )}
                </div>

                {/* 커밋 상세 (확장 시) */}
                {selectedSha === commit.sha && (
                  <div className="mt-3 pt-3 border-t">
                    {detailLoading ? (
                      <div className="flex items-center justify-center py-4">
                        <Loader2 className="h-5 w-5 animate-spin" />
                      </div>
                    ) : commitDetail ? (
                      <div className="space-y-2">
                        <div className="text-sm font-medium">
                          변경된 파일 ({commitDetail.changedFiles.length})
                        </div>
                        <div className="space-y-1 max-h-60 overflow-y-auto">
                          {commitDetail.changedFiles.map((file, idx) => (
                            <div
                              key={idx}
                              className="flex items-center gap-2 text-sm py-1 px-2 rounded hover:bg-accent/30"
                            >
                              {getChangeIcon(file.changeType)}
                              <span className="flex-1 truncate font-mono text-xs">
                                {file.changeType === 'RENAMED' && file.oldPath
                                  ? `${file.oldPath} → ${file.path}`
                                  : file.path}
                              </span>
                              {getChangeBadge(file.changeType)}
                            </div>
                          ))}
                        </div>
                      </div>
                    ) : (
                      <div className="text-sm text-muted-foreground">상세 정보를 불러올 수 없습니다.</div>
                    )}
                  </div>
                )}
              </div>
            ))
          )}
        </div>

        {/* 페이지네이션 */}
        <div className="flex items-center justify-between pt-4 border-t">
          <Button variant="outline" onClick={() => setPage(Math.max(0, page - 1))} disabled={page === 0}>
            이전
          </Button>
          <span className="text-sm text-muted-foreground">Page {page + 1}</span>
          <Button
            variant="outline"
            onClick={() => setPage(page + 1)}
            disabled={!commits || commits.length < limit}
          >
            다음
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
