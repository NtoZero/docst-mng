'use client';

import { useState } from 'react';
import { useParams, useSearchParams } from 'next/navigation';
import { ChatInterface } from '@/components/playground/chat-interface';
import { BranchSelector } from '@/components/playground/branch-selector';
import { SessionManager } from '@/components/playground/session-manager';
import { ProjectSelector } from '@/components/playground/project-selector';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import type { ChatMessage } from '@/lib/types';

export default function PlaygroundPage() {
  const params = useParams();
  const searchParams = useSearchParams();
  const urlProjectId = params.projectId as string | undefined;
  const repositoryId = searchParams?.get('repositoryId') as string | undefined;

  // State for selected project (from URL or user selection)
  const [selectedProjectId, setSelectedProjectId] = useState<string | undefined>(urlProjectId);
  const [chatKey, setChatKey] = useState(0);

  // Use selected project or fallback to URL project
  const projectId = selectedProjectId || urlProjectId;

  const handleNewSession = () => {
    setChatKey((prev) => prev + 1);
  };

  const handleLoadSession = (messages: ChatMessage[]) => {
    // TODO: Implement session loading into ChatInterface
    console.log('Loading session with messages:', messages);
    // For now, just reset the chat
    setChatKey((prev) => prev + 1);
  };

  const handleProjectChange = (newProjectId: string) => {
    setSelectedProjectId(newProjectId);
    // Reset chat when project changes
    setChatKey((prev) => prev + 1);
  };

  return (
    <div className="container mx-auto p-6 h-[calc(100vh-4rem)]">
      <div className="flex flex-col h-full gap-6">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold">AI Playground</h1>
            <p className="text-muted-foreground mt-1">
              Chat with AI to search, read, and analyze your documents
            </p>
          </div>
          <div className="flex items-center gap-2">
            {repositoryId && (
              <BranchSelector
                repositoryId={repositoryId}
                onBranchChange={(branch) => console.log('Switched to branch:', branch)}
              />
            )}
            {projectId && (
              <SessionManager
                projectId={projectId}
                onLoadSession={handleLoadSession}
                onNewSession={handleNewSession}
              />
            )}
          </div>
        </div>

        {/* Project Selector */}
        <div className="flex items-center gap-4 p-4 bg-muted/50 rounded-lg">
          <span className="text-sm font-medium">Select Project:</span>
          <ProjectSelector
            selectedProjectId={projectId}
            onProjectChange={handleProjectChange}
          />
        </div>

        {/* Main Content */}
        <div className="flex-1 flex gap-6 min-h-0">
          {/* Chat Area */}
          {projectId ? (
            <Card className="flex-1 flex flex-col min-h-0">
              <CardContent className="flex-1 p-0 flex flex-col min-h-0">
                <ChatInterface key={chatKey} projectId={projectId} />
              </CardContent>
            </Card>
          ) : (
            <Card className="flex-1 flex flex-col items-center justify-center">
              <CardContent className="text-center p-8">
                <h3 className="text-xl font-semibold mb-2">No Project Selected</h3>
                <p className="text-muted-foreground">
                  Please select a project from the dropdown above to start chatting with AI.
                </p>
              </CardContent>
            </Card>
          )}

          {/* Sidebar */}
          <Card className="w-80 flex flex-col">
            <CardHeader className="border-b">
              <CardTitle>Quick Tips</CardTitle>
              <CardDescription>How to use the AI assistant</CardDescription>
            </CardHeader>
            <CardContent className="flex-1 p-4">
              <div className="space-y-4">
                <div className="p-4 bg-muted rounded-lg text-sm">
                  <p className="font-semibold mb-2">Example Questions:</p>
                  <ul className="space-y-2 text-muted-foreground">
                    <li>• Find all README files</li>
                    <li>• Search for authentication documentation</li>
                    <li>• List all documents in this project</li>
                    <li>• What does the API.md file say?</li>
                  </ul>
                </div>

                <div className="p-4 bg-muted rounded-lg text-sm">
                  <p className="font-semibold mb-2">Available Tools:</p>
                  <div className="space-y-2">
                    <div>
                      <p className="text-xs font-medium text-muted-foreground mb-1">Documents:</p>
                      <ul className="space-y-1 text-muted-foreground text-xs pl-2">
                        <li>• searchDocuments</li>
                        <li>• listDocuments</li>
                        <li>• getDocument</li>
                        <li>• updateDocument</li>
                        <li>• createDocument</li>
                      </ul>
                    </div>
                    <div>
                      <p className="text-xs font-medium text-muted-foreground mb-1">Git:</p>
                      <ul className="space-y-1 text-muted-foreground text-xs pl-2">
                        <li>• listBranches</li>
                        <li>• createBranch</li>
                        <li>• switchBranch</li>
                        <li>• getCurrentBranch</li>
                        <li>• syncRepository</li>
                      </ul>
                    </div>
                  </div>
                </div>

                <div className="p-4 bg-blue-50 dark:bg-blue-950 rounded-lg text-sm">
                  <p className="font-semibold mb-1 text-blue-900 dark:text-blue-100">
                    Powered by Spring AI
                  </p>
                  <p className="text-xs text-blue-700 dark:text-blue-300">
                    Using OpenAI GPT-4o with real-time streaming
                  </p>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
