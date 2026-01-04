'use client';

import { useApiKeys, useRevokeApiKey } from '@/hooks/use-api';
import { Button } from '@/components/ui/button';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
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
import { Key, Trash2, Plus, Loader2 } from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';
import { useState } from 'react';
import { toast } from 'sonner';

interface ApiKeyListProps {
  onCreateClick: () => void;
}

export function ApiKeyList({ onCreateClick }: ApiKeyListProps) {
  const { data: apiKeys, isLoading } = useApiKeys();
  const revokeApiKey = useRevokeApiKey();
  const [revokeId, setRevokeId] = useState<string | null>(null);

  const handleRevoke = async () => {
    if (revokeId) {
      try {
        await revokeApiKey.mutateAsync(revokeId);
        toast.success('API key revoked successfully');
        setRevokeId(null);
      } catch (error: any) {
        const errorMessage = error?.message || 'Failed to revoke API key';
        toast.error(errorMessage);
      }
    }
  };

  if (isLoading) {
    return (
      <Card>
        <CardContent className="flex items-center justify-center py-12">
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
        </CardContent>
      </Card>
    );
  }

  return (
    <>
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle className="flex items-center gap-2">
                <Key className="h-5 w-5" />
                API Keys
              </CardTitle>
              <CardDescription className="mt-2">
                Manage API keys for MCP client authentication (Claude Desktop, Claude Code)
              </CardDescription>
            </div>
            <Button onClick={onCreateClick}>
              <Plus className="mr-2 h-4 w-4" />
              Create New Key
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          {!apiKeys || apiKeys.length === 0 ? (
            <div className="text-center py-12">
              <Key className="h-12 w-12 mx-auto text-muted-foreground/50 mb-4" />
              <h3 className="text-lg font-semibold mb-2">No API keys yet</h3>
              <p className="text-muted-foreground mb-6">
                Create an API key to authenticate MCP clients
              </p>
              <Button onClick={onCreateClick}>
                <Plus className="mr-2 h-4 w-4" />
                Create Your First API Key
              </Button>
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Key Prefix</TableHead>
                  <TableHead>Last Used</TableHead>
                  <TableHead>Expires</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {apiKeys.map((key) => (
                  <TableRow key={key.id}>
                    <TableCell className="font-medium">{key.name}</TableCell>
                    <TableCell>
                      <code className="text-xs bg-muted px-2 py-1 rounded">{key.keyPrefix}</code>
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {key.lastUsedAt
                        ? formatDistanceToNow(new Date(key.lastUsedAt), { addSuffix: true })
                        : 'Never used'}
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {key.expiresAt
                        ? formatDistanceToNow(new Date(key.expiresAt), { addSuffix: true })
                        : 'Never'}
                    </TableCell>
                    <TableCell>
                      {key.active ? (
                        <Badge variant="default" className="bg-green-500">Active</Badge>
                      ) : (
                        <Badge variant="destructive">Revoked</Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-right">
                      {key.active && (
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => setRevokeId(key.id)}
                        >
                          <Trash2 className="h-4 w-4 text-destructive" />
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <AlertDialog open={!!revokeId} onOpenChange={(open) => !open && setRevokeId(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Revoke API Key?</AlertDialogTitle>
            <AlertDialogDescription>
              This action cannot be undone. The API key will be permanently revoked and
              can no longer be used for authentication.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleRevoke}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              {revokeApiKey.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Revoke
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
