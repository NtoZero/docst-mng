'use client';

import { useState } from 'react';
import { useSystemCredentials, useDeleteSystemCredential } from '@/hooks/use-admin-config';
import { Button } from '@/components/ui/button';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Badge } from '@/components/ui/badge';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Loader2, Trash2, Plus } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { SystemCredential } from '@/lib/types';

interface CredentialListProps {
  onCreateClick: () => void;
  onEditClick: (credential: SystemCredential) => void;
}

export function CredentialList({ onCreateClick, onEditClick }: CredentialListProps) {
  const t = useTranslations('admin');
  const { data: credentials, isLoading, error } = useSystemCredentials();
  const deleteCredential = useDeleteSystemCredential();
  const [deleteId, setDeleteId] = useState<string | null>(null);

  const handleDelete = async () => {
    if (!deleteId) return;

    try {
      await deleteCredential.mutateAsync(deleteId);
      setDeleteId(null);
    } catch (err) {
      console.error('Failed to delete credential:', err);
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center p-8">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (error) {
    return (
      <Alert variant="destructive">
        <AlertDescription>
          Failed to load credentials: {error.message}
        </AlertDescription>
      </Alert>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-start justify-between">
        <p className="text-sm text-muted-foreground">
          {t('credentials.description')}
        </p>
        <Button onClick={onCreateClick}>
          <Plus className="mr-2 h-4 w-4" />
          {t('credentials.create')}
        </Button>
      </div>

      {credentials && credentials.length > 0 ? (
        <div className="border rounded-lg">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Scope</TableHead>
                <TableHead>Description</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Created</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {credentials.map((credential) => (
                <TableRow key={credential.id}>
                  <TableCell className="font-medium">{credential.name}</TableCell>
                  <TableCell>
                    <Badge variant="outline">{credential.type}</Badge>
                  </TableCell>
                  <TableCell>
                    <Badge>{credential.scope}</Badge>
                  </TableCell>
                  <TableCell className="max-w-xs truncate">
                    {credential.description || '-'}
                  </TableCell>
                  <TableCell>
                    {credential.active ? (
                      <Badge variant="default">Active</Badge>
                    ) : (
                      <Badge variant="secondary">Inactive</Badge>
                    )}
                  </TableCell>
                  <TableCell className="text-muted-foreground text-sm">
                    {new Date(credential.createdAt).toLocaleDateString()}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end gap-2">
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => onEditClick(credential)}
                      >
                        Edit
                      </Button>
                      <Button
                        size="sm"
                        variant="destructive"
                        onClick={() => setDeleteId(credential.id)}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      ) : (
        <div className="flex flex-col items-center justify-center p-8 border rounded-lg border-dashed">
          <p className="text-muted-foreground mb-4">No credentials configured</p>
          <Button onClick={onCreateClick}>
            <Plus className="mr-2 h-4 w-4" />
            Create First Credential
          </Button>
        </div>
      )}

      <AlertDialog open={!!deleteId} onOpenChange={(open) => !open && setDeleteId(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete Credential</AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to delete this credential? This action cannot be
              undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={handleDelete}>
              {deleteCredential.isPending ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                'Delete'
              )}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
