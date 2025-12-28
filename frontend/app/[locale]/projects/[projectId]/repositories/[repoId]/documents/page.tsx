'use client';

import { use, useEffect, useState } from 'react';
import { Link, useRouter } from '@/i18n/routing';
import { ArrowLeft, FileText, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { useDocuments, useRepository } from '@/hooks/use-api';
import { useAuthStore } from '@/lib/store';
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

  const { data: repo, isLoading: repoLoading } = useRepository(repoId);
  const { data: documents, isLoading: docsLoading } = useDocuments(repoId);

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
        {documents && documents.length > 0 && (
          <ViewToggle view={viewMode} onViewChange={handleViewChange} />
        )}
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
