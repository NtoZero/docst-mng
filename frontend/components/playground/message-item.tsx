'use client';

import { User, Bot, AlertCircle, Code } from 'lucide-react';
import type { ChatMessage } from '@/lib/types';
import { cn } from '@/lib/utils';

interface MessageItemProps {
  message: ChatMessage;
}

function formatTime(date: Date): string {
  const hours = date.getHours().toString().padStart(2, '0');
  const minutes = date.getMinutes().toString().padStart(2, '0');
  const seconds = date.getSeconds().toString().padStart(2, '0');
  return `${hours}:${minutes}:${seconds}`;
}

export function MessageItem({ message }: MessageItemProps) {
  const isUser = message.role === 'user';
  const isError = message.isError;

  return (
    <div
      className={cn(
        'flex gap-3 p-4',
        isUser && 'bg-muted/50',
        isError && 'bg-destructive/10'
      )}
    >
      {/* Avatar */}
      <div
        className={cn(
          'flex h-8 w-8 shrink-0 items-center justify-center rounded-full',
          isUser ? 'bg-primary' : 'bg-secondary'
        )}
      >
        {isUser ? (
          <User className="h-4 w-4 text-primary-foreground" />
        ) : (
          <Bot className="h-4 w-4 text-secondary-foreground" />
        )}
      </div>

      {/* Content */}
      <div className="flex-1 space-y-2">
        {/* Header */}
        <div className="flex items-center gap-2">
          <span className="text-sm font-semibold">
            {isUser ? 'You' : 'Assistant'}
          </span>
          <span className="text-xs text-muted-foreground">
            {formatTime(message.timestamp)}
          </span>
          {isError && (
            <AlertCircle className="h-4 w-4 text-destructive" />
          )}
        </div>

        {/* Message Content */}
        <div className="text-sm whitespace-pre-wrap">{message.content}</div>

        {/* Tool Calls */}
        {message.toolCalls && message.toolCalls.length > 0 && (
          <div className="space-y-2 mt-3">
            {message.toolCalls.map((toolCall, index) => (
              <div
                key={index}
                className="border rounded-lg p-3 bg-card text-card-foreground"
              >
                <div className="flex items-center gap-2 mb-2">
                  <Code className="h-4 w-4 text-muted-foreground" />
                  <span className="text-sm font-medium">
                    {toolCall.toolName}
                  </span>
                  {toolCall.duration && (
                    <span className="text-xs text-muted-foreground">
                      ({toolCall.duration}ms)
                    </span>
                  )}
                </div>

                {/* Input */}
                <div className="text-xs mb-2">
                  <div className="text-muted-foreground mb-1">Input:</div>
                  <pre className="bg-muted p-2 rounded overflow-x-auto">
                    {JSON.stringify(toolCall.input, null, 2)}
                  </pre>
                </div>

                {/* Output or Error */}
                {toolCall.error ? (
                  <div className="text-xs">
                    <div className="text-destructive mb-1">Error:</div>
                    <pre className="bg-destructive/10 p-2 rounded text-destructive">
                      {toolCall.error}
                    </pre>
                  </div>
                ) : toolCall.output ? (
                  <div className="text-xs">
                    <div className="text-muted-foreground mb-1">Output:</div>
                    <pre className="bg-muted p-2 rounded overflow-x-auto">
                      {JSON.stringify(toolCall.output, null, 2)}
                    </pre>
                  </div>
                ) : null}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
