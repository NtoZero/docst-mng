'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Loader2, Search, FileText, MapPin } from 'lucide-react';
import type { SearchParams, SearchResult, SearchResponse } from '@/lib/types';
import { getAuthToken } from '@/lib/auth-utils';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8342';

interface SearchPreviewProps {
  projectId: string | null;
  params: SearchParams;
}

function useDebounce<T>(value: T, delay: number): T {
  const [debouncedValue, setDebouncedValue] = useState<T>(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedValue(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);

  return debouncedValue;
}

export function SearchPreview({ projectId, params }: SearchPreviewProps) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<SearchResult[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [queryTimeMs, setQueryTimeMs] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const debouncedQuery = useDebounce(query, 300);
  const abortControllerRef = useRef<AbortController | null>(null);

  const executeSearch = useCallback(async () => {
    if (!debouncedQuery.trim() || !projectId) {
      setResults([]);
      setQueryTimeMs(null);
      return;
    }

    // Cancel previous request
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
    abortControllerRef.current = new AbortController();

    setIsLoading(true);
    setError(null);

    try {
      const searchParams = new URLSearchParams({
        q: debouncedQuery,
        mode: params.mode,
        topK: params.topK.toString(),
      });

      if (params.mode !== 'keyword') {
        searchParams.set('similarityThreshold', params.similarityThreshold.toString());
      }

      if (params.mode === 'hybrid') {
        searchParams.set('fusionStrategy', params.fusionStrategy);
        if (params.fusionStrategy === 'rrf') {
          searchParams.set('rrfK', params.rrfK.toString());
        } else {
          searchParams.set('vectorWeight', params.vectorWeight.toString());
        }
      }

      const token = getAuthToken();
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
      };
      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }

      const response = await fetch(
        `${API_BASE}/api/projects/${projectId}/search?${searchParams}`,
        {
          signal: abortControllerRef.current.signal,
          headers,
          credentials: 'include',
        }
      );

      if (!response.ok) {
        const errorText = await response.text();
        console.error('Search failed:', response.status, errorText);
        throw new Error(`Search failed: ${response.status}`);
      }

      const data: SearchResponse = await response.json();
      setResults(data.results || []);
      setQueryTimeMs(data.metadata?.queryTimeMs || null);
    } catch (err) {
      if (err instanceof Error && err.name === 'AbortError') {
        return; // Ignore abort errors
      }
      console.error('Search preview failed:', err);
      setError('Search failed');
      setResults([]);
    } finally {
      setIsLoading(false);
    }
  }, [debouncedQuery, projectId, params]);

  useEffect(() => {
    executeSearch();
  }, [executeSearch]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, []);

  if (!projectId) {
    return (
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-sm flex items-center gap-2">
            <Search className="h-4 w-4" />
            Search Preview
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-xs text-muted-foreground text-center py-4">
            Select a project first
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-sm flex items-center gap-2">
          <Search className="h-4 w-4" />
          Search Preview
          {queryTimeMs !== null && (
            <Badge variant="secondary" className="text-xs ml-auto">
              {queryTimeMs}ms
            </Badge>
          )}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <Input
          placeholder="Test your search..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className="h-8"
        />

        {isLoading ? (
          <div className="flex items-center justify-center py-4">
            <Loader2 className="h-4 w-4 animate-spin" />
          </div>
        ) : error ? (
          <p className="text-xs text-destructive text-center py-4">
            {error}
          </p>
        ) : results.length > 0 ? (
          <div className="space-y-2 max-h-64 overflow-y-auto">
            {results.slice(0, 5).map((result, idx) => (
              <SearchResultItem key={`${result.documentId}-${idx}`} result={result} />
            ))}
            {results.length > 5 && (
              <p className="text-xs text-muted-foreground text-center">
                +{results.length - 5} more
              </p>
            )}
          </div>
        ) : query ? (
          <p className="text-xs text-muted-foreground text-center py-4">
            No results
          </p>
        ) : (
          <p className="text-xs text-muted-foreground text-center py-4">
            Enter a query to test
          </p>
        )}
      </CardContent>
    </Card>
  );
}

function SearchResultItem({ result }: { result: SearchResult }) {
  const scorePercent = Math.round(result.score * 100);

  return (
    <div className="border rounded-md p-2 space-y-1 text-xs">
      <div className="flex items-center gap-1 text-muted-foreground">
        <FileText className="h-3 w-3" />
        <span className="truncate flex-1">{result.path}</span>
        <Badge
          variant={scorePercent >= 70 ? 'default' : 'secondary'}
          className="text-xs"
        >
          {scorePercent}%
        </Badge>
      </div>
      {result.headingPath && (
        <div className="flex items-center gap-1 text-blue-600">
          <MapPin className="h-3 w-3" />
          <span className="truncate">{result.headingPath}</span>
        </div>
      )}
      <p
        className="text-muted-foreground line-clamp-2"
        dangerouslySetInnerHTML={{ __html: result.highlightedSnippet || result.snippet }}
      />
    </div>
  );
}
