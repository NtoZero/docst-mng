'use client';

import { useState } from 'react';
import { ArrowRightLeft, AlertTriangle, Loader2 } from 'lucide-react';
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { useProjects, useMoveRepository } from '@/hooks/use-api';
import type { Repository, Project } from '@/lib/types';

interface MoveRepositoryDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  repository: Repository;
  currentProject: Project;
  onSuccess?: () => void;
}

export function MoveRepositoryDialog({
  open,
  onOpenChange,
  repository,
  currentProject,
  onSuccess,
}: MoveRepositoryDialogProps) {
  const [targetProjectId, setTargetProjectId] = useState<string>('');
  const [error, setError] = useState<string | null>(null);

  const { data: projects, isLoading: isLoadingProjects } = useProjects();
  const moveRepository = useMoveRepository();

  // Filter out current project from the list
  const availableProjects = projects?.filter((p) => p.id !== currentProject.id) || [];

  const handleConfirm = async () => {
    if (!targetProjectId) {
      setError('이관할 프로젝트를 선택해주세요.');
      return;
    }

    setError(null);
    try {
      await moveRepository.mutateAsync({
        id: repository.id,
        targetProjectId,
      });
      onOpenChange(false);
      setTargetProjectId('');
      onSuccess?.();
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'status' in err) {
        const apiError = err as { status: number; message?: string };
        if (apiError.status === 409) {
          setError('대상 프로젝트에 동일한 레포지토리가 이미 존재합니다.');
        } else if (apiError.status === 404) {
          setError('레포지토리 또는 대상 프로젝트를 찾을 수 없습니다.');
        } else {
          setError(apiError.message || '레포지토리 이관 중 오류가 발생했습니다.');
        }
      } else {
        setError('레포지토리 이관 중 오류가 발생했습니다.');
      }
    }
  };

  const handleClose = () => {
    onOpenChange(false);
    setTargetProjectId('');
    setError(null);
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-[450px]">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <ArrowRightLeft className="h-5 w-5" />
            레포지토리 이관
          </DialogTitle>
          <DialogDescription>
            레포지토리를 다른 프로젝트로 이관합니다.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-4">
          {/* Current repository info */}
          <div className="space-y-2">
            <Label className="text-muted-foreground">레포지토리</Label>
            <div className="rounded-md border bg-muted/50 p-3">
              <p className="font-medium">{repository.owner}/{repository.name}</p>
              <p className="text-sm text-muted-foreground">
                현재 프로젝트: {currentProject.name}
              </p>
            </div>
          </div>

          {/* Target project selector */}
          <div className="space-y-2">
            <Label htmlFor="target-project">이관할 프로젝트</Label>
            {isLoadingProjects ? (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" />
                프로젝트 목록 로딩 중...
              </div>
            ) : availableProjects.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                이관할 수 있는 다른 프로젝트가 없습니다.
              </p>
            ) : (
              <Select value={targetProjectId} onValueChange={setTargetProjectId}>
                <SelectTrigger id="target-project">
                  <SelectValue placeholder="프로젝트 선택..." />
                </SelectTrigger>
                <SelectContent>
                  {availableProjects.map((project) => (
                    <SelectItem key={project.id} value={project.id}>
                      {project.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          </div>

          {/* Warning message */}
          <div className="flex items-start gap-2 rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-200">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
            <p>
              레포지토리와 연결된 모든 문서 및 동기화 기록이 함께 이관됩니다.
            </p>
          </div>

          {/* Error message */}
          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={handleClose}>
            취소
          </Button>
          <Button
            onClick={handleConfirm}
            disabled={!targetProjectId || moveRepository.isPending || availableProjects.length === 0}
          >
            {moveRepository.isPending ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                이관 중...
              </>
            ) : (
              '이관'
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
