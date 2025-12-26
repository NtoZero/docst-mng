'use client';

import { RefreshCw, CheckCircle, XCircle, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';
import type { SyncEvent, SyncStatus } from '@/lib/types';

interface SyncProgressProps {
  repositoryName: string;
  isConnecting: boolean;
  isSyncing: boolean;
  syncEvent: SyncEvent | null;
  error: string | null;
  onStart: () => void;
  onCancel: () => void;
}

function getStatusIcon(status: SyncStatus | null, isConnecting: boolean, isSyncing: boolean) {
  if (isConnecting) {
    return <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />;
  }

  if (!status || isSyncing) {
    return <RefreshCw className="h-5 w-5 animate-spin text-primary" />;
  }

  switch (status) {
    case 'SUCCEEDED':
      return <CheckCircle className="h-5 w-5 text-green-500" />;
    case 'FAILED':
      return <XCircle className="h-5 w-5 text-destructive" />;
    default:
      return <RefreshCw className="h-5 w-5 animate-spin text-primary" />;
  }
}

function getStatusText(
  status: SyncStatus | null,
  isConnecting: boolean,
  isSyncing: boolean,
  message?: string
): string {
  if (isConnecting) {
    return 'Connecting to sync stream...';
  }

  if (!status) {
    return isSyncing ? 'Starting sync...' : 'Ready to sync';
  }

  switch (status) {
    case 'PENDING':
      return 'Sync pending...';
    case 'RUNNING':
      return message || 'Syncing...';
    case 'SUCCEEDED':
      return message || 'Sync completed successfully';
    case 'FAILED':
      return message || 'Sync failed';
    default:
      return 'Unknown status';
  }
}

export function SyncProgress({
  repositoryName,
  isConnecting,
  isSyncing,
  syncEvent,
  error,
  onStart,
  onCancel,
}: SyncProgressProps) {
  const status = syncEvent?.status as SyncStatus | null;
  const isActive = isConnecting || isSyncing;
  const isComplete = status === 'SUCCEEDED' || status === 'FAILED';
  const progress = syncEvent?.progress ?? 0;
  const processedDocs = syncEvent?.processedDocs ?? 0;
  const totalDocs = syncEvent?.totalDocs ?? 0;

  return (
    <Card>
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <CardTitle className="flex items-center gap-2 text-base">
            {getStatusIcon(status, isConnecting, isSyncing)}
            Sync: {repositoryName}
          </CardTitle>
          {isActive && (
            <Button variant="ghost" size="sm" onClick={onCancel}>
              Cancel
            </Button>
          )}
          {!isActive && !isComplete && (
            <Button size="sm" onClick={onStart}>
              <RefreshCw className="mr-2 h-4 w-4" />
              Start Sync
            </Button>
          )}
          {isComplete && (
            <Button variant="outline" size="sm" onClick={onStart}>
              <RefreshCw className="mr-2 h-4 w-4" />
              Sync Again
            </Button>
          )}
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <p className="text-sm text-muted-foreground">
          {error ? (
            <span className="text-destructive">{error}</span>
          ) : (
            getStatusText(status, isConnecting, isSyncing, syncEvent?.message)
          )}
        </p>

        {(isActive || (syncEvent && status === 'RUNNING')) && (
          <>
            <Progress value={progress} className="h-2" />
            <div className="flex items-center justify-between text-xs text-muted-foreground">
              <span>
                {processedDocs} / {totalDocs} documents
              </span>
              <span>{Math.round(progress)}%</span>
            </div>
          </>
        )}

        {status === 'SUCCEEDED' && (
          <div className="rounded-lg bg-green-50 p-3 text-sm text-green-800 dark:bg-green-900/20 dark:text-green-300">
            Successfully synced {processedDocs} documents
          </div>
        )}

        {status === 'FAILED' && error && (
          <div className="rounded-lg bg-red-50 p-3 text-sm text-red-800 dark:bg-red-900/20 dark:text-red-300">
            {error}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
