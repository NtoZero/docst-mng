'use client';

import { useEffect, useState } from 'react';
import { OnboardingModal } from './onboarding-modal';
import { useOnboardingStore, useAuthHydrated } from '@/lib/store';

export function OnboardingWrapper() {
  const { isHydrated, user } = useAuthHydrated();
  const hasSeenOnboarding = useOnboardingStore((s) => s.hasSeenOnboarding);
  const [showOnboarding, setShowOnboarding] = useState(false);

  useEffect(() => {
    // Conditions to show onboarding:
    // 1. Zustand hydration complete
    // 2. User is logged in
    // 3. User has not seen onboarding
    if (isHydrated && user && !hasSeenOnboarding) {
      // Delay slightly after page load
      const timer = setTimeout(() => {
        setShowOnboarding(true);
      }, 1000);
      return () => clearTimeout(timer);
    }
  }, [isHydrated, user, hasSeenOnboarding]);

  // Don't render before hydration
  if (!isHydrated) return null;

  return (
    <OnboardingModal
      open={showOnboarding}
      onOpenChange={setShowOnboarding}
    />
  );
}
