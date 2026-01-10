'use client';

import { use, useCallback, useEffect, useState } from 'react';
import { Link, useRouter } from '@/i18n/routing';
import { ArrowLeft, FileText, Loader2, Upload, GitCommit, ChevronDown } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover';
import { useDocuments, useRepository, useUnpushedCommits, usePushRepository } from '@/hooks/use-api';
import { useAuthStore } from '@/lib/store';
import { useToast } from '@/hooks/use-toast';
import { DocumentContentView } from '@/components/documents/document-content-view';
import { DocumentTreeView } from '@/components/documents/document-tree-view';
import { ViewToggle, type ViewMode } from '@/components/documents/view-toggle';

export default function DocumentsPage({
  params,
}: {
  params: Promise<{ projectId: string; repoId: string }>;
}) {
  const { projectId, repoId } = use(params);
  const router = useRouter();
  const user = useAuthStore((state) => state.user);
  const { toast } = useToast();

  const { data: repo, isLoading: repoLoading } = useRepository(repoId);
  const { data: documents, isLoading: docsLoading } = useDocuments(repoId);

  // Unpushed commits 조회
  const {
    data: unpushedData,
    refetch: refetchUnpushed,
  } = useUnpushedCommits(repoId, repo?.defaultBranch, !!repo);

  // Push mutation
  const pushMutation = usePushRepository();

  // Push 핸들러
  const handlePush = useCallback(async () => {
    if (!repo) return;

    try {
      const result = await pushMutation.mutateAsync({
        id: repoId,
        branch: repo.defaultBranch,
      });
      if (result.success) {
        toast.success(result.message);
        refetchUnpushed();
      } else {
        toast.error(result.message);
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to push to remote');
    }
  }, [repo, repoId, pushMutation, toast, refetchUnpushed]);

  // View mode state with localStorage persistence
  const [viewMode, setViewMode] = useState<ViewMode>('content');

  useEffect(() => {
    // Load saved view preference
    const savedView = localStorage.getItem('documents-view-mode') as ViewMode;
    if (savedView === 'content' || savedView === 'tree') {
      setViewMode(savedView);
    }
  }, []);

  const handleViewChange = (view: ViewMode) => {
    setViewMode(view);
    localStorage.setItem('documents-view-mode', view);
  };

  useEffect(() => {
    if (!user) {
      router.push('/login');
    }
  }, [user, router]);

  if (!user) return null;

  const isLoading = repoLoading || docsLoading;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="icon" asChild>
            <Link href={`/projects/${projectId}`}>
              <ArrowLeft className="h-4 w-4" />
            </Link>
          </Button>
          <div>
            <h1 className="text-3xl font-bold">Documents</h1>
            {repo && (
              <p className="text-muted-foreground">
                {repo.owner}/{repo.name}
              </p>
            )}
          </div>
        </div>
        <div className="flex items-center gap-2">
          {/* Push Button with Unpushed Commits Popover */}
          {repo && (
            <Popover>
              <PopoverTrigger asChild>
                <Button
                  variant="outline"
                  disabled={pushMutation.isPending || !unpushedData?.hasPushableCommits}
                >
                  {pushMutation.isPending ? (
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  ) : (
                    <Upload className="mr-2 h-4 w-4" />
                  )}
                  Push
                  {unpushedData && unpushedData.totalCount > 0 && (
                    <Badge variant="secondary" className="ml-2">
                      {unpushedData.totalCount}
                    </Badge>
                  )}
                  <ChevronDown className="ml-1 h-4 w-4" />
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-80" align="end">
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <h4 className="font-medium">Unpushed Commits</h4>
                    <span className="text-sm text-muted-foreground">
                      {unpushedData?.branch}
                    </span>
                  </div>

                  {!unpushedData || unpushedData.totalCount === 0 ? (
                    <p className="text-sm text-muted-foreground">
                      No unpushed commits
                    </p>
                  ) : (
                    <>
                      <div className="max-h-48 overflow-y-auto space-y-2">
                        {unpushedData.commits.slice(0, 5).map((commit) => (
                          <div
                            key={commit.sha}
                            className="flex items-start gap-2 text-sm border-b pb-2 last:border-0"
                          >
                            <GitCommit className="h-4 w-4 mt-0.5 shrink-0 text-muted-foreground" />
                            <div className="min-w-0">
                              <p className="font-mono text-xs text-muted-foreground">
                                {commit.sha.substring(0, 7)}
                              </p>
                              <p className="truncate">{commit.message}</p>
                            </div>
                          </div>
                        ))}
                        {unpushedData.totalCount > 5 && (
                          <p className="text-xs text-muted-foreground text-center">
                            +{unpushedData.totalCount - 5} more commits
                          </p>
                        )}
                      </div>

                      <Button
                        className="w-full"
                        onClick={handlePush}
                        disabled={pushMutation.isPending}
                      >
                        {pushMutation.isPending ? (
                          <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        ) : (
                          <Upload className="mr-2 h-4 w-4" />
                        )}
                        Push {unpushedData.totalCount} commit
                        {unpushedData.totalCount !== 1 ? 's' : ''}
                      </Button>
                    </>
                  )}
                </div>
              </PopoverContent>
            </Popover>
          )}

          {/* View Toggle */}
          {documents && documents.length > 0 && (
            <ViewToggle view={viewMode} onViewChange={handleViewChange} />
          )}
        </div>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
        </div>
      ) : documents && documents.length > 0 ? (
        viewMode === 'content' ? (
          <DocumentContentView documents={documents} />
        ) : (
          <DocumentTreeView documents={documents} />
        )
      ) : (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-12">
            <FileText className="h-12 w-12 text-muted-foreground" />
            <h3 className="mt-4 text-lg font-semibold">No documents yet</h3>
            <p className="mt-2 text-sm text-muted-foreground">
              Sync the repository to import documents
            </p>
            <Button asChild className="mt-6">
              <Link href={`/projects/${projectId}`}>Go back and sync</Link>
            </Button>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
