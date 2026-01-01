'use client';

import { ChatInterface } from '@/components/playground/chat-interface';
import { useMcpTools } from '@/hooks/use-mcp-tools';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Trash2, Play } from 'lucide-react';

export default function PlaygroundPage() {
  const { messages, sendMessage, isLoading, clearMessages } = useMcpTools();

  return (
    <div className="container mx-auto p-6 h-[calc(100vh-4rem)]">
      <div className="flex flex-col h-full gap-6">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold">MCP Playground</h1>
            <p className="text-muted-foreground mt-1">
              Test MCP tools and explore document operations
            </p>
          </div>
          <div className="flex items-center gap-2">
            <Badge variant="outline" className="text-sm">
              Phase 5 MVP
            </Badge>
            <Button
              variant="outline"
              size="sm"
              onClick={clearMessages}
              disabled={messages.length === 0}
            >
              <Trash2 className="h-4 w-4 mr-2" />
              Clear
            </Button>
          </div>
        </div>

        {/* Main Content */}
        <div className="flex-1 flex gap-6 min-h-0">
          {/* Chat Area */}
          <Card className="flex-1 flex flex-col min-h-0">
            <CardHeader className="border-b">
              <CardTitle>Chat</CardTitle>
              <CardDescription>
                Try commands like &quot;ping&quot; or &quot;list tools&quot;
              </CardDescription>
            </CardHeader>
            <CardContent className="flex-1 p-0 flex flex-col min-h-0">
              <ChatInterface
                messages={messages}
                onSendMessage={sendMessage}
                isLoading={isLoading}
              />
            </CardContent>
          </Card>

          {/* Sidebar */}
          <Card className="w-80 flex flex-col">
            <CardHeader className="border-b">
              <CardTitle>Quick Commands</CardTitle>
              <CardDescription>Click to try</CardDescription>
            </CardHeader>
            <CardContent className="flex-1 p-4">
              <div className="space-y-2">
                <Button
                  variant="outline"
                  className="w-full justify-start"
                  onClick={() => sendMessage('ping')}
                  disabled={isLoading}
                >
                  <Play className="h-4 w-4 mr-2" />
                  Ping Server
                </Button>
                <Button
                  variant="outline"
                  className="w-full justify-start"
                  onClick={() => sendMessage('list tools')}
                  disabled={isLoading}
                >
                  <Play className="h-4 w-4 mr-2" />
                  List Tools
                </Button>
              </div>

              <div className="mt-6 p-4 bg-muted rounded-lg text-sm">
                <p className="font-semibold mb-2">Available Commands:</p>
                <ul className="space-y-1 text-muted-foreground">
                  <li>• ping</li>
                  <li>• list tools</li>
                </ul>
                <p className="mt-4 text-xs text-muted-foreground">
                  Full LLM integration coming in Phase 6
                </p>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
