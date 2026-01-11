'use client';

import { useState, useCallback, useEffect } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import { useParams } from 'next/navigation';
import { Plus, KeyRound } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useAuthStore } from '@/lib/store';
import {
  CredentialScopeTabs,
  ProjectSelector,
  CredentialFormDialog,
  CredentialListView,
} from '@/components/credentials';
import type { CredentialScope, UnifiedCredential } from '@/lib/types';

export default function UnifiedCredentialsPage() {
  const router = useRouter();
  const params = useParams();
  const searchParams = useSearchParams();
  const locale = params.locale as string;
  const user = useAuthStore((state) => state.user);

  // Parse URL parameters
  const scopeParam = searchParams.get('scope')?.toUpperCase() as CredentialScope | undefined;
  const projectIdParam = searchParams.get('projectId') || undefined;

  // State
  const [scope, setScope] = useState<CredentialScope>(scopeParam || 'USER');
  const [projectId, setProjectId] = useState<string | undefined>(projectIdParam);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingCredential, setEditingCredential] = useState<UnifiedCredential | null>(null);

  // TODO: Implement proper admin check based on user role from backend
  // Currently showing SYSTEM tab to all users, but backend will reject non-admin requests
  const isAdmin = true;

  // Sync scope from URL on mount
  useEffect(() => {
    if (scopeParam && ['USER', 'SYSTEM', 'PROJECT'].includes(scopeParam)) {
      setScope(scopeParam);
    }
    if (projectIdParam) {
      setProjectId(projectIdParam);
    }
  }, [scopeParam, projectIdParam]);

  // Update URL when scope changes
  const handleScopeChange = useCallback(
    (newScope: CredentialScope) => {
      setScope(newScope);
      const params = new URLSearchParams();
      params.set('scope', newScope.toLowerCase());
      if (newScope === 'PROJECT' && projectId) {
        params.set('projectId', projectId);
      }
      router.push(`/${locale}/settings/credentials?${params.toString()}`);
    },
    [router, locale, projectId]
  );

  // Update URL when project changes
  const handleProjectChange = useCallback(
    (newProjectId: string) => {
      setProjectId(newProjectId);
      const params = new URLSearchParams();
      params.set('scope', 'project');
      params.set('projectId', newProjectId);
      router.push(`/${locale}/settings/credentials?${params.toString()}`);
    },
    [router, locale]
  );

  // Handle create button click
  const handleCreateClick = useCallback(() => {
    setEditingCredential(null);
    setDialogOpen(true);
  }, []);

  // Handle edit button click
  const handleEditClick = useCallback((credential: UnifiedCredential) => {
    setEditingCredential(credential);
    setDialogOpen(true);
  }, []);

  // Handle dialog close
  const handleDialogClose = useCallback((open: boolean) => {
    setDialogOpen(open);
    if (!open) {
      setEditingCredential(null);
    }
  }, []);

  // Redirect to login if not authenticated
  useEffect(() => {
    if (!user) {
      router.push(`/${locale}/login`);
    }
  }, [user, router, locale]);

  if (!user) {
    return null;
  }

  // Check if PROJECT scope is ready (has projectId)
  const isProjectScopeReady = scope !== 'PROJECT' || !!projectId;

  return (
    <div className="container mx-auto py-8 space-y-6 max-w-6xl">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div className="flex items-center gap-3">
          <div className="rounded-lg bg-primary/10 p-2">
            <KeyRound className="h-6 w-6 text-primary" />
          </div>
          <div>
            <h1 className="text-3xl font-bold">Credentials</h1>
            <p className="text-muted-foreground mt-1">
              Manage authentication credentials for services and repositories
            </p>
          </div>
        </div>
        {isProjectScopeReady && (
          <Button onClick={handleCreateClick}>
            <Plus className="mr-2 h-4 w-4" />
            Add Credential
          </Button>
        )}
      </div>

      {/* Scope Tabs */}
      <div className="flex items-center justify-between gap-4">
        <CredentialScopeTabs
          activeScope={scope}
          onScopeChange={handleScopeChange}
          isAdmin={isAdmin}
        />
      </div>

      {/* Project Selector (only for PROJECT scope) */}
      {scope === 'PROJECT' && (
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium">Project:</span>
          <ProjectSelector
            selectedProjectId={projectId}
            onProjectChange={handleProjectChange}
          />
        </div>
      )}

      {/* Credential List */}
      <CredentialListView
        scope={scope}
        projectId={projectId}
        onEdit={handleEditClick}
        onCreateClick={handleCreateClick}
      />

      {/* Form Dialog */}
      <CredentialFormDialog
        open={dialogOpen}
        onOpenChange={handleDialogClose}
        scope={scope}
        projectId={projectId}
        credential={editingCredential}
      />
    </div>
  );
}
