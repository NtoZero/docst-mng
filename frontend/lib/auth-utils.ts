/**
 * Authentication Utilities
 *
 * Centralized authentication token management.
 * All files should import from here instead of implementing their own getAuthToken.
 */

const AUTH_STORAGE_KEY = 'docst-auth';

/**
 * Get the authentication token from localStorage.
 *
 * Reads from Zustand persisted state in localStorage.
 * Returns null if:
 * - Running on server (SSR)
 * - No auth data in storage
 * - Invalid JSON format
 * - No token in state
 */
export function getAuthToken(): string | null {
  if (typeof window === 'undefined') return null;

  const stored = localStorage.getItem(AUTH_STORAGE_KEY);
  if (!stored) return null;

  try {
    const parsed = JSON.parse(stored);
    return parsed.state?.token || null;
  } catch {
    return null;
  }
}

/**
 * Async version of getAuthToken for consistency with async API patterns.
 * Simply wraps the sync version.
 */
export async function getAuthTokenAsync(): Promise<string | null> {
  return getAuthToken();
}

/**
 * Check if user is authenticated.
 */
export function isAuthenticated(): boolean {
  return getAuthToken() !== null;
}

/**
 * Create authorization headers for API requests.
 * Returns empty object if no token available.
 */
export function getAuthHeaders(): Record<string, string> {
  const token = getAuthToken();
  if (!token) return {};
  return { Authorization: `Bearer ${token}` };
}
