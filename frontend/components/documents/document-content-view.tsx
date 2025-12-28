import { Link } from '@/i18n/routing';
import { FileText, Folder } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { groupDocumentsByPath } from '@/lib/document-utils';
import type { Document, DocType } from '@/lib/types';

function getDocTypeBadge(docType: DocType) {
  const variants: Record<DocType, { label: string; variant: 'default' | 'secondary' | 'outline' }> =
    {
      MD: { label: 'Markdown', variant: 'default' },
      ADOC: { label: 'AsciiDoc', variant: 'secondary' },
      OPENAPI: { label: 'OpenAPI', variant: 'outline' },
      ADR: { label: 'ADR', variant: 'secondary' },
      OTHER: { label: 'Other', variant: 'outline' },
    };
  const { label, variant } = variants[docType] || variants.OTHER;
  return <Badge variant={variant}>{label}</Badge>;
}

interface DocumentContentViewProps {
  documents: Document[];
}

export function DocumentContentView({ documents }: DocumentContentViewProps) {
  const groupedDocuments = groupDocumentsByPath(documents);

  return (
    <div className="space-y-6">
      {groupedDocuments.map(([folder, docs]) => (
        <div key={folder}>
          <div className="mb-2 flex items-center gap-2 text-sm font-medium text-muted-foreground">
            <Folder className="h-4 w-4" />
            {folder === '/' ? 'Root' : folder}
          </div>
          <div className="grid gap-2">
            {docs.map((doc) => (
              <Link key={doc.id} href={`/documents/${doc.id}`}>
                <Card className="cursor-pointer transition-colors hover:bg-accent">
                  <CardContent className="flex items-center justify-between p-4">
                    <div className="flex items-center gap-3">
                      <FileText className="h-5 w-5 text-muted-foreground" />
                      <div>
                        <p className="font-medium">{doc.path.split('/').pop()}</p>
                        <p className="text-xs text-muted-foreground">{doc.title}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      {getDocTypeBadge(doc.docType)}
                      <span className="text-xs text-muted-foreground">
                        {doc.latestCommitSha?.substring(0, 7)}
                      </span>
                    </div>
                  </CardContent>
                </Card>
              </Link>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
