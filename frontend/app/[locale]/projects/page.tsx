'use client';

import { Link, useRouter } from '@/i18n/routing';
import { Plus, FolderGit2, Calendar } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { useProjects } from '@/hooks/use-api';
import { useAuthStore } from '@/lib/store';
import { useEffect } from 'react';

export default function ProjectsPage() {
  const router = useRouter();
  const user = useAuthStore((state) => state.user);
  const { data: projects, isLoading, error } = useProjects();

  useEffect(() => {
    if (!user) {
      router.push('/login');
    }
  }, [user, router]);

  if (!user) {
    return null;
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Projects</h1>
          <p className="text-muted-foreground">Manage your documentation projects</p>
        </div>
        <Button asChild>
          <Link href="/projects/new">
            <Plus className="mr-2 h-4 w-4" />
            New Project
          </Link>
        </Button>
      </div>

      {isLoading ? (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {[1, 2, 3].map((i) => (
            <Card key={i} className="animate-pulse">
              <CardHeader>
                <div className="h-6 w-3/4 rounded bg-muted" />
                <div className="h-4 w-1/2 rounded bg-muted" />
              </CardHeader>
            </Card>
          ))}
        </div>
      ) : error ? (
        <Card>
          <CardContent className="py-8 text-center text-destructive">
            Failed to load projects. Please try again.
          </CardContent>
        </Card>
      ) : projects && projects.length > 0 ? (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {projects.map((project) => (
            <Link key={project.id} href={`/projects/${project.id}`}>
              <Card className="h-full cursor-pointer transition-colors hover:bg-accent">
                <CardHeader>
                  <CardTitle className="flex items-center gap-2">
                    <FolderGit2 className="h-5 w-5" />
                    {project.name}
                  </CardTitle>
                  <CardDescription className="line-clamp-2">
                    {project.description || 'No description'}
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <Calendar className="h-4 w-4" />
                    <span>Created {new Date(project.createdAt).toLocaleDateString()}</span>
                  </div>
                </CardContent>
              </Card>
            </Link>
          ))}
        </div>
      ) : (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-12">
            <FolderGit2 className="h-16 w-16 text-muted-foreground" />
            <h3 className="mt-4 text-lg font-semibold">No projects yet</h3>
            <p className="mt-2 text-sm text-muted-foreground">
              Create your first project to start managing documentation
            </p>
            <Button asChild className="mt-6">
              <Link href="/projects/new">
                <Plus className="mr-2 h-4 w-4" />
                Create your first project
              </Link>
            </Button>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
