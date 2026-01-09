import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { useEffect, useState } from 'react';

interface User {
  id: string;
  email: string;
  displayName: string;
}

interface AuthState {
  user: User | null;
  token: string | null;
  _hasHydrated: boolean;
  setAuth: (user: User, token: string) => void;
  clearAuth: () => void;
  setHasHydrated: (state: boolean) => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      token: null,
      _hasHydrated: false,
      setAuth: (user, token) => set({ user, token }),
      clearAuth: () => set({ user: null, token: null }),
      setHasHydrated: (state) => set({ _hasHydrated: state }),
    }),
    {
      name: 'docst-auth',
      onRehydrateStorage: () => (state) => {
        state?.setHasHydrated(true);
      },
    }
  )
);

/**
 * Hook to wait for Zustand hydration before accessing auth state.
 * Prevents auth check before localStorage is loaded.
 *
 * @returns { isHydrated, user, token }
 */
export function useAuthHydrated() {
  const [isHydrated, setIsHydrated] = useState(false);
  const user = useAuthStore((state) => state.user);
  const token = useAuthStore((state) => state.token);
  const hasHydrated = useAuthStore((state) => state._hasHydrated);

  useEffect(() => {
    // Wait for Zustand to hydrate from localStorage
    if (hasHydrated) {
      setIsHydrated(true);
    }
  }, [hasHydrated]);

  return { isHydrated, user, token };
}

interface UIState {
  sidebarOpen: boolean;
  toggleSidebar: () => void;
  setSidebarOpen: (open: boolean) => void;
  selectedProjectId: string | null;
  setSelectedProjectId: (id: string | null) => void;
}

export const useUIStore = create<UIState>()((set) => ({
  sidebarOpen: true,
  toggleSidebar: () => set((state) => ({ sidebarOpen: !state.sidebarOpen })),
  setSidebarOpen: (open) => set({ sidebarOpen: open }),
  selectedProjectId: null,
  setSelectedProjectId: (id) => set({ selectedProjectId: id }),
}));
