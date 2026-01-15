'use client';

import { useEffect, useState } from 'react';
import {
  useCreateUnifiedCredential,
  useUpdateUnifiedCredential,
} from '@/hooks/use-unified-credentials';
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
import { Eye, EyeOff, Loader2 } from 'lucide-react';
import type { CredentialScope, CredentialType, UnifiedCredential } from '@/lib/types';
import {
  getTypesForScope,
  isJsonAuthType,
  getCredentialTypeLabel,
  getSecretLabel,
  getSecretPlaceholder,
  getHelpUrl,
  getGuideKey,
  CREDENTIAL_TYPE_CONFIG,
} from './credential-type-config';
import { HelpPopover, GuideSheet } from '@/components/guide';

interface CredentialFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  scope: CredentialScope;
  projectId?: string;
  credential?: UnifiedCredential | null;
}

export function CredentialFormDialog({
  open,
  onOpenChange,
  scope,
  projectId,
  credential,
}: CredentialFormDialogProps) {
  const createCredential = useCreateUnifiedCredential();
  const updateCredential = useUpdateUnifiedCredential();

  // Get available types for this scope
  const availableTypes = getTypesForScope(scope);
  const defaultType = availableTypes[0] || 'GITHUB_PAT';

  // Form state
  const [name, setName] = useState('');
  const [type, setType] = useState<CredentialType>(defaultType);
  const [secret, setSecret] = useState('');
  const [username, setUsername] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [showSecret, setShowSecret] = useState(false);

  // DB Auth fields (NEO4J_AUTH, PGVECTOR_AUTH)
  const [dbUsername, setDbUsername] = useState('');
  const [dbPassword, setDbPassword] = useState('');

  // Guide sheet state
  const [guideSheetOpen, setGuideSheetOpen] = useState(false);

  const isEditMode = !!credential;
  const isDbAuth = isJsonAuthType(type);
  const showUsernameField = type === 'BASIC_AUTH' && scope === 'USER';
  const helpUrl = getHelpUrl(type);
  const guideKey = getGuideKey(type);

  // Reset form when dialog opens/closes or credential changes
  useEffect(() => {
    if (credential) {
      setName(credential.name);
      setType(credential.type);
      setSecret('');
      setUsername(credential.username || '');
      setDescription(credential.description || '');
      setDbUsername('');
      setDbPassword('');
    } else {
      setName('');
      setType(defaultType);
      setSecret('');
      setUsername('');
      setDescription('');
      setDbUsername('');
      setDbPassword('');
    }
    setError(null);
    setShowSecret(false);
  }, [credential, open, defaultType]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    // Validation
    if (!name.trim()) {
      setError('Name is required');
      return;
    }

    // DB Auth validation
    if (isDbAuth) {
      if (!isEditMode && (!dbUsername.trim() || !dbPassword.trim())) {
        setError('Username and password are required for database authentication');
        return;
      }
      if (isEditMode && (dbUsername.trim() || dbPassword.trim())) {
        if (!dbUsername.trim() || !dbPassword.trim()) {
          setError('Both username and password must be provided to update');
          return;
        }
      }
    } else {
      if (!isEditMode && !secret.trim()) {
        setError(`${getSecretLabel(type)} is required`);
        return;
      }
    }

    try {
      // Build secret value
      let finalSecret = secret.trim();
      if (isDbAuth && dbUsername.trim() && dbPassword.trim()) {
        finalSecret = JSON.stringify({
          username: dbUsername.trim(),
          password: dbPassword.trim(),
        });
      }

      if (isEditMode && credential) {
        await updateCredential.mutateAsync({
          scope,
          id: credential.id,
          projectId,
          request: {
            secret: finalSecret || undefined,
            username: showUsernameField ? username.trim() || undefined : undefined,
            description: description.trim() || undefined,
          },
        });
      } else {
        await createCredential.mutateAsync({
          scope,
          projectId,
          request: {
            name: name.trim(),
            type,
            secret: finalSecret,
            username: showUsernameField ? username.trim() || undefined : undefined,
            description: description.trim() || undefined,
          },
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

            {/* Name field */}
            <div className="grid gap-2">
              <Label htmlFor="name">Name *</Label>
              <Input
                id="name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g., my-github-token"
                disabled={isEditMode}
                required
              />
              {isEditMode && (
                <p className="text-xs text-muted-foreground">
                  Name cannot be changed after creation
                </p>
              )}
            </div>

            {/* Type field */}
            <div className="grid gap-2">
              <div className="flex items-center gap-1">
                <Label htmlFor="type">Type *</Label>
                {guideKey && !isEditMode && (
                  <HelpPopover
                    guideKey={guideKey}
                    showDetailButton={true}
                    onDetailClick={() => setGuideSheetOpen(true)}
                    externalUrl={helpUrl}
                  />
                )}
              </div>
              <Select
                value={type}
                onValueChange={(value) => setType(value as CredentialType)}
                disabled={isEditMode}
              >
                <SelectTrigger id="type">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {availableTypes.map((t) => (
                    <SelectItem key={t} value={t}>
                      {getCredentialTypeLabel(t)}
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

            {/* Username field for BASIC_AUTH in USER scope */}
            {showUsernameField && (
              <div className="grid gap-2">
                <Label htmlFor="username">Username</Label>
                <Input
                  id="username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="username"
                />
              </div>
            )}

            {/* Secret fields - different for DB auth vs regular */}
            {isDbAuth ? (
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
                  <div className="relative">
                    <Input
                      id="db-password"
                      type={showSecret ? 'text' : 'password'}
                      value={dbPassword}
                      onChange={(e) => setDbPassword(e.target.value)}
                      placeholder="your-password"
                      required={!isEditMode}
                      className="pr-10"
                    />
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      className="absolute right-0 top-0 h-10 w-10"
                      onClick={() => setShowSecret(!showSecret)}
                    >
                      {showSecret ? (
                        <EyeOff className="h-4 w-4" />
                      ) : (
                        <Eye className="h-4 w-4" />
                      )}
                    </Button>
                  </div>
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
                  {getSecretLabel(type)} {!isEditMode && '*'}
                  {isEditMode && ' (leave empty to keep current)'}
                </Label>
                <div className="relative">
                  <Input
                    id="secret"
                    type={showSecret ? 'text' : 'password'}
                    value={secret}
                    onChange={(e) => setSecret(e.target.value)}
                    placeholder={getSecretPlaceholder(type) || (isEditMode ? '••••••••' : 'Enter secret')}
                    required={!isEditMode}
                    className="pr-10"
                  />
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="absolute right-0 top-0 h-10 w-10"
                    onClick={() => setShowSecret(!showSecret)}
                  >
                    {showSecret ? (
                      <EyeOff className="h-4 w-4" />
                    ) : (
                      <Eye className="h-4 w-4" />
                    )}
                  </Button>
                </div>
              </div>
            )}

            {/* Description field */}
            <div className="grid gap-2">
              <Label htmlFor="description">Description</Label>
              <Textarea
                id="description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Optional description"
                rows={2}
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

      {/* Guide Sheet for detailed instructions */}
      {guideKey && (
        <GuideSheet
          open={guideSheetOpen}
          onOpenChange={setGuideSheetOpen}
          guideKey={guideKey}
        />
      )}
    </Dialog>
  );
}
