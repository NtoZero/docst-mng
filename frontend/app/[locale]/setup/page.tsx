'use client';

import { useState, useEffect } from 'react';
import { useRouter } from '@/i18n/routing';
import { Loader2, Check, AlertCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { PasswordStrengthIndicator } from '@/components/password-strength-indicator';
import { useSetupStatus, useInitialize } from '@/hooks/use-api';

export default function SetupPage() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  const { data: setupStatus, isLoading: statusLoading } = useSetupStatus();
  const initializeMutation = useInitialize();

  useEffect(() => {
    // If setup is not needed, redirect to login
    if (setupStatus && !setupStatus.needsSetup) {
      router.push('/login');
    }
  }, [setupStatus, router]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess(false);

    if (!email || !password || !displayName) {
      setError('All fields are required');
      return;
    }

    try {
      await initializeMutation.mutateAsync({ email, password, displayName });
      setSuccess(true);

      // Redirect to login page after 2 seconds
      setTimeout(() => {
        router.push('/login');
      }, 2000);
    } catch (err: any) {
      console.error('Setup error:', err);

      // Parse error message
      try {
        const errorData = JSON.parse(err.message);
        setError(errorData.message || 'Setup failed');
      } catch {
        setError(err.message || 'Setup failed. Please try again.');
      }
    }
  };

  if (statusLoading) {
    return (
      <div className="flex min-h-[calc(100vh-3.5rem)] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (!setupStatus?.needsSetup) {
    return null; // Will redirect via useEffect
  }

  return (
    <div className="flex min-h-[calc(100vh-3.5rem)] items-center justify-center p-4">
      <Card className="w-full max-w-md">
        <CardHeader className="text-center">
          <CardTitle className="text-2xl">Initial Setup</CardTitle>
          <CardDescription>
            Create the first admin account to get started
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            {error && (
              <div className="rounded-md bg-destructive/10 p-3 text-sm text-destructive flex items-start gap-2">
                <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
                <span>{error}</span>
              </div>
            )}

            {success && (
              <div className="rounded-md bg-green-50 p-3 text-sm text-green-600 flex items-center gap-2">
                <Check className="h-4 w-4" />
                Admin account created successfully! Redirecting to login...
              </div>
            )}

            <div className="rounded-md bg-blue-50 p-3 text-sm text-blue-900">
              <p className="font-medium mb-1">Welcome to Docst!</p>
              <p>This is a one-time setup to create your administrator account.</p>
            </div>

            <div className="space-y-2">
              <Label htmlFor="displayName">Admin Name</Label>
              <Input
                id="displayName"
                type="text"
                placeholder="Admin User"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                disabled={initializeMutation.isPending || success}
                required
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="email">Admin Email</Label>
              <Input
                id="email"
                type="email"
                placeholder="admin@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                disabled={initializeMutation.isPending || success}
                required
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="password">Admin Password</Label>
              <Input
                id="password"
                type="password"
                placeholder="Enter a strong password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={initializeMutation.isPending || success}
                required
              />
            </div>

            <PasswordStrengthIndicator password={password} />

            <Button
              type="submit"
              className="w-full"
              disabled={initializeMutation.isPending || success}
            >
              {initializeMutation.isPending ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Creating admin account...
                </>
              ) : (
                'Complete Setup'
              )}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
