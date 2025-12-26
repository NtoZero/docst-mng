'use client';

import { use, useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { ArrowLeft, Search as SearchIcon, FileText, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { useProject, useSearch } from '@/hooks/use-api';
import { useAuthStore } from '@/lib/store';
import type { SearchResult } from '@/lib/types';

function SearchResultCard({ result }: { result: SearchResult }) {
  return (
    <Link href={`/documents/${result.documentId}`}>
      <Card className="cursor-pointer transition-colors hover:bg-accent">
        <CardContent className="p-4">
          <div className="flex items-start justify-between">
            <div className="flex items-start gap-3">
              <FileText className="mt-0.5 h-5 w-5 text-muted-foreground" />
              <div>
                <p className="font-medium">{result.path}</p>
                <div
                  className="mt-1 text-sm text-muted-foreground"
                  dangerouslySetInnerHTML={{ __html: result.highlightedSnippet || result.snippet }}
                />
              </div>
            </div>
            <Badge variant="outline">{Math.round(result.score * 100)}%</Badge>
          </div>
        </CardContent>
      </Card>
    </Link>
  );
}

export default function SearchPage({ params }: { params: Promise<{ projectId: string }> }) {
  const { projectId } = use(params);
  const router = useRouter();
  const searchParams = useSearchParams();
  const user = useAuthStore((state) => state.user);

  const initialQuery = searchParams.get('q') || '';
  const [query, setQuery] = useState(initialQuery);
  const [searchQuery, setSearchQuery] = useState(initialQuery);

  const { data: project } = useProject(projectId);
  const {
    data: results,
    isLoading,
    error,
  } = useSearch(projectId, { q: searchQuery, mode: 'keyword', topK: 20 }, !!searchQuery);

  useEffect(() => {
    if (!user) {
      router.push('/login');
    }
  }, [user, router]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (query.trim()) {
      setSearchQuery(query.trim());
      router.push(`/projects/${projectId}/search?q=${encodeURIComponent(query.trim())}`);
    }
  };

  if (!user) return null;

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" asChild>
          <Link href={`/projects/${projectId}`}>
            <ArrowLeft className="h-4 w-4" />
          </Link>
        </Button>
        <div>
          <h1 className="text-3xl font-bold">Search</h1>
          {project && <p className="text-muted-foreground">{project.name}</p>}
        </div>
      </div>

      <form onSubmit={handleSearch}>
        <div className="flex gap-2">
          <div className="relative flex-1">
            <SearchIcon className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              type="search"
              placeholder="Search documents..."
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              className="pl-10"
            />
          </div>
          <Button type="submit" disabled={!query.trim()}>
            Search
          </Button>
        </div>
      </form>

      {searchQuery && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <p className="text-sm text-muted-foreground">
              {isLoading
                ? 'Searching...'
                : results
                  ? `${results.length} results for "${searchQuery}"`
                  : 'No results'}
            </p>
          </div>

          {isLoading ? (
            <div className="flex items-center justify-center py-12">
              <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
            </div>
          ) : error ? (
            <Card>
              <CardContent className="py-8 text-center text-destructive">
                Search failed. Please try again.
              </CardContent>
            </Card>
          ) : results && results.length > 0 ? (
            <div className="space-y-2">
              {results.map((result, idx) => (
                <SearchResultCard key={`${result.documentId}-${idx}`} result={result} />
              ))}
            </div>
          ) : (
            <Card>
              <CardContent className="flex flex-col items-center justify-center py-12">
                <SearchIcon className="h-12 w-12 text-muted-foreground" />
                <h3 className="mt-4 text-lg font-semibold">No results found</h3>
                <p className="mt-2 text-sm text-muted-foreground">
                  Try different keywords or check your spelling
                </p>
              </CardContent>
            </Card>
          )}
        </div>
      )}

      {!searchQuery && (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-12">
            <SearchIcon className="h-12 w-12 text-muted-foreground" />
            <h3 className="mt-4 text-lg font-semibold">Search documents</h3>
            <p className="mt-2 text-sm text-muted-foreground">
              Enter keywords to search across all documents in this project
            </p>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
