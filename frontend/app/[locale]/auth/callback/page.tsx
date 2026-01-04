'use client';

import { Suspense, useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Loader2, CheckCircle, XCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useAuthStore } from '@/lib/store';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8342';

function OAuthCallbackContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { setAuth } = useAuthStore();
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading');
  const [errorMessage, setErrorMessage] = useState<string>('');

  useEffect(() => {
    const token = searchParams.get('token');
    const error = searchParams.get('error');

    if (error) {
      setStatus('error');
      setErrorMessage(error);
      return;
    }

    if (token) {
      // 토큰 저장
      localStorage.setItem('docst-token', token);

      // 사용자 정보 조회
      fetch(`${API_BASE}/api/auth/me`, {
        headers: { Authorization: `Bearer ${token}` }
      })
        .then(res => {
          if (!res.ok) {
            throw new Error('Failed to fetch user info');
          }
          return res.json();
        })
        .then(user => {
          setAuth(user, token);
          setStatus('success');
          // 대시보드로 리다이렉트
          setTimeout(() => router.push('/'), 1500);
        })
        .catch(err => {
          setStatus('error');
          setErrorMessage('Failed to fetch user info');
        });
    } else {
      setStatus('error');
      setErrorMessage('No token received');
    }
  }, [searchParams, router, setAuth]);

  return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="text-center">
        {status === 'loading' && (
          <>
            <Loader2 className="w-12 h-12 mx-auto animate-spin text-primary" />
            <p className="mt-4 text-muted-foreground">Authenticating...</p>
          </>
        )}

        {status === 'success' && (
          <>
            <CheckCircle className="w-12 h-12 mx-auto text-green-500" />
            <p className="mt-4 text-green-600">Login successful! Redirecting...</p>
          </>
        )}

        {status === 'error' && (
          <>
            <XCircle className="w-12 h-12 mx-auto text-red-500" />
            <p className="mt-4 text-red-600">{errorMessage}</p>
            <Button
              className="mt-4"
              onClick={() => router.push('/login')}
            >
              Back to Login
            </Button>
          </>
        )}
      </div>
    </div>
  );
}

export default function OAuthCallbackPage() {
  return (
    <Suspense fallback={
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <Loader2 className="w-12 h-12 mx-auto animate-spin text-primary" />
          <p className="mt-4 text-muted-foreground">Loading...</p>
        </div>
      </div>
    }>
      <OAuthCallbackContent />
    </Suspense>
  );
}
