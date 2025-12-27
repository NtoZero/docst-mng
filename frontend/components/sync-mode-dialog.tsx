'use client';

import { useState } from 'react';
import { RefreshCw, GitBranch, History } from 'lucide-react';
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
import { CommitHistoryDialog } from './commit-history-dialog';
import type { SyncMode } from '@/lib/types';

interface SyncModeDialogProps {
  repositoryId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: (mode: SyncMode, targetCommitSha?: string) => void;
}

export function SyncModeDialog({ repositoryId, open, onOpenChange, onConfirm }: SyncModeDialogProps) {
  const [mode, setMode] = useState<SyncMode>('FULL_SCAN');
  const [targetCommitSha, setTargetCommitSha] = useState<string>('');
  const [showCommitHistory, setShowCommitHistory] = useState(false);

  const handleConfirm = () => {
    if (mode === 'SPECIFIC_COMMIT' && !targetCommitSha) {
      alert('특정 커밋을 선택해주세요.');
      return;
    }
    onConfirm(mode, mode === 'SPECIFIC_COMMIT' ? targetCommitSha : undefined);
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
