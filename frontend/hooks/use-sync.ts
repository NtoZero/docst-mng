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
  const syncEventRef = useRef<SyncEvent | null>(null); // ref로 최신 값 추적
  const isCompletedRef = useRef(false); // 완료 상태 추적

  useEffect(() => {
    optionsRef.current = options;
  }, [options]);

  // syncEvent가 변경될 때 ref도 업데이트
  useEffect(() => {
    syncEventRef.current = syncEvent;
  }, [syncEvent]);

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
      isCompletedRef.current = false;

      try {
        // Start the sync job
        const job = await repositoriesApi.sync(repositoryId, { branch });

        if (job.status === 'FAILED') {
          setError(job.errorMessage || 'Sync failed');
          setIsConnecting(false);
          return;
        }

        // 이미 완료된 상태면 SSE 연결 불필요
        if (job.status === 'SUCCEEDED') {
          setIsConnecting(false);
          setSyncEvent({
            jobId: job.id,
            status: 'SUCCEEDED',
            message: 'Sync completed',
            progress: 100,
            totalDocs: 0,
            processedDocs: 0,
          });
          isCompletedRef.current = true;
          optionsRef.current.onComplete?.(job);
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
            syncEventRef.current = data;

            const status = data.status as SyncStatus;
            if (status === 'SUCCEEDED' || status === 'FAILED') {
              isCompletedRef.current = true;
              eventSource.close();
              eventSourceRef.current = null;
              setIsSyncing(false);

              if (status === 'FAILED') {
                setError(data.message || 'Sync failed');
                optionsRef.current.onError?.(data.message || 'Sync failed');
              } else {
                // DB 트랜잭션 커밋 완료를 보장하기 위해 약간의 지연 후 콜백 호출
                setTimeout(() => {
                  repositoriesApi.getSyncStatus(repositoryId).then((finalJob) => {
                    optionsRef.current.onComplete?.(finalJob);
                  });
                }, 300);
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

          // ref를 사용하여 최신 상태 확인 (stale closure 방지)
          const currentEvent = syncEventRef.current;
          if (
            !isCompletedRef.current &&
            (!currentEvent ||
              (currentEvent.status !== 'SUCCEEDED' && currentEvent.status !== 'FAILED'))
          ) {
            setError('Connection to sync stream lost');
          }
        };
      } catch (e) {
        setIsConnecting(false);
        setError(e instanceof Error ? e.message : 'Failed to start sync');
      }
    },
    [repositoryId, cancelSync]
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
