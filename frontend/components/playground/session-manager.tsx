'use client';

import { History, Trash2, Share2, Plus, MessageSquare } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet';
import { ScrollArea } from '@/components/ui/scroll-area';
import { useSession, type Session } from '@/hooks/use-session';
import { cn } from '@/lib/utils';
import type { ChatMessage } from '@/lib/types';

interface SessionManagerProps {
  projectId: string;
  onLoadSession: (messages: ChatMessage[]) => void;
  onNewSession: () => void;
}

/**
 * 세션 관리 컴포넌트
 *
 * - 대화 히스토리 목록 표시
 * - 세션 로드/삭제/공유
 * - 새 대화 시작
 */
export function SessionManager({
  projectId,
  onLoadSession,
  onNewSession,
}: SessionManagerProps) {
  const {
    sessions,
    currentSessionId,
    loadSession,
    deleteSession,
    shareSession,
  } = useSession(projectId);

  const handleLoadSession = (sessionId: string) => {
    const messages = loadSession(sessionId);
    if (messages) {
      onLoadSession(messages);
    }
  };

  const handleShare = (e: React.MouseEvent, sessionId: string) => {
    e.stopPropagation();
    const url = shareSession(sessionId);
    // TODO: Show a toast notification
    alert(`Link copied to clipboard:\n${url}`);
  };

  const handleDelete = (e: React.MouseEvent, sessionId: string) => {
    e.stopPropagation();
    if (confirm('Delete this session?')) {
      deleteSession(sessionId);
    }
  };

  return (
    <Sheet>
      <SheetTrigger asChild>
        <Button variant="outline" size="sm">
          <History className="h-4 w-4 mr-2" />
          History
          {sessions.length > 0 && (
            <span className="ml-2 text-xs bg-muted px-1.5 py-0.5 rounded-full">
              {sessions.length}
            </span>
          )}
        </Button>
      </SheetTrigger>
      <SheetContent className="w-[400px] sm:w-[540px]">
        <SheetHeader>
          <SheetTitle>Chat History</SheetTitle>
          <SheetDescription>
            View and manage your previous conversations
          </SheetDescription>
        </SheetHeader>

        <div className="mt-6">
          <Button onClick={onNewSession} className="w-full mb-4">
            <Plus className="h-4 w-4 mr-2" />
            New Conversation
          </Button>

          <ScrollArea className="h-[calc(100vh-200px)]">
            <div className="space-y-2">
              {sessions.length === 0 && (
                <div className="text-center text-muted-foreground py-12">
                  <MessageSquare className="h-12 w-12 mx-auto mb-4 opacity-50" />
                  <p className="text-sm">No saved sessions yet</p>
                  <p className="text-xs mt-1">
                    Your conversations will appear here
                  </p>
                </div>
              )}

              {sessions
                .sort((a, b) => b.updatedAt.getTime() - a.updatedAt.getTime())
                .map((session) => (
                  <SessionItem
                    key={session.id}
                    session={session}
                    isActive={session.id === currentSessionId}
                    onLoad={() => handleLoadSession(session.id)}
                    onShare={(e) => handleShare(e, session.id)}
                    onDelete={(e) => handleDelete(e, session.id)}
                  />
                ))}
            </div>
          </ScrollArea>
        </div>
      </SheetContent>
    </Sheet>
  );
}

interface SessionItemProps {
  session: Session;
  isActive: boolean;
  onLoad: () => void;
  onShare: (e: React.MouseEvent) => void;
  onDelete: (e: React.MouseEvent) => void;
}

function SessionItem({
  session,
  isActive,
  onLoad,
  onShare,
  onDelete,
}: SessionItemProps) {
  return (
    <div
      onClick={onLoad}
      className={cn(
        'p-4 rounded-lg border cursor-pointer transition-colors',
        'hover:bg-muted/50',
        isActive && 'border-primary bg-muted/50'
      )}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="flex-1 min-w-0">
          <p className="font-medium truncate text-sm">{session.title}</p>
          <div className="flex items-center gap-3 mt-1 text-xs text-muted-foreground">
            <span>{session.messages.length} messages</span>
            <span>•</span>
            <span title={session.updatedAt.toLocaleString()}>
              {formatRelativeTime(session.updatedAt)}
            </span>
          </div>
        </div>

        <div className="flex gap-1 shrink-0">
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7"
            onClick={onShare}
            title="Share session"
          >
            <Share2 className="h-3.5 w-3.5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7 text-destructive hover:text-destructive"
            onClick={onDelete}
            title="Delete session"
          >
            <Trash2 className="h-3.5 w-3.5" />
          </Button>
        </div>
      </div>

      {/* Preview first message */}
      {session.messages.length > 0 && (
        <div className="mt-2 text-xs text-muted-foreground line-clamp-2">
          {session.messages[0].content}
        </div>
      )}
    </div>
  );
}

/**
 * Format date as relative time
 */
function formatRelativeTime(date: Date): string {
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffSec = Math.floor(diffMs / 1000);
  const diffMin = Math.floor(diffSec / 60);
  const diffHour = Math.floor(diffMin / 60);
  const diffDay = Math.floor(diffHour / 24);

  if (diffSec < 60) return 'Just now';
  if (diffMin < 60) return `${diffMin}m ago`;
  if (diffHour < 24) return `${diffHour}h ago`;
  if (diffDay < 7) return `${diffDay}d ago`;
  if (diffDay < 30) return `${Math.floor(diffDay / 7)}w ago`;
  return date.toLocaleDateString();
}
