'use client';

import { use, useEffect, useState, useCallback, useMemo } from 'react';
import { Link, useRouter } from '@/i18n/routing';
import { useQueryClient } from '@tanstack/react-query';
import {
  ArrowLeft,
  Plus,
  GitBranch,
  RefreshCw,
  FileText,
  ExternalLink,
  Loader2,
  ChevronDown,
  ChevronUp,
  Key,
  Settings,
  History,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import { SyncModeDialog } from '@/components/sync-mode-dialog';
import { CommitHistoryDialog } from '@/components/commit-history-dialog';
import {
  useProject,
  useRepositories,
  useSyncStatus,
  useCredentials,
  useSetRepositoryCredential,
  queryKeys,
} from '@/hooks/use-api';
import { useSync } from '@/hooks/use-sync';
import { useAuthStore, useUIStore } from '@/lib/store';
import type { Repository, SyncStatus, Credential, SyncMode } from '@/lib/types';

function getSyncStatusBadge(status: SyncStatus | undefined) {
  if (!status) return <Badge variant="secondary">Unknown</Badge>;

  switch (status) {
    case 'SUCCEEDED':
      return <Badge variant="success">Synced</Badge>;
    case 'RUNNING':
      return <Badge variant="warning">Syncing</Badge>;
    case 'PENDING':
      return <Badge variant="secondary">Pending</Badge>;
    case 'FAILED':
      return <Badge variant="destructive">Failed</Badge>;
    default:
      return <Badge variant="secondary">{status}</Badge>;
  }
}

function RepositoryCard({
  repo,
  projectId,
  credentials,
}: {
  repo: Repository;
  projectId: string;
  credentials: Credential[];
}) {
  const queryClient = useQueryClient();
  const [showProgress, setShowProgress] = useState(false);
  const [showSettings, setShowSettings] = useState(false);
  const [showSyncModeDialog, setShowSyncModeDialog] = useState(false);
  const [showCommitHistory, setShowCommitHistory] = useState(false);
  const setCredential = useSetRepositoryCredential();

  const handleSyncComplete = useCallback(() => {
    // Invalidate queries to refresh data - use correct queryKeys
    void queryClient.invalidateQueries({ queryKey: queryKeys.repositories.syncStatus(repo.id) });
    void queryClient.invalidateQueries({ queryKey: queryKeys.documents.byRepository(repo.id) });
  }, [queryClient, repo.id]);

  const handleCredentialChange = async (credentialId: string | null) => {
    await setCredential.mutateAsync({
      repoId: repo.id,
      data: { credentialId },
    });
  };

  const currentCredential = credentials.find((c) => c.id === repo.credentialId);

  // Memoize options to prevent unnecessary re-renders
  const syncOptions = useMemo(
    () => ({
      onComplete: handleSyncComplete,
    }),
    [handleSyncComplete]
  );

  const { startSync, cancelSync, isConnecting, isSyncing, syncEvent, error } = useSync(
    repo.id,
    syncOptions
  );

  // SSE 활성화 시 polling 비활성화
  const isSSEActive = isConnecting || isSyncing;
  const { data: syncStatus, isLoading: syncLoading } = useSyncStatus(repo.id, !isSSEActive);

  // Sync Mode Dialog를 열기
  const handleSync = () => {
    setShowSyncModeDialog(true);
  };

  // Sync Mode Dialog에서 모드 선택 후 실제 동기화 실행
  const handleSyncConfirm = (mode: SyncMode, targetCommitSha?: string, enableEmbedding?: boolean) => {
    setShowProgress(true);
    startSync({ mode, targetCommitSha, enableEmbedding });
  };

  const handleCancel = () => {
    cancelSync();
    setShowProgress(false);
  };

  const isActive = isSSEActive;
  const syncProgress = syncEvent?.progress ?? 0;
  const processedDocs = syncEvent?.processedDocs ?? 0;
  const totalDocs = syncEvent?.totalDocs ?? 0;
  const currentStatus = syncEvent?.status as SyncStatus | undefined;
  const isComplete = currentStatus === 'SUCCEEDED' || currentStatus === 'FAILED';

  // Auto-show progress when syncing starts
  useEffect(() => {
    if (isActive) {
      setShowProgress(true);
    }
  }, [isActive]);

  return (
    <Card>
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-2">
            <GitBranch className="h-5 w-5 text-muted-foreground" />
            <div>
              <CardTitle className="text-base">
                {repo.owner}/{repo.name}
              </CardTitle>
              <CardDescription className="text-xs">
                {repo.provider} &middot; {repo.defaultBranch}
              </CardDescription>
            </div>
          </div>
          {!syncLoading && !isActive && getSyncStatusBadge(syncStatus?.status)}
          {isActive && <Badge variant="warning">Syncing</Badge>}
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={handleSync}
              disabled={isActive}
              title="Sync documents and generate embeddings for semantic search"
            >
              {isActive ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                <RefreshCw className="mr-2 h-4 w-4" />
              )}
              Sync
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setShowCommitHistory(true)}
              disabled={isActive}
            >
              <History className="mr-2 h-4 w-4" />
              History
            </Button>
            <Button variant="outline" size="sm" asChild>
              <Link href={`/projects/${projectId}/repositories/${repo.id}/documents`}>
                <FileText className="mr-2 h-4 w-4" />
                Documents
              </Link>
            </Button>
          </div>
          <div className="flex items-center gap-1">
            {currentCredential && (
              <Badge variant="outline" className="gap-1">
                <Key className="h-3 w-3" />
                {currentCredential.name}
              </Badge>
            )}
            <Button
              variant="ghost"
              size="icon"
              onClick={() => setShowSettings(!showSettings)}
              className="h-8 w-8"
            >
              <Settings className="h-4 w-4" />
            </Button>
            {repo.cloneUrl && (
              <Button variant="ghost" size="icon" asChild>
                <a href={repo.cloneUrl} target="_blank" rel="noopener noreferrer">
                  <ExternalLink className="h-4 w-4" />
                </a>
              </Button>
            )}
            {(isActive || showProgress) && (
              <Button
                variant="ghost"
                size="icon"
                onClick={() => setShowProgress(!showProgress)}
                className="h-8 w-8"
              >
                {showProgress ? (
                  <ChevronUp className="h-4 w-4" />
                ) : (
                  <ChevronDown className="h-4 w-4" />
                )}
              </Button>
            )}
          </div>
        </div>

        {/* Settings Section */}
        {showSettings && (
          <div className="space-y-3 rounded-lg border bg-muted/50 p-3">
            <div className="space-y-2">
              <label className="text-sm font-medium">Authentication</label>
              <select
                value={repo.credentialId || ''}
                onChange={(e) => handleCredentialChange(e.target.value || null)}
                disabled={setCredential.isPending}
                className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
              >
                <option value="">No credential (public repository)</option>
                {credentials.map((cred) => (
                  <option key={cred.id} value={cred.id}>
                    {cred.name} ({cred.type})
                  </option>
                ))}
              </select>
              {credentials.length === 0 && (
                <p className="text-xs text-muted-foreground">
                  No credentials available.{' '}
                  <Link href="/credentials" className="text-primary hover:underline">
                    Add one
                  </Link>
                </p>
              )}
            </div>
          </div>
        )}

        {/* Sync Progress Section */}
        {showProgress && (isActive || syncEvent) && (
          <div className="space-y-2 rounded-lg border bg-muted/50 p-3">
            <div className="flex items-center justify-between text-sm">
              <span className="text-muted-foreground">
                {isConnecting
                  ? 'Connecting...'
                  : syncEvent?.message || (isSyncing ? 'Syncing...' : 'Ready')}
              </span>
              {isActive && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={handleCancel}
                  className="h-6 px-2 text-xs"
                >
                  Cancel
                </Button>
              )}
            </div>
            {(isActive || currentStatus === 'RUNNING') && (
              <>
                <Progress value={syncProgress} className="h-2" />
                <div className="flex items-center justify-between text-xs text-muted-foreground">
                  <span>
                    {processedDocs} / {totalDocs} documents
                  </span>
                  <span>{Math.round(syncProgress)}%</span>
                </div>
              </>
            )}
            {isComplete && (
              <div
                className={`rounded-md p-2 text-sm ${
                  currentStatus === 'SUCCEEDED'
                    ? 'bg-green-50 text-green-800 dark:bg-green-900/20 dark:text-green-300'
                    : 'bg-red-50 text-red-800 dark:bg-red-900/20 dark:text-red-300'
                }`}
              >
                {currentStatus === 'SUCCEEDED'
                  ? `Successfully synced ${processedDocs} documents`
                  : error || 'Sync failed'}
              </div>
            )}
          </div>
        )}

        {/* Error message from last sync */}
        {!showProgress && syncStatus?.errorMessage && (
          <p className="text-sm text-destructive">{syncStatus.errorMessage}</p>
        )}
      </CardContent>

      {/* Sync Mode Dialog */}
      <SyncModeDialog
        repositoryId={repo.id}
        open={showSyncModeDialog}
        onOpenChange={setShowSyncModeDialog}
        onConfirm={handleSyncConfirm}
      />

      {/* Commit History Dialog */}
      <CommitHistoryDialog
        repositoryId={repo.id}
        open={showCommitHistory}
        onOpenChange={setShowCommitHistory}
      />
    </Card>
  );
}

