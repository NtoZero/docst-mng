'use client';

import { useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { Link, usePathname } from '@/i18n/routing';
import { Menu, Search, LogOut, User } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { LanguageSwitcher } from '@/components/language-switcher';
import { useAuthStore, useUIStore } from '@/lib/store';
import { useLogout } from '@/hooks/use-api';

const XL_BREAKPOINT = 1280;

export function Header() {
  const t = useTranslations('header');
  const tCommon = useTranslations('common');
  const pathname = usePathname();
  const user = useAuthStore((state) => state.user);
  const toggleSidebar = useUIStore((state) => state.toggleSidebar);
  const setSidebarOpen = useUIStore((state) => state.setSidebarOpen);
  const logout = useLogout();

  const isLoginPage = pathname === '/login';

  useEffect(() => {
    const handleResize = () => {
      if (window.innerWidth >= XL_BREAKPOINT) {
        setSidebarOpen(true);
      } else {
        setSidebarOpen(false);
      }
    };

    // Set initial state
    handleResize();

    // Listen to window resize
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, [setSidebarOpen]);

  if (isLoginPage) {
    return null;
  }

  return (
    <header className="sticky top-0 z-50 w-full border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
      <div className="flex h-14 items-center px-4">
        <Button variant="ghost" size="icon" className="mr-2 xl:hidden" onClick={toggleSidebar}>
          <Menu className="h-5 w-5" />
          <span className="sr-only">{t('toggleSidebar')}</span>
        </Button>

        <div className="flex items-center gap-2">
          <Link href="/" className="flex items-center gap-2 font-semibold">
            <span className="text-xl">Docst</span>
          </Link>
        </div>

        <div className="flex-1 px-4">
          <div className="relative max-w-md">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              type="search"
              placeholder={t('searchPlaceholder')}
              className="pl-8 md:w-[300px] lg:w-[400px]"
            />
          </div>
        </div>

        <nav className="flex items-center gap-2">
          <LanguageSwitcher />
          {user ? (
            <>
              <div className="hidden items-center gap-2 md:flex">
                <User className="h-4 w-4" />
                <span className="text-sm">{user.displayName}</span>
              </div>
              <Button variant="ghost" size="icon" onClick={logout}>
                <LogOut className="h-4 w-4" />
                <span className="sr-only">{tCommon('logout')}</span>
              </Button>
            </>
          ) : (
            <Button asChild variant="default" size="sm">
              <Link href="/login">{tCommon('login')}</Link>
            </Button>
          )}
        </nav>
      </div>
    </header>
  );
}
