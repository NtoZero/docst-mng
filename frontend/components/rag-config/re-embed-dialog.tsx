'use client';

import { useState, useEffect } from 'react';
import { useTranslations } from 'next-intl';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
import { useTriggerReEmbed, useReEmbedStatus } from '@/hooks/use-rag-config';
import { AlertTriangle, CheckCircle, Loader2, RefreshCw, XCircle } from 'lucide-react';

interface ReEmbedDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  projectId: string;
}

export function ReEmbedDialog({ open, onOpenChange, projectId }: ReEmbedDialogProps) {
  const t = useTranslations('ragConfig.reEmbed');
  const [isPolling, setIsPolling] = useState(false);

  const triggerReEmbed = useTriggerReEmbed(projectId);
  const { data: status, refetch } = useReEmbedStatus(projectId, {
    enabled: isPolling,
    refetchInterval: isPolling ? 2000 : false,
  });

  // 상태 폴링
  useEffect(() => {
    if (status && !status.inProgress && isPolling) {
      setIsPolling(false);
    }
  }, [status, isPolling]);

  const handleStartReEmbed = async () => {
    await triggerReEmbed.mutateAsync();
    setIsPolling(true);
  };

  const handleClose = () => {
    if (!status?.inProgress) {
      onOpenChange(false);
    }
  };

  const isCompleted = status && !status.inProgress && status.processedVersions > 0;
  const hasFailed = status && status.failedCount > 0;

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <RefreshCw className="w-5 h-5" />
            {t('title')}
          </DialogTitle>
          <DialogDescription>{t('description')}</DialogDescription>
        </DialogHeader>

        <div className="py-4">
          {!status && !triggerReEmbed.isPending && (
            <div className="text-center py-6">
              <AlertTriangle className="w-12 h-12 mx-auto text-yellow-500 mb-4" />
              <p className="text-sm text-muted-foreground mb-4">{t('warning')}</p>
              <p className="text-sm font-medium">{t('confirmMessage')}</p>
            </div>
          )}

          {(triggerReEmbed.isPending || status?.inProgress) && (
            <div className="space-y-4">
              <div className="flex items-center justify-center py-4">
                <Loader2 className="w-8 h-8 animate-spin text-primary" />
              </div>
              {status && (
                <>
                  <Progress value={status.progress} />
                  <div className="grid grid-cols-2 gap-4 text-sm">
                    <div>
                      <span className="text-muted-foreground">{t('processed')}: </span>
                      <span className="font-medium">
                        {status.processedVersions} / {status.totalVersions}
                      </span>
                    </div>
                    <div>
                      <span className="text-muted-foreground">{t('embedded')}: </span>
                      <span className="font-medium">{status.embeddedCount}</span>
                    </div>
                    <div>
                      <span className="text-muted-foreground">{t('deleted')}: </span>
                      <span className="font-medium">{status.deletedEmbeddings}</span>
                    </div>
                    {status.failedCount > 0 && (
                      <div className="text-red-500">
                        <span>{t('failed')}: </span>
                        <span className="font-medium">{status.failedCount}</span>
                      </div>
                    )}
                  </div>
                </>
              )}
            </div>
          )}

          {isCompleted && (
            <div className="text-center py-6">
              {hasFailed ? (
                <>
                  <XCircle className="w-12 h-12 mx-auto text-yellow-500 mb-4" />
                  <p className="font-medium">{t('completedWithErrors')}</p>
                  <p className="text-sm text-muted-foreground mt-2">
                    {t('completedStats', {
                      total: status.processedVersions,
                      failed: status.failedCount,
                    })}
                  </p>
                </>
              ) : (
                <>
                  <CheckCircle className="w-12 h-12 mx-auto text-green-500 mb-4" />
                  <p className="font-medium">{t('completed')}</p>
                  <p className="text-sm text-muted-foreground mt-2">
                    {t('completedMessage', { count: status.embeddedCount })}
                  </p>
                </>
              )}
            </div>
          )}
        </div>

        <DialogFooter>
          {!status && !triggerReEmbed.isPending && (
            <>
              <Button variant="outline" onClick={() => onOpenChange(false)}>
                {t('cancel')}
              </Button>
              <Button onClick={handleStartReEmbed} variant="destructive">
                {t('start')}
              </Button>
            </>
          )}
          {isCompleted && (
            <Button onClick={() => onOpenChange(false)}>{t('close')}</Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
