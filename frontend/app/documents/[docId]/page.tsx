'use client';

import { use, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { ArrowLeft, History, FileText, User, Calendar, GitCommit, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { MarkdownViewer } from '@/components/markdown-viewer';
import { useDocument } from '@/hooks/use-api';
import { useAuthStore } from '@/lib/store';

export default function DocumentDetailPage({ params }: { params: Promise<{ docId: string }> }) {
  const { docId } = use(params);
  const router = useRouter();
  const user = useAuthStore((state) => state.user);

  const { data: document, isLoading, error } = useDocument(docId);

  useEffect(() => {
    if (!user) {
      router.push('/login');
    }
  }, [user, router]);

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
        <Button variant="outline" asChild>
          <Link href={`/documents/${docId}/versions`}>
            <History className="mr-2 h-4 w-4" />
            View History
          </Link>
        </Button>
      </div>

      <Card>
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
    </div>
  );
}
