'use client';

import { useState, useCallback, useEffect, useRef } from 'react';
import { repositoriesApi } from '@/lib/api';
import type { SyncEvent, SyncJob, SyncStatus } from '@/lib/types';

interface UseSyncOptions {
  onComplete?: (job: SyncJob) => void;
  onError?: (error: string) => void;
}

interface UseSyncReturn {
  startSync: (branch?: string) => Promise<void>;
  cancelSync: () => void;
  isConnecting: boolean;
  isSyncing: boolean;
  syncEvent: SyncEvent | null;
  error: string | null;
}

export function useSync(repositoryId: string, options: UseSyncOptions = {}): UseSyncReturn {
  const [isConnecting, setIsConnecting] = useState(false);
  const [isSyncing, setIsSyncing] = useState(false);
  const [syncEvent, setSyncEvent] = useState<SyncEvent | null>(null);
  const [error, setError] = useState<string | null>(null);

  const eventSourceRef = useRef<EventSource | null>(null);
  const optionsRef = useRef(options);

  useEffect(() => {
    optionsRef.current = options;
  }, [options]);

  const cancelSync = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }
    setIsSyncing(false);
    setIsConnecting(false);
  }, []);

  const startSync = useCallback(
    async (branch?: string) => {
      // Clean up any existing connection
      cancelSync();

      setError(null);
      setIsConnecting(true);
      setSyncEvent(null);

      try {
        // Start the sync job
        const job = await repositoriesApi.sync(repositoryId, { branch });

        if (job.status === 'FAILED') {
          setError(job.errorMessage || 'Sync failed');
          setIsConnecting(false);
          return;
        }

        // Connect to SSE stream
        const streamUrl = repositoriesApi.getSyncStreamUrl(repositoryId);

        // Get auth token for SSE connection
        const stored = localStorage.getItem('docst-auth');
        const token = stored ? JSON.parse(stored)?.state?.token : null;

        const urlWithAuth = token ? `${streamUrl}?token=${encodeURIComponent(token)}` : streamUrl;

        const eventSource = new EventSource(urlWithAuth);
        eventSourceRef.current = eventSource;

        eventSource.onopen = () => {
          setIsConnecting(false);
          setIsSyncing(true);
        };

        eventSource.onmessage = (event) => {
          try {
            const data: SyncEvent = JSON.parse(event.data);
            setSyncEvent(data);

            const status = data.status as SyncStatus;
            if (status === 'SUCCEEDED' || status === 'FAILED') {
              eventSource.close();
              eventSourceRef.current = null;
              setIsSyncing(false);

              if (status === 'FAILED') {
                setError(data.message || 'Sync failed');
                optionsRef.current.onError?.(data.message || 'Sync failed');
              } else {
                // Fetch the final job status
                repositoriesApi.getSyncStatus(repositoryId).then((finalJob) => {
                  optionsRef.current.onComplete?.(finalJob);
                });
              }
            }
          } catch (e) {
            console.error('Failed to parse SSE event:', e);
          }
        };

        eventSource.onerror = () => {
          eventSource.close();
          eventSourceRef.current = null;
          setIsSyncing(false);
          setIsConnecting(false);

          // Check if we've received a success event already
          if (!syncEvent || (syncEvent.status !== 'SUCCEEDED' && syncEvent.status !== 'FAILED')) {
            setError('Connection to sync stream lost');
          }
        };
      } catch (e) {
        setIsConnecting(false);
        setError(e instanceof Error ? e.message : 'Failed to start sync');
      }
    },
    [repositoryId, cancelSync, syncEvent]
  );

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
      }
    };
  }, []);

  return {
    startSync,
    cancelSync,
    isConnecting,
    isSyncing,
    syncEvent,
    error,
  };
}
