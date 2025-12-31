'use client';

import { useTranslations } from 'next-intl';
import { Link, usePathname } from '@/i18n/routing';
import { FolderGit2, Settings, Plus, ChevronRight, Home, Key, Settings2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useUIStore, useAuthStore } from '@/lib/store';
import { useProjects } from '@/hooks/use-api';
import { cn } from '@/lib/utils';

export function Sidebar() {
  const t = useTranslations('sidebar');
  const tCommon = useTranslations('common');
  const pathname = usePathname();
  const sidebarOpen = useUIStore((state) => state.sidebarOpen);
  const user = useAuthStore((state) => state.user);
  const selectedProjectId = useUIStore((state) => state.selectedProjectId);
  const setSelectedProjectId = useUIStore((state) => state.setSelectedProjectId);

  const { data: projects, isLoading } = useProjects();

  const isLoginPage = pathname === '/login';

  if (isLoginPage || !user) {
    return null;
  }

  return (
    <aside
      className={cn(
        'fixed left-0 top-14 z-40 h-[calc(100vh-3.5rem)] w-64 border-r bg-background transition-transform duration-200 ease-in-out lg:translate-x-0',
        sidebarOpen ? 'translate-x-0' : '-translate-x-full'
      )}
    >
      <div className="flex h-full flex-col">
        <div className="flex-1 overflow-y-auto p-4">
          <nav className="space-y-2">
            <Link
              href="/"
              className={cn(
                'flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors hover:bg-accent',
                pathname === '/' && 'bg-accent'
              )}
            >
              <Home className="h-4 w-4" />
              {t('dashboard')}
            </Link>

            <div className="pt-4">
              <div className="flex items-center justify-between px-3 py-2">
                <span className="text-xs font-semibold uppercase text-muted-foreground">
                  {t('projects')}
                </span>
                <Button asChild variant="ghost" size="icon" className="h-6 w-6">
                  <Link href="/projects/new">
                    <Plus className="h-4 w-4" />
                    <span className="sr-only">{t('newProject')}</span>
                  </Link>
                </Button>
              </div>

              {isLoading ? (
                <div className="px-3 py-2 text-sm text-muted-foreground">{tCommon('loading')}</div>
              ) : projects && projects.length > 0 ? (
                <ul className="space-y-1">
                  {projects.map((project) => (
                    <li key={project.id}>
                      <Link
                        href={`/projects/${project.id}`}
                        onClick={() => setSelectedProjectId(project.id)}
                        className={cn(
                          'flex items-center gap-2 rounded-md px-3 py-2 text-sm transition-colors hover:bg-accent',
                          selectedProjectId === project.id && 'bg-accent',
                          pathname.startsWith(`/projects/${project.id}`) && 'font-medium'
                        )}
                      >
                        <FolderGit2 className="h-4 w-4" />
                        <span className="flex-1 truncate">{project.name}</span>
                        <ChevronRight className="h-4 w-4 text-muted-foreground" />
                      </Link>
                    </li>
                  ))}
                </ul>
              ) : (
                <div className="px-3 py-2 text-sm text-muted-foreground">{t('noProjects')}</div>
              )}
            </div>

            {selectedProjectId && (
              <div className="pt-4">
                <div className="px-3 py-2">
                  <span className="text-xs font-semibold uppercase text-muted-foreground">
                    {t('projectSettings')}
                  </span>
                </div>
                <Link
                  href={`/projects/${selectedProjectId}/settings/rag`}
                  className={cn(
                    'flex items-center gap-2 rounded-md px-3 py-2 text-sm transition-colors hover:bg-accent',
                    pathname === `/projects/${selectedProjectId}/settings/rag` && 'bg-accent font-medium'
                  )}
                >
                  <Settings2 className="h-4 w-4" />
                  {t('ragSettings')}
                </Link>
              </div>
            )}
          </nav>
        </div>

        <div className="border-t p-4 space-y-1">
          <Link
            href="/credentials"
            className={cn(
              'flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors hover:bg-accent',
              pathname === '/credentials' && 'bg-accent'
            )}
          >
            <Key className="h-4 w-4" />
            {t('credentials')}
          </Link>
          <Link
            href="/admin/settings"
            className={cn(
              'flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors hover:bg-accent',
              pathname.startsWith('/admin/settings') && 'bg-accent'
            )}
          >
            <Settings2 className="h-4 w-4" />
            {t('adminSettings')}
          </Link>
          <Link
            href="/settings"
            className={cn(
              'flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors hover:bg-accent',
              pathname === '/settings' && 'bg-accent'
            )}
          >
            <Settings className="h-4 w-4" />
            {t('settings')}
          </Link>
        </div>
      </div>
    </aside>
  );
}
