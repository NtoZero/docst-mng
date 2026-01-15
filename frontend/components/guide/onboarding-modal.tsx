'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  ChevronRight,
  ChevronLeft,
  KeyRound,
  FolderGit2,
  RefreshCw,
  Sparkles,
  Check,
  ArrowRight,
  Loader2,
} from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { useTranslations, useLocale } from 'next-intl';
import { useOnboardingStore } from '@/lib/store';
import { useCredentials, useProjects, useStats } from '@/hooks/use-api';

interface OnboardingModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

const STEP_ICONS = [Sparkles, KeyRound, FolderGit2, RefreshCw];
const TOTAL_STEPS = 4;

interface StepStatus {
  completed: boolean;
  count?: number;
  loading: boolean;
}

export function OnboardingModal({ open, onOpenChange }: OnboardingModalProps) {
  const t = useTranslations('guide.onboarding');
  const locale = useLocale();
  const router = useRouter();
  const { setOnboardingComplete } = useOnboardingStore();
  const [currentStep, setCurrentStep] = useState(0);
  const [dontShowAgain, setDontShowAgain] = useState(true);

  // Fetch current status for each step
  const { data: credentials, isLoading: credentialsLoading } = useCredentials();
  const { data: projects, isLoading: projectsLoading } = useProjects();
  const { data: stats, isLoading: statsLoading } = useStats();

  // Calculate step statuses
  const stepStatuses: Record<number, StepStatus> = {
    0: { completed: true, loading: false }, // Welcome step - always "completed"
    1: {
      completed: (credentials?.length ?? 0) > 0,
      count: credentials?.length ?? 0,
      loading: credentialsLoading,
    },
    2: {
      completed: (projects?.length ?? 0) > 0,
      count: projects?.length ?? 0,
      loading: projectsLoading,
    },
    3: {
      completed: (stats?.totalDocuments ?? 0) > 0,
      count: stats?.totalDocuments ?? 0,
      loading: statsLoading,
    },
  };

  // Navigation paths for each step
  const stepPaths: Record<number, string> = {
    1: `/${locale}/settings/credentials`,
    2: `/${locale}/projects/new`,
    3: `/${locale}/projects`, // Go to projects to sync
  };

  const handleComplete = () => {
    if (dontShowAgain) {
      setOnboardingComplete(true);
    }
    onOpenChange(false);
    setCurrentStep(0);
  };

  const handleSkip = () => {
    if (dontShowAgain) {
      setOnboardingComplete(true);
    }
    onOpenChange(false);
    setCurrentStep(0);
  };

  const handleGoToStep = (stepIndex: number) => {
    const path = stepPaths[stepIndex];
    if (path) {
      onOpenChange(false);
      router.push(path);
    }
  };

  const stepKeys = ['welcome', 'step1', 'step2', 'step3'];
  const currentStepKey = stepKeys[currentStep];
  const StepIcon = STEP_ICONS[currentStep];
  const currentStatus = stepStatuses[currentStep];

  const isLastStep = currentStep === TOTAL_STEPS - 1;
  const isFirstStep = currentStep === 0;

  // Calculate overall progress
  const completedSteps = Object.values(stepStatuses).filter((s) => s.completed).length - 1; // Exclude welcome
  const totalActionSteps = TOTAL_STEPS - 1; // Exclude welcome

  // Get localized status text
  const getStatusText = (step: number, status: StepStatus) => {
    const stepKey = stepKeys[step];
    if (status.completed) {
      return t(`${stepKey}.statusComplete`, { count: status.count ?? 0 });
    }
    return t(`${stepKey}.statusIncomplete`);
  };

  // Get localized action text
  const getActionText = (step: number, status: StepStatus) => {
    const stepKey = stepKeys[step];
    if (status.completed) {
      return t(`${stepKey}.actionComplete`);
    }
    return t(`${stepKey}.actionIncomplete`);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[520px]">
        <DialogHeader className="text-center pb-2">
          <div className="flex justify-center mb-4">
            <div
              className={`flex h-16 w-16 items-center justify-center rounded-full ${
                currentStatus.completed && currentStep > 0
                  ? 'bg-green-100 dark:bg-green-900'
                  : 'bg-primary/10'
              }`}
            >
              {currentStatus.completed && currentStep > 0 ? (
                <Check className="h-8 w-8 text-green-600 dark:text-green-400" />
              ) : (
                <StepIcon className="h-8 w-8 text-primary" />
              )}
            </div>
          </div>
          <DialogTitle className="text-xl">
            {t(`${currentStepKey}.title`)}
          </DialogTitle>
          <DialogDescription className="text-base">
            {t(`${currentStepKey}.description`)}
          </DialogDescription>
        </DialogHeader>

        {/* Status indicator for current step */}
        {currentStep > 0 && (
          <div className="flex justify-center py-2">
            {currentStatus.loading ? (
              <div className="flex items-center gap-2 text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" />
                <span className="text-sm">{t('checkingStatus')}</span>
              </div>
            ) : currentStatus.completed ? (
              <Badge variant="default" className="bg-green-600">
                <Check className="mr-1 h-3 w-3" />
                {getStatusText(currentStep, currentStatus)}
              </Badge>
            ) : (
              <Badge variant="secondary">
                {getStatusText(currentStep, currentStatus)}
              </Badge>
            )}
          </div>
        )}

        {/* Action button for current step */}
        {currentStep > 0 && (
          <div className="flex justify-center py-2">
            <Button
              variant={currentStatus.completed ? 'outline' : 'default'}
              onClick={() => handleGoToStep(currentStep)}
            >
              {getActionText(currentStep, currentStatus)}
              <ArrowRight className="ml-2 h-4 w-4" />
            </Button>
          </div>
        )}

        {/* Overall progress */}
        <div className="py-4">
          <div className="flex justify-between items-center mb-2">
            <span className="text-xs text-muted-foreground">{t('progressLabel')}</span>
            <span className="text-xs text-muted-foreground">
              {t('progressComplete', { completed: completedSteps, total: totalActionSteps })}
            </span>
          </div>
          <div className="flex gap-1">
            {[1, 2, 3].map((stepIdx) => (
              <div
                key={stepIdx}
                className={`h-2 flex-1 rounded-full transition-colors ${
                  stepStatuses[stepIdx].completed
                    ? 'bg-green-500'
                    : stepIdx === currentStep
                    ? 'bg-primary'
                    : 'bg-muted'
                }`}
              />
            ))}
          </div>
        </div>

        {/* Step indicators */}
        <div className="flex justify-center gap-2 py-2">
          {Array.from({ length: TOTAL_STEPS }).map((_, idx) => (
            <button
              key={idx}
              type="button"
              onClick={() => setCurrentStep(idx)}
              className={`h-2 rounded-full transition-all ${
                idx === currentStep
                  ? 'w-6 bg-primary'
                  : stepStatuses[idx].completed && idx > 0
                  ? 'w-2 bg-green-500'
                  : idx < currentStep
                  ? 'w-2 bg-primary/50'
                  : 'w-2 bg-muted'
              }`}
            />
          ))}
        </div>

        <DialogFooter className="flex-col gap-4 sm:flex-col">
          <div className="flex items-center justify-center space-x-2">
            <Checkbox
              id="dontShowAgain"
              checked={dontShowAgain}
              onCheckedChange={(checked) => setDontShowAgain(checked as boolean)}
            />
            <Label htmlFor="dontShowAgain" className="text-sm text-muted-foreground">
              {t('dontShowAgain')}
            </Label>
          </div>

          <div className="flex justify-between w-full">
            <Button variant="ghost" onClick={handleSkip}>
              {t('skipButton')}
            </Button>
            <div className="flex gap-2">
              {!isFirstStep && (
                <Button
                  variant="outline"
                  onClick={() => setCurrentStep((s) => s - 1)}
                >
                  <ChevronLeft className="mr-1 h-4 w-4" />
                  {t('prevButton')}
                </Button>
              )}
              {isLastStep ? (
                <Button onClick={handleComplete}>
                  {t('startButton')}
                </Button>
              ) : (
                <Button onClick={() => setCurrentStep((s) => s + 1)}>
                  {t('nextButton')}
                  <ChevronRight className="ml-1 h-4 w-4" />
                </Button>
              )}
            </div>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
