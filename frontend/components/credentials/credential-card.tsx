'use client';

import { useState } from 'react';
import { Key, Edit2, Trash2, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { useDeleteUnifiedCredential } from '@/hooks/use-unified-credentials';
import { getCredentialTypeLabel } from './credential-type-config';
import type { CredentialScope, UnifiedCredential } from '@/lib/types';

interface CredentialCardProps {
  credential: UnifiedCredential;
  scope: CredentialScope;
  projectId?: string;
  onEdit: (credential: UnifiedCredential) => void;
}

export function CredentialCard({
  credential,
  scope,
  projectId,
  onEdit,
}: CredentialCardProps) {
  const deleteCredential = useDeleteUnifiedCredential();
  const [showConfirmDelete, setShowConfirmDelete] = useState(false);

  const handleDelete = async () => {
    await deleteCredential.mutateAsync({
      scope,
      id: credential.id,
      projectId,
    });
    setShowConfirmDelete(false);
  };

  return (
    <Card>
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-2">
            <Key className="h-5 w-5 text-muted-foreground" />
            <div>
              <CardTitle className="text-base">{credential.name}</CardTitle>
              <CardDescription className="text-xs">
                Created {new Date(credential.createdAt).toLocaleDateString()}
              </CardDescription>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <Badge variant="secondary">
              {getCredentialTypeLabel(credential.type)}
            </Badge>
            {!credential.active && <Badge variant="destructive">Inactive</Badge>}
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        {credential.description && (
          <p className="text-sm text-muted-foreground">{credential.description}</p>
        )}
        {credential.username && (
          <p className="text-sm">
            <span className="text-muted-foreground">Username:</span>{' '}
            {credential.username}
          </p>
        )}

        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => onEdit(credential)}>
            <Edit2 className="mr-2 h-4 w-4" />
            Edit
          </Button>
          {showConfirmDelete ? (
            <>
              <Button
                variant="destructive"
                size="sm"
                onClick={handleDelete}
                disabled={deleteCredential.isPending}
              >
                {deleteCredential.isPending ? (
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                ) : (
                  <Trash2 className="mr-2 h-4 w-4" />
                )}
                Confirm
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setShowConfirmDelete(false)}
              >
                Cancel
              </Button>
            </>
          ) : (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setShowConfirmDelete(true)}
            >
              <Trash2 className="mr-2 h-4 w-4" />
              Delete
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
