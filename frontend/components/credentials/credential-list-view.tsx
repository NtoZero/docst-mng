'use client';

import { Key, Loader2, Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { useUnifiedCredentials } from '@/hooks/use-unified-credentials';
import { CredentialCard } from './credential-card';
import { CredentialTable } from './credential-table';
import type { CredentialScope, UnifiedCredential } from '@/lib/types';

interface CredentialListViewProps {
  scope: CredentialScope;
  projectId?: string;
  onEdit: (credential: UnifiedCredential) => void;
  onCreateClick: () => void;
}

export function CredentialListView({
  scope,
  projectId,
  onEdit,
  onCreateClick,
}: CredentialListViewProps) {
  const { data: credentials, isLoading, error } = useUnifiedCredentials(scope, projectId);

  // Loading state
  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  // Error state
  if (error) {
    return (
      <Card>
        <CardContent className="flex flex-col items-center justify-center py-12">
          <p className="text-destructive">Failed to load credentials</p>
          <p className="mt-2 text-sm text-muted-foreground">{error.message}</p>
        </CardContent>
      </Card>
    );
  }

  // Empty state
  if (!credentials || credentials.length === 0) {
    return (
      <Card>
        <CardContent className="flex flex-col items-center justify-center py-12">
          <Key className="h-12 w-12 text-muted-foreground" />
          <h3 className="mt-4 text-lg font-semibold">No credentials yet</h3>
          <p className="mt-2 text-center text-sm text-muted-foreground">
            {scope === 'USER' && 'Add credentials to authenticate with private Git repositories'}
            {scope === 'SYSTEM' && 'Add system-wide credentials for LLM and database services'}
            {scope === 'PROJECT' && 'Add project-specific credentials for this project'}
          </p>
          <Button onClick={onCreateClick} className="mt-6">
            <Plus className="mr-2 h-4 w-4" />
            Add your first credential
          </Button>
        </CardContent>
      </Card>
    );
  }

  // USER scope uses card view
  if (scope === 'USER') {
    return (
      <div className="grid gap-4 md:grid-cols-2">
        {credentials.map((credential) => (
          <CredentialCard
            key={credential.id}
            credential={credential}
            scope={scope}
            projectId={projectId}
            onEdit={onEdit}
          />
        ))}
      </div>
    );
  }

  // SYSTEM and PROJECT scopes use table view
  return (
    <CredentialTable
      credentials={credentials}
      scope={scope}
      projectId={projectId}
      onEdit={onEdit}
    />
  );
}
