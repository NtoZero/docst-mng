'use client';

import { useState } from 'react';
import { useParams } from 'next/navigation';
import { Key, Plus, Trash2, Eye, EyeOff, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Badge } from '@/components/ui/badge';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  useProjectCredentials,
  useCreateProjectCredential,
  useDeleteProjectCredential,
} from '@/hooks/use-admin-config';
import type { ProjectCredential, CredentialType } from '@/lib/types';

function getCredentialLabel(type: CredentialType): string {
  const labels: Record<CredentialType, string> = {
    GITHUB_PAT: 'GitHub PAT',
    BASIC_AUTH: 'Basic Auth',
    SSH_KEY: 'SSH Key',
    OPENAI_API_KEY: 'OpenAI API Key',
    ANTHROPIC_API_KEY: 'Anthropic API Key',
    NEO4J_AUTH: 'Neo4j Auth',
    CUSTOM_API_KEY: 'Custom API Key',
  };
  return labels[type] || type;
}

function CredentialForm({ projectId, onClose }: { projectId: string; onClose: () => void }) {
  const [name, setName] = useState('');
  const [type, setType] = useState<CredentialType>('OPENAI_API_KEY');
  const [secret, setSecret] = useState('');
  const [description, setDescription] = useState('');
  const [showSecret, setShowSecret] = useState(false);

  const createMutation = useCreateProjectCredential();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    await createMutation.mutateAsync({
      projectId,
      request: {
        name,
        type,
        secret,
        description: description || undefined,
      },
    });
    onClose();
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Add Credential</CardTitle>
        <CardDescription>Add a new credential for this project</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="name">Name</Label>
            <Input
              id="name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="my-openai-key"
              required
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="type">Type</Label>
            <Select value={type} onValueChange={(value) => setType(value as CredentialType)}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="OPENAI_API_KEY">OpenAI API Key</SelectItem>
                <SelectItem value="ANTHROPIC_API_KEY">Anthropic API Key</SelectItem>
                <SelectItem value="GITHUB_PAT">GitHub Personal Access Token</SelectItem>
                <SelectItem value="NEO4J_AUTH">Neo4j Authentication</SelectItem>
                <SelectItem value="BASIC_AUTH">Basic Authentication</SelectItem>
                <SelectItem value="SSH_KEY">SSH Key</SelectItem>
                <SelectItem value="CUSTOM_API_KEY">Custom API Key</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label htmlFor="secret">
              {type === 'OPENAI_API_KEY' || type === 'ANTHROPIC_API_KEY'
                ? 'API Key'
                : type === 'GITHUB_PAT'
                  ? 'Personal Access Token'
                  : 'Secret'}
            </Label>
            <div className="relative">
              <Input
                id="secret"
                type={showSecret ? 'text' : 'password'}
                value={secret}
                onChange={(e) => setSecret(e.target.value)}
                placeholder={
                  type === 'OPENAI_API_KEY'
                    ? 'sk-proj-...'
                    : type === 'ANTHROPIC_API_KEY'
                      ? 'sk-ant-...'
                      : 'Enter secret'
                }
                required
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
            {type === 'OPENAI_API_KEY' && (
              <p className="text-xs text-muted-foreground">
                Get your API key from{' '}
                <a
                  href="https://platform.openai.com/api-keys"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="underline"
                >
                  OpenAI Platform
                </a>
              </p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="description">Description (optional)</Label>
            <Textarea
              id="description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Main OpenAI key for LLM features"
              rows={2}
            />
          </div>

          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Create
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

function CredentialCard({
  credential,
  projectId,
}: {
  credential: ProjectCredential;
  projectId: string;
}) {
  const [showConfirmDelete, setShowConfirmDelete] = useState(false);

  const deleteMutation = useDeleteProjectCredential();

  const handleDelete = async () => {
    await deleteMutation.mutateAsync({
      projectId,
      credentialId: credential.id,
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
          <Badge variant="default">{getCredentialLabel(credential.type)}</Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        {credential.description && (
          <p className="text-sm text-muted-foreground">{credential.description}</p>
        )}

        <div className="flex items-center gap-2">
          {showConfirmDelete ? (
            <>
              <Button
                variant="destructive"
                size="sm"
                onClick={handleDelete}
                disabled={deleteMutation.isPending}
              >
                {deleteMutation.isPending ? (
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

export default function ProjectCredentialsPage() {
  const params = useParams();
  const projectId = params.projectId as string;
  const [showForm, setShowForm] = useState(false);

  const { data: credentials, isLoading } = useProjectCredentials(projectId);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-end">
        {!showForm && (
          <Button onClick={() => setShowForm(true)}>
            <Plus className="mr-2 h-4 w-4" />
            Add Credential
          </Button>
        )}
      </div>

      {showForm && <CredentialForm projectId={projectId} onClose={() => setShowForm(false)} />}

      {isLoading ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
        </div>
      ) : credentials && credentials.length > 0 ? (
        <div className="grid gap-4 md:grid-cols-2">
          {credentials.map((credential) => (
            <CredentialCard key={credential.id} credential={credential} projectId={projectId} />
          ))}
        </div>
      ) : (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-12">
            <Key className="h-12 w-12 text-muted-foreground" />
            <h3 className="mt-4 text-lg font-semibold">No credentials yet</h3>
            <p className="mt-2 text-center text-sm text-muted-foreground">
              Add credentials to use LLM features and external services
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
