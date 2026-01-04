'use client';

import { useEffect, useState } from 'react';
import {
  useCreateSystemCredential,
  useUpdateSystemCredential,
} from '@/hooks/use-admin-config';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Loader2 } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { SystemCredential, CredentialType } from '@/lib/types';

interface CredentialFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  credential?: SystemCredential | null;
}

const CREDENTIAL_TYPES: CredentialType[] = [
  'OPENAI_API_KEY',
  'NEO4J_AUTH',
  'PGVECTOR_AUTH',
  'ANTHROPIC_API_KEY',
  'CUSTOM_API_KEY',
];

export function CredentialFormDialog({
  open,
  onOpenChange,
  credential,
}: CredentialFormDialogProps) {
  const t = useTranslations('admin');
  const createCredential = useCreateSystemCredential();
  const updateCredential = useUpdateSystemCredential();

  const [name, setName] = useState('');
  const [type, setType] = useState<CredentialType>('OPENAI_API_KEY');
  const [secret, setSecret] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState<string | null>(null);

  // DB Auth 전용 필드 (NEO4J_AUTH, PGVECTOR_AUTH)
  const [dbUsername, setDbUsername] = useState('');
  const [dbPassword, setDbPassword] = useState('');

  const isEditMode = !!credential;

  useEffect(() => {
    if (credential) {
      setName(credential.name);
      setType(credential.type);
      setSecret('');
      setDescription(credential.description || '');
      setDbUsername('');
      setDbPassword('');
    } else {
      setName('');
      setType('OPENAI_API_KEY');
      setSecret('');
      setDescription('');
      setDbUsername('');
      setDbPassword('');
    }
    setError(null);
  }, [credential, open]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!name.trim()) {
      setError('Name is required');
      return;
    }

    // DB Auth 검증 (NEO4J_AUTH, PGVECTOR_AUTH)
    const isDbAuth = type === 'NEO4J_AUTH' || type === 'PGVECTOR_AUTH';
    if (isDbAuth) {
      if (!isEditMode && (!dbUsername.trim() || !dbPassword.trim())) {
        setError('Username and password are required for database authentication');
        return;
      }
      if (isEditMode && (dbUsername.trim() || dbPassword.trim())) {
        // 둘 다 입력되어야 함
        if (!dbUsername.trim() || !dbPassword.trim()) {
          setError('Both username and password must be provided to update');
          return;
        }
      }
    } else {
      // 다른 타입은 secret 필수
      if (!isEditMode && !secret.trim()) {
        setError('Secret is required');
        return;
      }
    }

    try {
      // DB Auth일 때 JSON 생성
      let finalSecret = secret.trim();
      if (isDbAuth && dbUsername.trim() && dbPassword.trim()) {
        finalSecret = JSON.stringify({
          username: dbUsername.trim(),
          password: dbPassword.trim(),
        });
      }

      if (isEditMode) {
        await updateCredential.mutateAsync({
          id: credential.id,
          request: {
            secret: finalSecret || undefined,
            description: description.trim() || undefined,
          },
        });
      } else {
        await createCredential.mutateAsync({
          name: name.trim(),
          type,
          secret: finalSecret,
          description: description.trim() || undefined,
        });
      }
      onOpenChange(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save credential');
    }
  };

  const isLoading = createCredential.isPending || updateCredential.isPending;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[500px]">
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>
              {isEditMode ? 'Edit Credential' : 'Create Credential'}
            </DialogTitle>
            <DialogDescription>
              {isEditMode
                ? 'Update the credential details below.'
                : 'Enter the credential details below. The secret will be encrypted.'}
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-4">
            {error && (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}

            <div className="grid gap-2">
              <Label htmlFor="name">Name *</Label>
              <Input
                id="name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g., OpenAI Production Key"
                disabled={isEditMode}
                required
              />
              {isEditMode && (
                <p className="text-xs text-muted-foreground">
                  Name cannot be changed after creation
                </p>
              )}
            </div>

            <div className="grid gap-2">
              <Label htmlFor="type">Type *</Label>
              <Select
                value={type}
                onValueChange={(value) => setType(value as CredentialType)}
                disabled={isEditMode}
              >
                <SelectTrigger id="type">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {CREDENTIAL_TYPES.map((t) => (
                    <SelectItem key={t} value={t}>
                      {t}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {isEditMode && (
                <p className="text-xs text-muted-foreground">
                  Type cannot be changed after creation
                </p>
              )}
            </div>

            {type === 'NEO4J_AUTH' || type === 'PGVECTOR_AUTH' ? (
              <>
                <div className="grid gap-2">
                  <Label htmlFor="db-username">
                    Username {!isEditMode && '*'}
                    {isEditMode && ' (leave empty to keep current)'}
                  </Label>
                  <Input
                    id="db-username"
                    value={dbUsername}
                    onChange={(e) => setDbUsername(e.target.value)}
                    placeholder={type === 'NEO4J_AUTH' ? 'neo4j' : 'postgres'}
                    required={!isEditMode}
                  />
                </div>
                <div className="grid gap-2">
                  <Label htmlFor="db-password">
                    Password {!isEditMode && '*'}
                    {isEditMode && ' (leave empty to keep current)'}
                  </Label>
                  <Input
                    id="db-password"
                    type="password"
                    value={dbPassword}
                    onChange={(e) => setDbPassword(e.target.value)}
                    placeholder="your-password"
                    required={!isEditMode}
                  />
                  {isEditMode && (
                    <p className="text-xs text-muted-foreground">
                      Both username and password must be provided to update
                    </p>
                  )}
                </div>
              </>
            ) : (
              <div className="grid gap-2">
                <Label htmlFor="secret">
                  Secret {!isEditMode && '*'}
                  {isEditMode && ' (leave empty to keep current)'}
                </Label>
                <Input
                  id="secret"
                  type="password"
                  value={secret}
                  onChange={(e) => setSecret(e.target.value)}
                  placeholder="sk-..."
                  required={!isEditMode}
                />
              </div>
            )}

            <div className="grid gap-2">
              <Label htmlFor="description">Description</Label>
              <Textarea
                id="description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Optional description"
                rows={3}
              />
            </div>
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={isLoading}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={isLoading}>
              {isLoading ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Saving...
                </>
              ) : isEditMode ? (
                'Update'
              ) : (
                'Create'
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
