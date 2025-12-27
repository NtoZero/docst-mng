import createMiddleware from 'next-intl/middleware';
import { routing } from './i18n/routing';

// Next.js 16 requires the named export to be 'proxy' instead of 'default'
export const proxy = createMiddleware(routing);

export const config = {
  // Match only internationalized pathnames
  matcher: ['/', '/(ko|en)/:path*'],
};
