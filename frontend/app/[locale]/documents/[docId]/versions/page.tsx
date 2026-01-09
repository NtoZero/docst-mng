'use client';

import { use, useEffect, useState } from 'react';
import { Link, useRouter } from '@/i18n/routing';
import { ArrowLeft, GitCommit, User, Calendar, ArrowRight, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useDocument, useDocumentVersions, useDocumentDiff } from '@/hooks/use-api';
import { useAuthHydrated } from '@/lib/store';
import type { DocumentVersion } from '@/lib/types';

function DiffViewer({ diff }: { diff: string }) {
  const lines = diff.split('\n');

  return (
    <div className="overflow-x-auto rounded-lg border bg-muted font-mono text-sm">
      {lines.map((line, idx) => {
        let bgColor = '';
        let textColor = '';

        if (line.startsWith('+') && !line.startsWith('+++')) {
          bgColor = 'bg-green-100 dark:bg-green-900/30';
          textColor = 'text-green-800 dark:text-green-300';
        } else if (line.startsWith('-') && !line.startsWith('---')) {
          bgColor = 'bg-red-100 dark:bg-red-900/30';
          textColor = 'text-red-800 dark:text-red-300';
        } else if (line.startsWith('@@')) {
          bgColor = 'bg-blue-100 dark:bg-blue-900/30';
          textColor = 'text-blue-800 dark:text-blue-300';
        }

        return (
          <div key={idx} className={`px-4 py-0.5 ${bgColor} ${textColor}`}>
            <span className="mr-4 inline-block w-8 text-right text-muted-foreground">
              {idx + 1}
            </span>
            {line || ' '}
          </div>
        );
      })}
    </div>
  );
}

export default function DocumentVersionsPage({ params }: { params: Promise<{ docId: string }> }) {
  const { docId } = use(params);
  const router = useRouter();
  const { isHydrated, user } = useAuthHydrated();

  const [selectedVersions, setSelectedVersions] = useState<[string | null, string | null]>([
    null,
    null,
  ]);

  const { data: document } = useDocument(docId);
  const { data: versions, isLoading } = useDocumentVersions(docId);
  const { data: diff, isLoading: diffLoading } = useDocumentDiff(
    docId,
    selectedVersions[0] || '',
    selectedVersions[1] || ''
  );

  useEffect(() => {
    // Wait for hydration before checking auth
    if (isHydrated && !user) {
      router.push('/login');
    }
  }, [isHydrated, user, router]);

  useEffect(() => {
    if (versions && versions.length >= 2 && !selectedVersions[0]) {
      setSelectedVersions([versions[1].commitSha, versions[0].commitSha]);
    }
  }, [versions, selectedVersions]);

  const handleVersionClick = (commitSha: string) => {
    if (!selectedVersions[0]) {
      setSelectedVersions([commitSha, null]);
    } else if (!selectedVersions[1]) {
      if (commitSha !== selectedVersions[0]) {
        setSelectedVersions([selectedVersions[0], commitSha]);
      }
    } else {
      setSelectedVersions([commitSha, null]);
    }
  };

  const isSelected = (commitSha: string) =>
    selectedVersions[0] === commitSha || selectedVersions[1] === commitSha;

  // Show loading while hydrating
  if (!isHydrated) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (!user) return null;

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" asChild>
          <Link href={`/documents/${docId}`}>
            <ArrowLeft className="h-4 w-4" />
          </Link>
        </Button>
        <div>
          <h1 className="text-3xl font-bold">Version History</h1>
          {document && <p className="text-muted-foreground">{document.title || document.path}</p>}
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">Versions</CardTitle>
            <p className="text-sm text-muted-foreground">
              Select two versions to compare. Click to select.
            </p>
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <div className="flex items-center justify-center py-8">
                <Loader2 className="h-6 w-6 animate-spin" />
              </div>
            ) : versions && versions.length > 0 ? (
              <div className="space-y-2">
                {versions.map((version: DocumentVersion, idx: number) => (
                  <button
                    key={version.id}
                    onClick={() => handleVersionClick(version.commitSha)}
                    className={`w-full rounded-lg border p-3 text-left transition-colors ${
                      isSelected(version.commitSha)
                        ? 'border-primary bg-primary/5'
                        : 'hover:bg-accent'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <GitCommit className="h-4 w-4 text-muted-foreground" />
                        <span className="font-mono text-sm">
                          {version.commitSha.substring(0, 7)}
                        </span>
                        {idx === 0 && (
                          <span className="rounded bg-primary px-1.5 py-0.5 text-xs text-primary-foreground">
                            Latest
                          </span>
                        )}
                      </div>
                      {isSelected(version.commitSha) && (
                        <span className="text-xs text-primary">
                          {selectedVersions[0] === version.commitSha ? 'From' : 'To'}
                        </span>
                      )}
                    </div>
                    <p className="mt-1 truncate text-sm">{version.message || 'No message'}</p>
                    <div className="mt-2 flex items-center gap-4 text-xs text-muted-foreground">
                      <div className="flex items-center gap-1">
                        <User className="h-3 w-3" />
                        {version.authorName}
                      </div>
                      <div className="flex items-center gap-1">
                        <Calendar className="h-3 w-3" />
                        {new Date(version.committedAt).toLocaleDateString()}
                      </div>
                    </div>
                  </button>
                ))}
              </div>
            ) : (
              <p className="py-8 text-center text-muted-foreground">No versions found</p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-lg">Diff</CardTitle>
            {selectedVersions[0] && selectedVersions[1] && (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <span className="font-mono">{selectedVersions[0].substring(0, 7)}</span>
                <ArrowRight className="h-4 w-4" />
                <span className="font-mono">{selectedVersions[1].substring(0, 7)}</span>
              </div>
            )}
          </CardHeader>
          <CardContent>
            {!selectedVersions[0] || !selectedVersions[1] ? (
              <p className="py-8 text-center text-muted-foreground">
                Select two versions to see the diff
              </p>
            ) : diffLoading ? (
              <div className="flex items-center justify-center py-8">
                <Loader2 className="h-6 w-6 animate-spin" />
              </div>
            ) : diff ? (
              <DiffViewer diff={diff} />
            ) : (
              <p className="py-8 text-center text-muted-foreground">No changes between versions</p>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
