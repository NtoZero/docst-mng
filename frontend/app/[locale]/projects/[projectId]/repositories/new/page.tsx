'use client';

import { use, useState, useEffect } from 'react';
import { Link, useRouter } from '@/i18n/routing';
import { ArrowLeft, Loader2, Github, Folder } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { useCreateRepository, useProject } from '@/hooks/use-api';
import { useAuthStore } from '@/lib/store';
import type { RepoProvider } from '@/lib/types';

export default function NewRepositoryPage({ params }: { params: Promise<{ projectId: string }> }) {
  const { projectId } = use(params);
  const router = useRouter();
  const user = useAuthStore((state) => state.user);

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

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

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
          <CardDescription>Choose where your repository is hosted</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 gap-4">
            <button
              type="button"
              onClick={() => setProvider('GITHUB')}
              className={`flex flex-col items-center gap-2 rounded-lg border-2 p-6 transition-colors ${
                provider === 'GITHUB'
                  ? 'border-primary bg-primary/5'
                  : 'border-border hover:border-primary/50'
              }`}
            >
              <Github className="h-8 w-8" />
              <span className="font-medium">GitHub</span>
            </button>
            <button
              type="button"
              onClick={() => setProvider('LOCAL')}
              className={`flex flex-col items-center gap-2 rounded-lg border-2 p-6 transition-colors ${
                provider === 'LOCAL'
                  ? 'border-primary bg-primary/5'
                  : 'border-border hover:border-primary/50'
              }`}
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
