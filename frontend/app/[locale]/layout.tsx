import type { ReactNode } from 'react';
import { NextIntlClientProvider } from 'next-intl';
import { getMessages } from 'next-intl/server';
import { QueryProvider } from '@/providers/query-provider';
import { Header } from '@/components/header';
import { Sidebar } from '@/components/sidebar';
import { Toaster } from '@/components/ui/sonner';
import '../globals.css';

export function generateStaticParams() {
  return [{ locale: 'en' }, { locale: 'ko' }];
}

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;

  const titles = {
    en: 'Docst',
    ko: 'Docst',
  };

  const descriptions = {
    en: 'Unified documentation hub',
    ko: '통합 문서 허브',
  };

  return {
    title: titles[locale as keyof typeof titles] || titles.en,
    description: descriptions[locale as keyof typeof descriptions] || descriptions.en,
  };
}

export default async function LocaleLayout({
  children,
  params,
}: {
  children: ReactNode;
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  const messages = await getMessages();

  return (
    <html lang={locale}>
      <body className="min-h-screen bg-background font-sans antialiased">
        <NextIntlClientProvider messages={messages}>
          <QueryProvider>
            <div className="relative flex min-h-screen flex-col">
              <Header />
              <div className="flex flex-1">
                <Sidebar />
                <main className="flex-1 lg:pl-72">
                  <div className="container px-6 py-6">{children}</div>
                </main>
              </div>
            </div>
            <Toaster />
          </QueryProvider>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
