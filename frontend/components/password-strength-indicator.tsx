'use client';

import { useMemo } from 'react';
import { Check, X } from 'lucide-react';
import { cn } from '@/lib/utils';

interface PasswordStrengthIndicatorProps {
  password: string;
  showRules?: boolean;
}

interface ValidationRule {
  label: string;
  test: (password: string) => boolean;
}

export function PasswordStrengthIndicator({ password, showRules = true }: PasswordStrengthIndicatorProps) {
  const rules: ValidationRule[] = useMemo(
    () => [
      {
        label: '8-128 characters',
        test: (pwd) => pwd.length >= 8 && pwd.length <= 128,
      },
      {
        label: 'At least 3 character types (uppercase, lowercase, digit, special)',
        test: (pwd) => {
          let count = 0;
          if (/[A-Z]/.test(pwd)) count++;
          if (/[a-z]/.test(pwd)) count++;
          if (/[0-9]/.test(pwd)) count++;
          if (/[^a-zA-Z0-9]/.test(pwd)) count++;
          return count >= 3;
        },
      },
      {
        label: 'No more than 2 consecutive repeating characters',
        test: (pwd) => {
          for (let i = 0; i < pwd.length - 2; i++) {
            if (pwd[i] === pwd[i + 1] && pwd[i] === pwd[i + 2]) {
              return false;
            }
          }
          return true;
        },
      },
    ],
    []
  );

  const strength = useMemo(() => {
    if (!password) return 0;

    let score = 0;

    // Length score
    if (password.length >= 12) score++;
    if (password.length >= 16) score++;

    // Character type score
    if (/[A-Z]/.test(password)) score++;
    if (/[a-z]/.test(password)) score++;
    if (/[0-9]/.test(password)) score++;
    if (/[^a-zA-Z0-9]/.test(password)) score++;

    return Math.min(score, 4);
  }, [password]);

  const validationResults = useMemo(() => {
    return rules.map((rule) => ({
      ...rule,
      passed: rule.test(password),
    }));
  }, [password, rules]);

  const isValid = validationResults.every((result) => result.passed);

  const strengthLabel = ['Weak', 'Fair', 'Good', 'Strong', 'Very Strong'][strength];
  const strengthColor = [
    'bg-destructive',
    'bg-orange-500',
    'bg-yellow-500',
    'bg-blue-500',
    'bg-green-500',
  ][strength];

  return (
    <div className="space-y-3">
      {password && (
        <div className="space-y-2">
          <div className="flex items-center justify-between text-sm">
            <span className="text-muted-foreground">Password strength:</span>
            <span className={cn('font-medium', isValid ? 'text-green-600' : 'text-muted-foreground')}>
              {strengthLabel}
            </span>
          </div>
          <div className="flex gap-1">
            {[...Array(4)].map((_, i) => (
              <div
                key={i}
                className={cn(
                  'h-1.5 flex-1 rounded-full transition-colors',
                  i < strength ? strengthColor : 'bg-muted'
                )}
              />
            ))}
          </div>
        </div>
      )}

      {showRules && password && (
        <div className="space-y-2 rounded-md border p-3">
          <p className="text-sm font-medium">Password requirements:</p>
          <ul className="space-y-1.5">
            {validationResults.map((result, index) => (
              <li key={index} className="flex items-start gap-2 text-sm">
                {result.passed ? (
                  <Check className="h-4 w-4 shrink-0 text-green-600 mt-0.5" />
                ) : (
                  <X className="h-4 w-4 shrink-0 text-muted-foreground mt-0.5" />
                )}
                <span className={cn(result.passed ? 'text-green-600' : 'text-muted-foreground')}>
                  {result.label}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
