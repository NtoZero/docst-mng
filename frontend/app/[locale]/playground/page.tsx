'use client';

import { useState } from 'react';
import { useParams, useSearchParams } from 'next/navigation';
import { ChatInterface } from '@/components/playground/chat-interface';
import { BranchSelector } from '@/components/playground/branch-selector';
import { SessionManager } from '@/components/playground/session-manager';
import { ProjectSelector } from '@/components/playground/project-selector';
import { SearchParamsPanel, DEFAULT_SEARCH_PARAMS } from '@/components/playground/search-params-panel';
import { SearchPreview } from '@/components/playground/search-preview';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import type { ChatMessage, SearchParams } from '@/lib/types';

export default function PlaygroundPage() {
  const params = useParams();
  const searchParams = useSearchParams();
  const urlProjectId = params.projectId as string | undefined;
  const repositoryId = searchParams?.get('repositoryId') as string | undefined;

  // State for selected project (from URL or user selection)
  const [selectedProjectId, setSelectedProjectId] = useState<string | undefined>(urlProjectId);
  const [chatKey, setChatKey] = useState(0);
  // Phase 14: Search parameters state
  const [searchParamsState, setSearchParamsState] = useState<SearchParams>(DEFAULT_SEARCH_PARAMS);

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

        {/* Main Content - 3 Column Layout */}
        <div className="flex-1 flex gap-4 min-h-0">
          {/* Left Sidebar - Search Parameters (Phase 14) */}
          <aside className="w-64 flex-shrink-0 overflow-y-auto">
            <SearchParamsPanel
              params={searchParamsState}
              onChange={setSearchParamsState}
            />
          </aside>

          {/* Chat Area */}
          {projectId ? (
            <Card className="flex-1 flex flex-col min-h-0">
              <CardContent className="flex-1 p-0 flex flex-col min-h-0">
                <ChatInterface key={chatKey} projectId={projectId} searchParams={searchParamsState} />
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

          {/* Right Sidebar - Quick Tips + Search Preview */}
          <aside className="w-72 flex flex-col gap-4 flex-shrink-0 overflow-y-auto">
            {/* Quick Tips */}
            <Card className="flex flex-col">
              <CardHeader className="border-b pb-3">
                <CardTitle className="text-sm">Quick Tips</CardTitle>
                <CardDescription className="text-xs">How to use the AI assistant</CardDescription>
              </CardHeader>
              <CardContent className="p-3">
                <div className="space-y-3">
                  <div className="p-3 bg-muted rounded-lg text-xs">
                    <p className="font-semibold mb-1">Example Questions:</p>
                    <ul className="space-y-1 text-muted-foreground">
                      <li>• Find all README files</li>
                      <li>• Search for authentication docs</li>
                      <li>• List all documents in project</li>
                    </ul>
                  </div>

                  <div className="p-3 bg-muted rounded-lg text-xs">
                    <p className="font-semibold mb-1">Available Tools:</p>
                    <div className="space-y-1">
                      <p className="text-muted-foreground">
                        searchDocuments, listDocuments, getDocument, updateDocument, createDocument
                      </p>
                    </div>
                  </div>
                </div>
              </CardContent>
            </Card>

            {/* Search Preview (Phase 14) */}
            <SearchPreview
              projectId={projectId ?? null}
              params={searchParamsState}
            />
          </aside>
        </div>
      </div>
    </div>
  );
}
