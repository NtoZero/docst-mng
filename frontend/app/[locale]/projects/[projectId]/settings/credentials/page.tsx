import { redirect } from 'next/navigation';

interface PageProps {
  params: Promise<{
    locale: string;
    projectId: string;
  }>;
}

export default async function ProjectCredentialsPage({ params }: PageProps) {
  const { locale, projectId } = await params;
  redirect(`/${locale}/settings/credentials?scope=project&projectId=${projectId}`);
}
