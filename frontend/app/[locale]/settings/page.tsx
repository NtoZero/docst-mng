'use client';

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Key, KeyRound, Lock } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';

export default function SettingsPage() {
  const params = useParams();
  const locale = params.locale as string;

  const settingsItems = [
    {
      title: 'Credentials',
      description: 'Manage authentication credentials for services and repositories',
      icon: KeyRound,
      href: `/${locale}/settings/credentials`,
    },
    {
      title: 'API Keys',
      description: 'Manage API keys for MCP client authentication (Claude Desktop, Claude Code)',
      icon: Key,
      href: `/${locale}/settings/api-keys`,
    },
    {
      title: 'Password',
      description: 'Change your account password',
      icon: Lock,
      href: `/${locale}/settings/password`,
    },
  ];

  return (
    <div className="container mx-auto py-8 space-y-6 max-w-4xl">
      <div>
        <h1 className="text-3xl font-bold">Settings</h1>
        <p className="text-muted-foreground mt-2">
          Manage your account settings and preferences
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        {settingsItems.map((item) => (
          <Link key={item.href} href={item.href}>
            <Card className="hover:bg-muted/50 transition-colors cursor-pointer h-full">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <item.icon className="h-5 w-5" />
                  {item.title}
                </CardTitle>
                <CardDescription>{item.description}</CardDescription>
              </CardHeader>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
