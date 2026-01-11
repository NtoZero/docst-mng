import { redirect } from 'next/navigation';

interface PageProps {
  params: Promise<{
    locale: string;
  }>;
}

export default async function CredentialsPage({ params }: PageProps) {
  const { locale } = await params;
  redirect(`/${locale}/settings/credentials?scope=user`);
}
