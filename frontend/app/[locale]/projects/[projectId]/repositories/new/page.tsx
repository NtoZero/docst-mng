'use client';

import { use, useState, useEffect } from 'react';
import { Link, useRouter } from '@/i18n/routing';
import { ArrowLeft, Loader2, Github, Folder, CheckCircle2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useCreateRepository, useProject } from '@/hooks/use-api';
import { useAuthStore } from '@/lib/store';
import type { RepoProvider } from '@/lib/types';
import { parseGitUrl, type ParsedGitUrl } from '@/lib/git-url-parser';

export default function NewRepositoryPage({ params }: { params: Promise<{ projectId: string }> }) {
  const { projectId } = use(params);
  const router = useRouter();
  const user = useAuthStore((state) => state.user);

  const [inputMode, setInputMode] = useState<'manual' | 'url'>('url');
  const [gitUrl, setGitUrl] = useState('');
  const [parsedUrl, setParsedUrl] = useState<ParsedGitUrl | null>(null);
  const [provider, setProvider] = useState<RepoProvider>('GITHUB');
  const [owner, setOwner] = useState('');
  const [name, setName] = useState('');
  const [defaultBranch, setDefaultBranch] = useState('main');
  const [localPath, setLocalPath] = useState('');
  const [error, setError] = useState('');

  const { data: project } = useProject(projectId);
  const createMutation = useCreateRepository();

  useEffect(() => {
    if (!user) {
      router.push('/login');
    }
  }, [user, router]);

  // Parse Git URL when in URL mode
  useEffect(() => {
    if (inputMode === 'url' && gitUrl) {
      const parsed = parseGitUrl(gitUrl);
      setParsedUrl(parsed);

      if (parsed.isValid && parsed.provider && parsed.owner && parsed.name) {
        setProvider(parsed.provider);
        setOwner(parsed.owner);
        setName(parsed.name);
        if (parsed.provider === 'LOCAL' && parsed.cloneUrl) {
          setLocalPath(parsed.cloneUrl);
        }
      }
    } else if (inputMode === 'manual') {
      setParsedUrl(null);
    }
  }, [inputMode, gitUrl]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    // Validate Git URL mode
    if (inputMode === 'url') {
      if (!gitUrl.trim()) {
        setError('Git URL is required');
        return;
      }
      if (!parsedUrl?.isValid) {
        setError(parsedUrl?.error || 'Invalid Git URL');
        return;
      }
    }

    // Validate manual mode
    if (!owner.trim() || !name.trim()) {
      setError('Owner and repository name are required');
      return;
    }

    if (provider === 'LOCAL' && !localPath.trim()) {
      setError('Local path is required for local repositories');
      return;
    }

    try {
      await createMutation.mutateAsync({
        projectId,
        data: {
          provider,
          owner: owner.trim(),
          name: name.trim(),
          defaultBranch: defaultBranch.trim() || 'main',
          localPath: provider === 'LOCAL' ? localPath.trim() : undefined,
        },
      });
      router.push(`/projects/${projectId}`);
    } catch (err) {
      setError('Failed to add repository. Please check your inputs and try again.');
    }
  };

  if (!user) {
    return null;
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" asChild>
          <Link href={`/projects/${projectId}`}>
            <ArrowLeft className="h-4 w-4" />
          </Link>
        </Button>
        <div>
          <h1 className="text-3xl font-bold">Add Repository</h1>
          <p className="text-muted-foreground">
            Connect a repository to {project?.name || 'your project'}
          </p>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Repository Source</CardTitle>
          <CardDescription>
            {inputMode === 'url'
              ? 'Provider will be auto-detected from Git URL'
              : 'Choose where your repository is hosted'}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 gap-4">
            <button
              type="button"
              onClick={() => setProvider('GITHUB')}
              disabled={inputMode === 'url'}
              className={`flex flex-col items-center gap-2 rounded-lg border-2 p-6 transition-colors ${
                provider === 'GITHUB'
                  ? 'border-primary bg-primary/5'
                  : 'border-border hover:border-primary/50'
              } ${inputMode === 'url' ? 'cursor-not-allowed opacity-50' : ''}`}
            >
              <Github className="h-8 w-8" />
              <span className="font-medium">GitHub</span>
            </button>
            <button
              type="button"
              onClick={() => setProvider('LOCAL')}
              disabled={inputMode === 'url'}
              className={`flex flex-col items-center gap-2 rounded-lg border-2 p-6 transition-colors ${
                provider === 'LOCAL'
                  ? 'border-primary bg-primary/5'
                  : 'border-border hover:border-primary/50'
              } ${inputMode === 'url' ? 'cursor-not-allowed opacity-50' : ''}`}
            >
              <Folder className="h-8 w-8" />
              <span className="font-medium">Local</span>
            </button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Repository Details</CardTitle>
          <CardDescription>
            {provider === 'GITHUB'
              ? 'Enter the GitHub repository information'
              : 'Enter the local repository path'}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-6">
            {error && (
              <div className="rounded-md bg-destructive/10 p-3 text-sm text-destructive">
                {error}
              </div>
            )}

            <Tabs value={inputMode} onValueChange={(v) => setInputMode(v as 'manual' | 'url')}>
              <TabsList className="grid w-full grid-cols-2">
                <TabsTrigger value="manual">Manual</TabsTrigger>
                <TabsTrigger value="url">Git URL</TabsTrigger>
              </TabsList>

              <TabsContent value="manual" className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="owner">Owner *</Label>
                    <Input
                      id="owner"
                      placeholder={provider === 'GITHUB' ? 'username or org' : 'owner'}
                      value={owner}
                      onChange={(e) => setOwner(e.target.value)}
                      disabled={createMutation.isPending}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="name">Repository Name *</Label>
                    <Input
                      id="name"
                      placeholder="repository-name"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      disabled={createMutation.isPending}
                    />
                  </div>
                </div>
              </TabsContent>

              <TabsContent value="url" className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="gitUrl">Git URL *</Label>
                  <div className="relative">
                    <Input
                      id="gitUrl"
                      placeholder="https://github.com/owner/repository.git"
                      value={gitUrl}
                      onChange={(e) => setGitUrl(e.target.value)}
                      disabled={createMutation.isPending}
                      className={
                        gitUrl
                          ? parsedUrl?.isValid
                            ? 'border-green-500 focus-visible:ring-green-500'
                            : 'border-destructive focus-visible:ring-destructive'
                          : ''
                      }
                    />
                    {gitUrl && parsedUrl?.isValid && (
                      <CheckCircle2 className="absolute right-3 top-3 h-4 w-4 text-green-500" />
                    )}
                  </div>
                  {gitUrl && !parsedUrl?.isValid && parsedUrl?.error && (
                    <p className="text-xs text-destructive">{parsedUrl.error}</p>
                  )}
                  <p className="text-xs text-muted-foreground">
                    Supports GitHub HTTPS, SSH URLs, and local paths
                  </p>
                </div>

                {parsedUrl?.isValid && (
                  <div className="grid grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <Label htmlFor="ownerDetected">Owner (auto-detected)</Label>
                      <Input
                        id="ownerDetected"
                        value={owner}
                        disabled
                        className="bg-muted"
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="nameDetected">Repository (auto-detected)</Label>
                      <Input
                        id="nameDetected"
                        value={name}
                        disabled
                        className="bg-muted"
                      />
                    </div>
                  </div>
                )}
              </TabsContent>
            </Tabs>

            <div className="space-y-2">
              <Label htmlFor="branch">Default Branch</Label>
              <Input
                id="branch"
                placeholder="main"
                value={defaultBranch}
                onChange={(e) => setDefaultBranch(e.target.value)}
                disabled={createMutation.isPending}
              />
              <p className="text-xs text-muted-foreground">
                The branch to sync documents from (default: main)
              </p>
            </div>

            {provider === 'LOCAL' && (
              <div className="space-y-2">
                <Label htmlFor="localPath">Local Path *</Label>
                <Input
                  id="localPath"
                  placeholder="/path/to/repository"
                  value={localPath}
                  onChange={(e) => setLocalPath(e.target.value)}
                  disabled={createMutation.isPending}
                />
                <p className="text-xs text-muted-foreground">
                  Absolute path to the local Git repository
                </p>
              </div>
            )}

            <div className="flex gap-4">
              <Button type="submit" disabled={createMutation.isPending}>
                {createMutation.isPending ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Adding...
                  </>
                ) : (
                  'Add Repository'
                )}
              </Button>
              <Button type="button" variant="outline" asChild>
                <Link href={`/projects/${projectId}`}>Cancel</Link>
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
