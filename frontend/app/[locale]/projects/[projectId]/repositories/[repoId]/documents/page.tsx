'use client';

import { use, useEffect } from 'react';
import { Link, useRouter } from '@/i18n/routing';
import { ArrowLeft, FileText, Folder, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { useDocuments, useRepository } from '@/hooks/use-api';
import { useAuthStore } from '@/lib/store';
import type { Document, DocType } from '@/lib/types';

function getDocTypeBadge(docType: DocType) {
  const variants: Record<DocType, { label: string; variant: 'default' | 'secondary' | 'outline' }> =
    {
      MD: { label: 'Markdown', variant: 'default' },
      ADOC: { label: 'AsciiDoc', variant: 'secondary' },
      OPENAPI: { label: 'OpenAPI', variant: 'outline' },
      ADR: { label: 'ADR', variant: 'secondary' },
      OTHER: { label: 'Other', variant: 'outline' },
    };
  const { label, variant } = variants[docType] || variants.OTHER;
  return <Badge variant={variant}>{label}</Badge>;
}

function groupDocumentsByPath(documents: Document[]) {
  const tree: Record<string, Document[]> = {};

  documents.forEach((doc) => {
    const parts = doc.path.split('/');
    const folder = parts.length > 1 ? parts.slice(0, -1).join('/') : '/';
    if (!tree[folder]) {
      tree[folder] = [];
    }
    tree[folder].push(doc);
  });

  return Object.entries(tree).sort(([a], [b]) => a.localeCompare(b));
}

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

  useEffect(() => {
    if (!user) {
      router.push('/login');
    }
  }, [user, router]);

  if (!user) return null;

  const isLoading = repoLoading || docsLoading;

  return (
    <div className="space-y-6">
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

      {isLoading ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
        </div>
      ) : documents && documents.length > 0 ? (
        <div className="space-y-6">
          {groupDocumentsByPath(documents).map(([folder, docs]) => (
            <div key={folder}>
              <div className="mb-2 flex items-center gap-2 text-sm font-medium text-muted-foreground">
                <Folder className="h-4 w-4" />
                {folder === '/' ? 'Root' : folder}
              </div>
              <div className="grid gap-2">
                {docs.map((doc) => (
                  <Link key={doc.id} href={`/documents/${doc.id}`}>
                    <Card className="cursor-pointer transition-colors hover:bg-accent">
                      <CardContent className="flex items-center justify-between p-4">
                        <div className="flex items-center gap-3">
                          <FileText className="h-5 w-5 text-muted-foreground" />
                          <div>
                            <p className="font-medium">{doc.title || doc.path.split('/').pop()}</p>
                            <p className="text-xs text-muted-foreground">{doc.path}</p>
                          </div>
                        </div>
                        <div className="flex items-center gap-2">
                          {getDocTypeBadge(doc.docType)}
                          <span className="text-xs text-muted-foreground">
                            {doc.latestCommitSha?.substring(0, 7)}
                          </span>
                        </div>
                      </CardContent>
                    </Card>
                  </Link>
                ))}
              </div>
            </div>
          ))}
        </div>
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
