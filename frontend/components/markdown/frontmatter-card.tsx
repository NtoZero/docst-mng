'use client';

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Calendar, User, Tag, FileText } from 'lucide-react';
import { cn } from '@/lib/utils';

interface FrontmatterData {
  title?: string;
  author?: string;
  date?: string;
  tags?: string[];
  description?: string;
  [key: string]: unknown;
}

interface FrontmatterCardProps {
  data: FrontmatterData;
  className?: string;
}

export function FrontmatterCard({ data, className }: FrontmatterCardProps) {
  if (!data || Object.keys(data).length === 0) return null;

  const { title, author, date, tags, description, ...rest } = data;

  const formatDate = (dateValue: string) => {
    try {
      return new Date(dateValue).toLocaleDateString();
    } catch {
      return dateValue;
    }
  };

  return (
    <Card className={cn('mb-6 border-l-4 border-l-primary', className)}>
      <CardHeader className="pb-3">
        {title && (
          <CardTitle className="flex items-center gap-2">
            <FileText className="h-5 w-5" />
            {title}
          </CardTitle>
        )}
        {description && (
          <p className="text-sm text-muted-foreground">{description}</p>
        )}
      </CardHeader>
      <CardContent className="pt-0">
        <div className="flex flex-wrap gap-4 text-sm">
          {author && (
            <div className="flex items-center gap-1.5">
              <User className="h-4 w-4 text-muted-foreground" />
              <span>{author}</span>
            </div>
          )}
          {date && (
            <div className="flex items-center gap-1.5">
              <Calendar className="h-4 w-4 text-muted-foreground" />
              <span>{formatDate(date)}</span>
            </div>
          )}
        </div>
        {tags && tags.length > 0 && (
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <Tag className="h-4 w-4 text-muted-foreground" />
            {tags.map((tag) => (
              <Badge key={tag} variant="secondary">
                {tag}
              </Badge>
            ))}
          </div>
        )}
        {Object.keys(rest).length > 0 && (
          <div className="mt-3 grid grid-cols-2 gap-2 text-sm">
            {Object.entries(rest).map(([key, value]) => (
              <div key={key}>
                <span className="font-medium text-muted-foreground">{key}: </span>
                <span>{String(value)}</span>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
