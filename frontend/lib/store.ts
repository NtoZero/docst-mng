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
  sidebarOpen: false,
  toggleSidebar: () => set((state) => ({ sidebarOpen: !state.sidebarOpen })),
  setSidebarOpen: (open) => set({ sidebarOpen: open }),
  selectedProjectId: null,
  setSelectedProjectId: (id) => set({ selectedProjectId: id }),
}));

// ===== Editor State (Phase 8) =====

type EditorViewMode = 'source' | 'split';

interface EditorState {
  isEditMode: boolean;
  viewMode: EditorViewMode;
  hasUnsavedChanges: boolean;
  originalContent: string | null;
  editedContent: string | null;
  setEditMode: (mode: boolean) => void;
  setViewMode: (mode: EditorViewMode) => void;
  setContent: (original: string, edited?: string) => void;
  updateEditedContent: (content: string) => void;
  resetEditor: () => void;
}

export const useEditorStore = create<EditorState>()((set, get) => ({
  isEditMode: false,
  viewMode: 'split',
  hasUnsavedChanges: false,
  originalContent: null,
  editedContent: null,

  setEditMode: (mode) => set({ isEditMode: mode }),

  setViewMode: (mode) => set({ viewMode: mode }),

  setContent: (original, edited) =>
    set({
      originalContent: original,
      editedContent: edited ?? original,
      hasUnsavedChanges: false,
    }),

  updateEditedContent: (content) => {
    const { originalContent } = get();
    set({
      editedContent: content,
      hasUnsavedChanges: content !== originalContent,
    });
  },

  resetEditor: () =>
    set({
      isEditMode: false,
      hasUnsavedChanges: false,
      originalContent: null,
      editedContent: null,
    }),
}));

// ===== Onboarding State (Phase 15) =====

interface OnboardingState {
  hasSeenOnboarding: boolean;
  completedGuides: string[];
  setOnboardingComplete: (complete: boolean) => void;
  markGuideComplete: (guideKey: string) => void;
  resetOnboarding: () => void;
}

export const useOnboardingStore = create<OnboardingState>()(
  persist(
    (set) => ({
      hasSeenOnboarding: false,
      completedGuides: [],
      setOnboardingComplete: (complete) => set({ hasSeenOnboarding: complete }),
      markGuideComplete: (guideKey) =>
        set((state) => ({
          completedGuides: [...new Set([...state.completedGuides, guideKey])],
        })),
      resetOnboarding: () =>
        set({ hasSeenOnboarding: false, completedGuides: [] }),
    }),
    {
      name: 'docst-onboarding',
    }
  )
);
