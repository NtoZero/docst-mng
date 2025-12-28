'use client';

import { useState } from 'react';
import { RefreshCw, GitBranch, History, Sparkles, Info } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { Switch } from '@/components/ui/switch';
import { CommitHistoryDialog } from './commit-history-dialog';
import type { SyncMode } from '@/lib/types';

interface SyncModeDialogProps {
  repositoryId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: (mode: SyncMode, targetCommitSha?: string, enableEmbedding?: boolean) => void;
}

export function SyncModeDialog({ repositoryId, open, onOpenChange, onConfirm }: SyncModeDialogProps) {
  const [mode, setMode] = useState<SyncMode>('FULL_SCAN');
  const [targetCommitSha, setTargetCommitSha] = useState<string>('');
  const [showCommitHistory, setShowCommitHistory] = useState(false);
  const [enableEmbedding, setEnableEmbedding] = useState(true);

  const handleConfirm = () => {
    if (mode === 'SPECIFIC_COMMIT' && !targetCommitSha) {
      alert('특정 커밋을 선택해주세요.');
      return;
    }
    onConfirm(mode, mode === 'SPECIFIC_COMMIT' ? targetCommitSha : undefined, enableEmbedding);
    onOpenChange(false);
  };

  const handleSelectCommit = (sha: string) => {
    setTargetCommitSha(sha);
    setShowCommitHistory(false);
  };

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="sm:max-w-[500px]">
          <DialogHeader>
            <DialogTitle>동기화 모드 선택</DialogTitle>
            <DialogDescription>레포지토리를 동기화할 방식을 선택하세요.</DialogDescription>
          </DialogHeader>

          {/* 안내 메시지 */}
          <div className="flex items-start gap-2 rounded-md border border-blue-200 bg-blue-50 p-3 text-sm text-blue-800 dark:border-blue-800 dark:bg-blue-950 dark:text-blue-200">
            <Info className="mt-0.5 h-4 w-4 shrink-0" />
            <p>
              동기화 시 문서가 자동으로 청킹되며, 임베딩 옵션을 켜면 AI 의미 검색(Semantic Search)을 위한 벡터가 생성됩니다.
            </p>
          </div>

          <div className="space-y-4 py-4">
            <RadioGroup value={mode} onValueChange={(value) => setMode(value as SyncMode)}>
              {/* Full Scan 모드 */}
              <div className="flex items-start space-x-3 space-y-0 rounded-md border p-4 hover:bg-accent/50 transition-colors">
                <RadioGroupItem value="FULL_SCAN" id="full-scan" />
                <div className="flex-1 space-y-1">
                  <Label htmlFor="full-scan" className="flex items-center gap-2 cursor-pointer">
                    <RefreshCw className="h-4 w-4" />
                    <span className="font-semibold">전체 스캔 (Full Scan)</span>
                  </Label>
                  <p className="text-sm text-muted-foreground">
                    최신 커밋의 모든 문서를 스캔합니다. 가장 확실하지만 시간이 오래 걸립니다.
                  </p>
                </div>
              </div>

              {/* Incremental 모드 */}
              <div className="flex items-start space-x-3 space-y-0 rounded-md border p-4 hover:bg-accent/50 transition-colors">
                <RadioGroupItem value="INCREMENTAL" id="incremental" />
                <div className="flex-1 space-y-1">
                  <Label htmlFor="incremental" className="flex items-center gap-2 cursor-pointer">
                    <GitBranch className="h-4 w-4" />
                    <span className="font-semibold">증분 동기화 (Incremental)</span>
                  </Label>
                  <p className="text-sm text-muted-foreground">
                    마지막 동기화 이후 변경된 문서만 처리합니다. 빠르고 효율적입니다.
                  </p>
                </div>
              </div>

              {/* Specific Commit 모드 */}
              <div className="flex items-start space-x-3 space-y-0 rounded-md border p-4 hover:bg-accent/50 transition-colors">
                <RadioGroupItem value="SPECIFIC_COMMIT" id="specific-commit" />
                <div className="flex-1 space-y-1">
                  <Label htmlFor="specific-commit" className="flex items-center gap-2 cursor-pointer">
                    <History className="h-4 w-4" />
                    <span className="font-semibold">특정 커밋 (Specific Commit)</span>
                  </Label>
                  <p className="text-sm text-muted-foreground">
                    특정 커밋 시점의 문서를 가져옵니다. 과거 버전 복원에 유용합니다.
                  </p>
                  {mode === 'SPECIFIC_COMMIT' && (
                    <div className="mt-3 space-y-2">
                      <div className="flex gap-2">
                        <input
                          type="text"
                          value={targetCommitSha}
                          onChange={(e) => setTargetCommitSha(e.target.value)}
                          placeholder="커밋 SHA를 입력하거나 선택하세요"
                          className="flex-1 px-3 py-2 text-sm border rounded-md bg-background"
                        />
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          onClick={() => setShowCommitHistory(true)}
                        >
                          선택
                        </Button>
                      </div>
                      {targetCommitSha && (
                        <p className="text-xs text-muted-foreground">
                          선택된 커밋: <span className="font-mono">{targetCommitSha.substring(0, 7)}</span>
                        </p>
                      )}
                    </div>
                  )}
                </div>
              </div>
            </RadioGroup>

            {/* Embedding 옵션 */}
            <div className="flex items-center justify-between rounded-md border p-4">
              <div className="flex items-center gap-3">
                <Sparkles className="h-5 w-5 text-amber-500" />
                <div className="space-y-0.5">
                  <Label htmlFor="enable-embedding" className="text-sm font-medium cursor-pointer">
                    임베딩 생성
                  </Label>
                  <p className="text-xs text-muted-foreground">
                    의미 검색(Semantic/Hybrid)을 위한 벡터 생성
                  </p>
                </div>
              </div>
              <Switch
                id="enable-embedding"
                checked={enableEmbedding}
                onCheckedChange={setEnableEmbedding}
              />
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => onOpenChange(false)}>
              취소
            </Button>
            <Button onClick={handleConfirm}>동기화 시작</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Commit History Dialog (Specific Commit 모드에서 사용) */}
      <CommitHistoryDialog
        repositoryId={repositoryId}
        open={showCommitHistory}
        onOpenChange={setShowCommitHistory}
        onSelectCommit={handleSelectCommit}
      />
    </>
  );
}
