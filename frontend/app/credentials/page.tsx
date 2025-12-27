'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Key, Plus, Trash2, Edit2, Eye, EyeOff, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Badge } from '@/components/ui/badge';
import {
  useCredentials,
  useCreateCredential,
  useUpdateCredential,
  useDeleteCredential,
} from '@/hooks/use-api';
import { useAuthStore } from '@/lib/store';
import type { Credential, CredentialType } from '@/lib/types';

function getCredentialTypeBadge(type: CredentialType) {
  switch (type) {
    case 'GITHUB_PAT':
      return <Badge variant="default">GitHub PAT</Badge>;
    case 'BASIC_AUTH':
      return <Badge variant="secondary">Basic Auth</Badge>;
    case 'SSH_KEY':
      return <Badge variant="outline">SSH Key</Badge>;
    default:
      return <Badge variant="secondary">{type}</Badge>;
  }
}

function CredentialForm({
  credential,
  onClose,
}: {
  credential?: Credential;
  onClose: () => void;
}) {
  const [name, setName] = useState(credential?.name || '');
  const [type, setType] = useState<CredentialType>(credential?.type || 'GITHUB_PAT');
  const [username, setUsername] = useState(credential?.username || '');
  const [secret, setSecret] = useState('');
  const [description, setDescription] = useState(credential?.description || '');
  const [showSecret, setShowSecret] = useState(false);

  const createCredential = useCreateCredential();
  const updateCredential = useUpdateCredential();

  const isEdit = !!credential;
  const isPending = createCredential.isPending || updateCredential.isPending;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (isEdit) {
      await updateCredential.mutateAsync({
        id: credential.id,
        data: {
          username: username || undefined,
          secret: secret || undefined,
          description: description || undefined,
        },
      });
    } else {
      await createCredential.mutateAsync({
        name,
        type,
        username: username || undefined,
        secret,
        description: description || undefined,
      });
    }

    onClose();
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>{isEdit ? 'Edit Credential' : 'New Credential'}</CardTitle>
        <CardDescription>
          {isEdit
            ? 'Update credential details. Leave secret empty to keep existing.'
            : 'Add a new credential for repository authentication.'}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="name">Name</Label>
            <Input
              id="name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="my-github-token"
              required
              disabled={isEdit}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="type">Type</Label>
            <select
              id="type"
              value={type}
              onChange={(e) => setType(e.target.value as CredentialType)}
              disabled={isEdit}
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
            >
              <option value="GITHUB_PAT">GitHub Personal Access Token</option>
              <option value="BASIC_AUTH">Basic Authentication</option>
              <option value="SSH_KEY">SSH Key</option>
            </select>
          </div>

          {type === 'BASIC_AUTH' && (
            <div className="space-y-2">
              <Label htmlFor="username">Username</Label>
              <Input
                id="username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="username"
              />
            </div>
          )}

          <div className="space-y-2">
            <Label htmlFor="secret">
              {type === 'GITHUB_PAT'
                ? 'Personal Access Token'
                : type === 'SSH_KEY'
                  ? 'Private Key'
                  : 'Password'}
              {isEdit && ' (leave empty to keep existing)'}
            </Label>
            <div className="relative">
              <Input
                id="secret"
                type={showSecret ? 'text' : 'password'}
                value={secret}
                onChange={(e) => setSecret(e.target.value)}
                placeholder={isEdit ? '••••••••' : 'Enter secret'}
                required={!isEdit}
                className="pr-10"
              />
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="absolute right-0 top-0 h-10 w-10"
                onClick={() => setShowSecret(!showSecret)}
              >
                {showSecret ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </Button>
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="description">Description (optional)</Label>
            <Textarea
              id="description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Token for accessing private repositories"
              rows={2}
            />
          </div>

          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={isPending}>
              {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              {isEdit ? 'Update' : 'Create'}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

function CredentialCard({
  credential,
  onEdit,
}: {
  credential: Credential;
  onEdit: (credential: Credential) => void;
}) {
  const deleteCredential = useDeleteCredential();
  const [showConfirmDelete, setShowConfirmDelete] = useState(false);

  const handleDelete = async () => {
    await deleteCredential.mutateAsync(credential.id);
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
            {getCredentialTypeBadge(credential.type)}
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
            <span className="text-muted-foreground">Username:</span> {credential.username}
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
              <Button variant="ghost" size="sm" onClick={() => setShowConfirmDelete(false)}>
                Cancel
              </Button>
            </>
          ) : (
            <Button variant="ghost" size="sm" onClick={() => setShowConfirmDelete(true)}>
              <Trash2 className="mr-2 h-4 w-4" />
              Delete
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

export default function CredentialsPage() {
  const router = useRouter();
  const user = useAuthStore((state) => state.user);
  const { data: credentials, isLoading } = useCredentials();
  const [showForm, setShowForm] = useState(false);
  const [editingCredential, setEditingCredential] = useState<Credential | undefined>();

  if (!user) {
    router.push('/login');
    return null;
  }

  const handleEdit = (credential: Credential) => {
    setEditingCredential(credential);
    setShowForm(true);
  };

  const handleCloseForm = () => {
    setShowForm(false);
    setEditingCredential(undefined);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Credentials</h1>
          <p className="text-muted-foreground">
            Manage authentication credentials for your repositories
          </p>
        </div>
        {!showForm && (
          <Button onClick={() => setShowForm(true)}>
            <Plus className="mr-2 h-4 w-4" />
            Add Credential
          </Button>
        )}
      </div>

      {showForm && <CredentialForm credential={editingCredential} onClose={handleCloseForm} />}

      {isLoading ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
        </div>
      ) : credentials && credentials.length > 0 ? (
        <div className="grid gap-4 md:grid-cols-2">
          {credentials.map((credential) => (
            <CredentialCard key={credential.id} credential={credential} onEdit={handleEdit} />
          ))}
        </div>
      ) : (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-12">
            <Key className="h-12 w-12 text-muted-foreground" />
            <h3 className="mt-4 text-lg font-semibold">No credentials yet</h3>
            <p className="mt-2 text-center text-sm text-muted-foreground">
              Add credentials to authenticate with private Git repositories
            </p>
            <Button onClick={() => setShowForm(true)} className="mt-6">
              <Plus className="mr-2 h-4 w-4" />
              Add your first credential
            </Button>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
