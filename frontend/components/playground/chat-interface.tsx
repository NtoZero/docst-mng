'use client';

import { MessageList } from './message-list';
import { MessageInput } from './message-input';
import type { ChatMessage } from '@/lib/types';

interface ChatInterfaceProps {
  messages: ChatMessage[];
  onSendMessage: (content: string) => void;
  isLoading: boolean;
}

export function ChatInterface({
  messages,
  onSendMessage,
  isLoading,
}: ChatInterfaceProps) {
  return (
    <div className="flex-1 flex flex-col h-full">
      <MessageList messages={messages} />
      <MessageInput
        onSend={onSendMessage}
        disabled={isLoading}
        placeholder={
          isLoading
            ? 'Processing...'
            : 'Ask me to search documents, create/update files, etc.'
        }
      />
    </div>
  );
}
