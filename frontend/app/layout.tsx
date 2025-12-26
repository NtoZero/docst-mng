import type { ReactNode } from 'react';
import type { Metadata } from 'next';
import { QueryProvider } from '@/providers/query-provider';
import { Header } from '@/components/header';
import { Sidebar } from '@/components/sidebar';
import './globals.css';

export const metadata: Metadata = {
  title: 'Docst',
  description: 'Unified documentation hub',
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-background font-sans antialiased">
        <QueryProvider>
          <div className="relative flex min-h-screen flex-col">
            <Header />
            <div className="flex flex-1">
              <Sidebar />
              <main className="flex-1 lg:pl-64">
                <div className="container py-6">{children}</div>
              </main>
            </div>
          </div>
        </QueryProvider>
      </body>
    </html>
  );
}
