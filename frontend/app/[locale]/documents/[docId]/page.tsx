'use client';

import { use, useEffect, useState, useCallback } from 'react';
import { Link, useRouter } from '@/i18n/routing';
import {
  ArrowLeft,
  History,
  FileText,
  User,
  Calendar,
  GitCommit,
  Loader2,
  Pencil,
  X,
  Save,
  Upload,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { MarkdownViewer } from '@/components/markdown-viewer';
import { TableOfContents } from '@/components/markdown';
import {
  DocumentEditor,
  EditorViewModeToggle,
  CommitDialog,
  UnsavedChangesAlert,
} from '@/components/editor';
import { useDocument, useUpdateDocument, usePushRepository } from '@/hooks/use-api';
import { useAuthHydrated, useEditorStore } from '@/lib/store';
import { useToast } from '@/hooks/use-toast';

export default function DocumentDetailPage({ params }: { params: Promise<{ docId: string }> }) {
  const { docId } = use(params);
  const router = useRouter();
  const { toast } = useToast();
  const { isHydrated, user } = useAuthHydrated();

  const { data: document, isLoading, error } = useDocument(docId);
  const updateMutation = useUpdateDocument();
  const pushMutation = usePushRepository();

  // Editor state from store
  const {
    isEditMode,
    viewMode,
    hasUnsavedChanges,
    editedContent,
    setEditMode,
    setViewMode,
    setContent,
    updateEditedContent,
    resetEditor,
  } = useEditorStore();

  // Dialog states
  const [commitDialogOpen, setCommitDialogOpen] = useState(false);
  const [unsavedAlertOpen, setUnsavedAlertOpen] = useState(false);

  useEffect(() => {
    // Wait for hydration before checking auth
    if (isHydrated && !user) {
      router.push('/login');
    }
  }, [isHydrated, user, router]);

  // Reset editor state when leaving the page
  useEffect(() => {
    return () => {
      resetEditor();
    };
  }, [resetEditor]);

  // Handle entering edit mode
  const handleEnterEditMode = useCallback(() => {
    if (document?.content) {
      setContent(document.content);
      setEditMode(true);
    }
  }, [document?.content, setContent, setEditMode]);

  // Handle cancel with unsaved changes check
  const handleCancelEdit = useCallback(() => {
    if (hasUnsavedChanges) {
      setUnsavedAlertOpen(true);
    } else {
      resetEditor();
    }
  }, [hasUnsavedChanges, resetEditor]);

  // Handle discard changes confirmation
  const handleDiscardChanges = useCallback(() => {
    setUnsavedAlertOpen(false);
    resetEditor();
  }, [resetEditor]);

  // Handle commit
  const handleCommit = useCallback(
    async (message: string) => {
      if (!editedContent) return;

      try {
        await updateMutation.mutateAsync({
          id: docId,
          data: {
            content: editedContent,
            commitMessage: message,
          },
        });

        setCommitDialogOpen(false);
        resetEditor();
        toast.success('Document saved successfully');
      } catch (err) {
        toast.error(err instanceof Error ? err.message : 'Failed to save document');
      }
    },
    [docId, editedContent, updateMutation, resetEditor, toast]
  );

  // Handle push to remote
  const handlePush = useCallback(async () => {
    if (!document?.repositoryId) return;

    try {
      const result = await pushMutation.mutateAsync({ id: document.repositoryId });
      if (result.success) {
        toast.success(result.message);
      } else {
        toast.error(result.message);
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to push to remote');
    }
  }, [document?.repositoryId, pushMutation, toast]);

  // Show loading while hydrating or if no user yet
  if (!isHydrated) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (!user) return null;

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (error || !document) {
    return (
      <div className="space-y-6">
        <Button variant="ghost" size="sm" onClick={() => router.back()}>
          <ArrowLeft className="mr-2 h-4 w-4" />
          Go back
        </Button>
        <Card>
          <CardContent className="py-8 text-center text-destructive">
            Document not found or failed to load.
          </CardContent>
        </Card>
      </div>
    );
  }

  // Edit Mode UI
  if (isEditMode) {
    return (
      <div className="flex flex-col h-[calc(100vh-8rem)]">
        {/* Edit Header */}
        <div className="flex items-center justify-between pb-4 border-b mb-4">
          <div className="flex items-center gap-4">
            <Button variant="ghost" size="icon" onClick={handleCancelEdit}>
              <X className="h-4 w-4" />
            </Button>
            <div>
              <div className="flex items-center gap-2">
                <FileText className="h-5 w-5" />
                <h1 className="text-xl font-bold">{document.title || document.path}</h1>
                {hasUnsavedChanges && (
                  <Badge variant="secondary" className="ml-2">
                    Unsaved
                  </Badge>
                )}
              </div>
              <p className="text-sm text-muted-foreground">{document.path}</p>
            </div>
          </div>
          <div className="flex items-center gap-4">
            <EditorViewModeToggle mode={viewMode} onModeChange={setViewMode} />
            <Button onClick={() => setCommitDialogOpen(true)} disabled={!hasUnsavedChanges}>
              <Save className="mr-2 h-4 w-4" />
              Save
            </Button>
          </div>
        </div>

        {/* Editor */}
        <div className="flex-1 min-h-0">
          <DocumentEditor
            content={editedContent || ''}
            onChange={updateEditedContent}
            viewMode={viewMode}
            className="h-full"
          />
        </div>

        {/* Dialogs */}
        <CommitDialog
          open={commitDialogOpen}
          onOpenChange={setCommitDialogOpen}
          onCommit={handleCommit}
          documentPath={document.path}
          isLoading={updateMutation.isPending}
          originalContent={document.content || ''}
          editedContent={editedContent || ''}
        />
        <UnsavedChangesAlert
          open={unsavedAlertOpen}
          onOpenChange={setUnsavedAlertOpen}
          onConfirm={handleDiscardChanges}
        />
      </div>
    );
  }

  // View Mode UI
  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="icon" onClick={() => router.back()}>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <div>
            <div className="flex items-center gap-2">
              <FileText className="h-6 w-6" />
              <h1 className="text-3xl font-bold">{document.title || document.path}</h1>
            </div>
            <p className="text-muted-foreground">{document.path}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {document.docType === 'MD' && (
            <Button variant="outline" onClick={handleEnterEditMode}>
              <Pencil className="mr-2 h-4 w-4" />
              Edit
            </Button>
          )}
          <Button
            variant="outline"
            onClick={handlePush}
            disabled={pushMutation.isPending}
          >
            {pushMutation.isPending ? (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            ) : (
              <Upload className="mr-2 h-4 w-4" />
            )}
            Push
          </Button>
          <Button variant="outline" asChild>
            <Link href={`/documents/${docId}/versions`}>
              <History className="mr-2 h-4 w-4" />
              View History
            </Link>
          </Button>
        </div>
      </div>

      <div className="flex gap-6">
        {/* Main Content */}
        <Card className="flex-1 min-w-0">
          <CardHeader className="pb-3">
            <div className="flex flex-wrap items-center gap-4 text-sm text-muted-foreground">
              <div className="flex items-center gap-1">
                <User className="h-4 w-4" />
                <span>{document.authorName || 'Unknown'}</span>
              </div>
              <div className="flex items-center gap-1">
                <Calendar className="h-4 w-4" />
                <span>
                  {document.committedAt
                    ? new Date(document.committedAt).toLocaleDateString()
                    : 'Unknown'}
                </span>
              </div>
              <div className="flex items-center gap-1">
                <GitCommit className="h-4 w-4" />
                <span className="font-mono">{document.latestCommitSha?.substring(0, 7)}</span>
              </div>
              <Badge variant="outline">{document.docType}</Badge>
            </div>
          </CardHeader>
          <CardContent>
            {document.docType === 'MD' ? (
              <MarkdownViewer content={document.content || ''} />
            ) : (
              <pre className="overflow-x-auto rounded-lg bg-muted p-4 font-mono text-sm">
                {document.content}
              </pre>
            )}
          </CardContent>
        </Card>

        {/* TOC Sidebar - only for Markdown */}
        {document.docType === 'MD' && document.content && (
          <aside className="hidden w-64 shrink-0 xl:block">
            <div className="sticky top-6">
              <Card>
                <CardContent className="p-4">
                  <TableOfContents content={document.content} />
                </CardContent>
              </Card>
            </div>
          </aside>
        )}
      </div>
    </div>
  );
}
