'use client';

import { useLocale } from 'next-intl';
import { useRouter, usePathname } from '@/i18n/routing';
import { Button } from '@/components/ui/button';
import { Globe } from 'lucide-react';

export function LanguageSwitcher() {
  const locale = useLocale();
  const router = useRouter();
  const pathname = usePathname();

  const switchLocale = () => {
    const newLocale = locale === 'en' ? 'ko' : 'en';
    router.replace(pathname, { locale: newLocale });
  };

  return (
    <Button variant="ghost" size="sm" onClick={switchLocale} className="gap-2">
      <Globe className="h-4 w-4" />
      <span className="text-sm">{locale === 'en' ? '한국어' : 'English'}</span>
    </Button>
  );
}
