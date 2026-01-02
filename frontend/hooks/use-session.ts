'use client';

import { useState, useEffect, useCallback } from 'react';
import type { ChatMessage } from '@/lib/types';

export interface Session {
  id: string;
  title: string;
  messages: ChatMessage[];
  projectId: string;
  createdAt: Date;
  updatedAt: Date;
}

const SESSIONS_STORAGE_KEY = 'docst-chat-sessions';
const MAX_SESSIONS = 50;

/**
 * 세션 관리 Hook
 *
 * - LocalStorage 기반 대화 히스토리 저장
 * - 세션 목록 관리
 * - 세션 공유 URL 생성
 */
export function useSession(projectId: string) {
  const [sessions, setSessions] = useState<Session[]>([]);
  const [currentSessionId, setCurrentSessionId] = useState<string | null>(null);

  // Load sessions from localStorage on mount
  useEffect(() => {
    if (typeof window === 'undefined') return;

    const stored = localStorage.getItem(SESSIONS_STORAGE_KEY);
    if (stored) {
      try {
        const parsed = JSON.parse(stored) as Session[];
        // Filter sessions for current project
        const projectSessions = parsed
          .filter((s) => s.projectId === projectId)
          .map((s) => ({
            ...s,
            createdAt: new Date(s.createdAt),
            updatedAt: new Date(s.updatedAt),
            messages: s.messages.map((m) => ({
              ...m,
              timestamp: new Date(m.timestamp),
            })),
          }));
        setSessions(projectSessions);
      } catch (e) {
        console.error('Failed to load sessions:', e);
      }
    }
  }, [projectId]);

  // Save sessions to localStorage
  const persistSessions = useCallback(
    (newSessions: Session[]) => {
      if (typeof window === 'undefined') return;

      const stored = localStorage.getItem(SESSIONS_STORAGE_KEY);
      let allSessions: Session[] = [];

      if (stored) {
        try {
          allSessions = JSON.parse(stored);
          // Remove sessions for current project
          allSessions = allSessions.filter((s) => s.projectId !== projectId);
        } catch (e) {
          console.error('Failed to parse stored sessions:', e);
        }
      }

      // Add new sessions and limit total
      allSessions = [...allSessions, ...newSessions].slice(-MAX_SESSIONS);
      localStorage.setItem(SESSIONS_STORAGE_KEY, JSON.stringify(allSessions));
    },
    [projectId]
  );

  /**
   * 새 세션 생성
   */
  const saveSession = useCallback(
    (messages: ChatMessage[]): string => {
      const sessionId = generateSessionId();
      const title = messages[0]?.content.slice(0, 50) || 'New Session';

      const newSession: Session = {
        id: sessionId,
        title,
        messages,
        projectId,
        createdAt: new Date(),
        updatedAt: new Date(),
      };

      const newSessions = [...sessions, newSession];
      setSessions(newSessions);
      persistSessions(newSessions);
      setCurrentSessionId(sessionId);

      return sessionId;
    },
    [sessions, projectId, persistSessions]
  );

  /**
   * 기존 세션 업데이트
   */
  const updateSession = useCallback(
    (sessionId: string, messages: ChatMessage[]) => {
      const newSessions = sessions.map((s) =>
        s.id === sessionId ? { ...s, messages, updatedAt: new Date() } : s
      );
      setSessions(newSessions);
      persistSessions(newSessions);
    },
    [sessions, persistSessions]
  );

  /**
   * 세션 로드
   */
  const loadSession = useCallback(
    (sessionId: string): ChatMessage[] | null => {
      const session = sessions.find((s) => s.id === sessionId);
      if (session) {
        setCurrentSessionId(sessionId);
        return session.messages;
      }
      return null;
    },
    [sessions]
  );

  /**
   * 세션 삭제
   */
  const deleteSession = useCallback(
    (sessionId: string) => {
      const newSessions = sessions.filter((s) => s.id !== sessionId);
      setSessions(newSessions);
      persistSessions(newSessions);

      if (currentSessionId === sessionId) {
        setCurrentSessionId(null);
      }
    },
    [sessions, currentSessionId, persistSessions]
  );

  /**
   * 세션 공유 URL 생성 (클립보드에 복사)
   */
  const shareSession = useCallback(
    (sessionId: string): string => {
      if (typeof window === 'undefined') return '';

      const url = `${window.location.origin}/playground?session=${sessionId}`;
      navigator.clipboard.writeText(url);
      return url;
    },
    []
  );

  /**
   * 모든 세션 삭제
   */
  const clearAllSessions = useCallback(() => {
    setSessions([]);
    persistSessions([]);
    setCurrentSessionId(null);
  }, [persistSessions]);

  return {
    sessions,
    currentSessionId,
    saveSession,
    updateSession,
    loadSession,
    deleteSession,
    shareSession,
    clearAllSessions,
  };
}

/**
 * 세션 ID 생성 (UUID v4)
 */
function generateSessionId(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  // Fallback for older browsers
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}
