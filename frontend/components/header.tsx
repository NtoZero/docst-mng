'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Menu, Search, LogOut, User } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useAuthStore, useUIStore } from '@/lib/store';
import { useLogout } from '@/hooks/use-api';

export function Header() {
  const pathname = usePathname();
  const user = useAuthStore((state) => state.user);
  const toggleSidebar = useUIStore((state) => state.toggleSidebar);
  const logout = useLogout();

  const isLoginPage = pathname === '/login';

  if (isLoginPage) {
    return null;
  }

  return (
    <header className="sticky top-0 z-50 w-full border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
      <div className="flex h-14 items-center px-4">
        <Button variant="ghost" size="icon" className="mr-2 lg:hidden" onClick={toggleSidebar}>
          <Menu className="h-5 w-5" />
          <span className="sr-only">Toggle sidebar</span>
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
              placeholder="Search documents..."
              className="pl-8 md:w-[300px] lg:w-[400px]"
            />
          </div>
        </div>

        <nav className="flex items-center gap-2">
          {user ? (
            <>
              <div className="hidden items-center gap-2 md:flex">
                <User className="h-4 w-4" />
                <span className="text-sm">{user.displayName}</span>
              </div>
              <Button variant="ghost" size="icon" onClick={logout}>
                <LogOut className="h-4 w-4" />
                <span className="sr-only">Logout</span>
              </Button>
            </>
          ) : (
            <Button asChild variant="default" size="sm">
              <Link href="/login">Login</Link>
            </Button>
          )}
        </nav>
      </div>
    </header>
  );
}
