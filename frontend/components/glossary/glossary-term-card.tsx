'use client';

import { Edit, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import type { GlossaryTerm } from '@/lib/types';

interface GlossaryTermCardProps {
  term: GlossaryTerm;
  onEdit?: (term: GlossaryTerm) => void;
  onDelete?: (term: GlossaryTerm) => void;
  canEdit?: boolean;
}

export function GlossaryTermCard({ term, onEdit, onDelete, canEdit = false }: GlossaryTermCardProps) {
  const truncatedDefinition =
    term.definition.length > 150 ? term.definition.slice(0, 150) + '...' : term.definition;

  return (
    <Card className="group transition-shadow hover:shadow-md">
      <CardHeader className="pb-2">
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-2">
            <CardTitle className="text-lg">{term.name}</CardTitle>
            {term.abbreviation && (
              <Badge variant="outline" className="text-xs">
                {term.abbreviation}
              </Badge>
            )}
          </div>
          {canEdit && (
            <div className="flex gap-1 opacity-0 transition-opacity group-hover:opacity-100">
              <Button
                variant="ghost"
                size="icon"
                className="h-8 w-8"
                onClick={() => onEdit?.(term)}
              >
                <Edit className="h-4 w-4" />
              </Button>
              <Button
                variant="ghost"
                size="icon"
                className="h-8 w-8 text-destructive hover:text-destructive"
                onClick={() => onDelete?.(term)}
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
          )}
        </div>
        {term.category && (
          <Badge variant="secondary" className="w-fit text-xs">
            {term.category}
          </Badge>
        )}
      </CardHeader>
      <CardContent className="space-y-2">
        <p className="text-sm text-muted-foreground">{truncatedDefinition}</p>
        {term.synonyms && term.synonyms.length > 0 && (
          <div className="flex flex-wrap gap-1">
            {term.synonyms.slice(0, 3).map((synonym, idx) => (
              <Badge key={idx} variant="outline" className="text-xs">
                {synonym}
              </Badge>
            ))}
            {term.synonyms.length > 3 && (
              <Badge variant="outline" className="text-xs">
                +{term.synonyms.length - 3}
              </Badge>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