export default function ProjectDetailPage({ params }: { params: Promise<{ projectId: string }> }) {
  const { projectId } = use(params);
  const router = useRouter();
  const user = useAuthStore((state) => state.user);
  const setSelectedProjectId = useUIStore((state) => state.setSelectedProjectId);

  const { data: project, isLoading: projectLoading, error: projectError } = useProject(projectId);
  const { data: repositories, isLoading: reposLoading } = useRepositories(projectId);
  const { data: credentials = [] } = useCredentials();

  useEffect(() => {
    if (!user) {
      router.push('/login');
    }
  }, [user, router]);

  useEffect(() => {
    if (projectId) {
      setSelectedProjectId(projectId);
    }
  }, [projectId, setSelectedProjectId]);

  if (!user) {
    return null;
  }

  if (projectLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (projectError || !project) {
    return (
      <div className="space-y-6">
        <Button variant="ghost" size="sm" asChild>
          <Link href="/projects">
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back to Projects
          </Link>
        </Button>
        <Card>
          <CardContent className="py-8 text-center text-destructive">
            Project not found or failed to load.
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" asChild>
          <Link href="/projects">
            <ArrowLeft className="h-4 w-4" />
          </Link>
        </Button>
        <div className="flex-1">
          <h1 className="text-3xl font-bold">{project.name}</h1>
          {project.description && <p className="text-muted-foreground">{project.description}</p>}
        </div>
        <Button variant="outline" asChild>
          <Link href={`/projects/${projectId}/settings/credentials`}>
            <Settings className="mr-2 h-4 w-4" />
            Settings
          </Link>
        </Button>
      </div>

      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-semibold">Repositories</h2>
          <Button asChild>
            <Link href={`/projects/${projectId}/repositories/new`}>
              <Plus className="mr-2 h-4 w-4" />
              Add Repository
            </Link>
          </Button>
        </div>

        {reposLoading ? (
          <div className="grid gap-4 md:grid-cols-2">
            {[1, 2].map((i) => (
              <Card key={i} className="animate-pulse">
                <CardHeader>
                  <div className="h-5 w-3/4 rounded bg-muted" />
                  <div className="h-4 w-1/2 rounded bg-muted" />
                </CardHeader>
              </Card>
            ))}
          </div>
        ) : repositories && repositories.length > 0 ? (
          <div className="grid gap-4 md:grid-cols-2">
            {repositories.map((repo) => (
              <RepositoryCard
                key={repo.id}
                repo={repo}
                projectId={projectId}
                credentials={credentials}
              />
            ))}
          </div>
        ) : (
          <Card>
            <CardContent className="flex flex-col items-center justify-center py-12">
              <GitBranch className="h-12 w-12 text-muted-foreground" />
              <h3 className="mt-4 text-lg font-semibold">No repositories yet</h3>
              <p className="mt-2 text-sm text-muted-foreground">
                Connect a Git repository to start syncing documents
              </p>
              <Button asChild className="mt-6">
                <Link href={`/projects/${projectId}/repositories/new`}>
                  <Plus className="mr-2 h-4 w-4" />
                  Add your first repository
                </Link>
              </Button>
            </CardContent>
          </Card>
        )}
      </div>
    </div>
  );
}
