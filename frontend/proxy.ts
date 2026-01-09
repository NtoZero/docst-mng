import createMiddleware from 'next-intl/middleware';
import { routing } from './i18n/routing';

// Next.js 16 requires the named export to be 'proxy' instead of 'default'
export const proxy = createMiddleware(routing);

export const config = {
  // Match all pathnames except for:
  // - API routes (/api/...)
  // - Static files (/_next/..., /favicon.ico, etc.)
  // - Public files containing a dot (e.g., images)
  matcher: [
    // Match root
    '/',
    // Match locale prefixed paths
    '/(ko|en)/:path*',
    // Match paths without locale (will be redirected by middleware)
    '/((?!api|_next|_vercel|.*\\..*).*)',
  ],
};
