'use client';

import { useEffect, useState } from 'react';
import { X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Badge } from '@/components/ui/badge';
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import type { GlossaryTerm, CreateGlossaryTermRequest, UpdateGlossaryTermRequest } from '@/lib/types';

interface GlossaryFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  term?: GlossaryTerm | null;
  onSubmit: (data: CreateGlossaryTermRequest | UpdateGlossaryTermRequest) => void;
  isLoading?: boolean;
}

export function GlossaryFormDialog({
  open,
  onOpenChange,
  term,
  onSubmit,
  isLoading = false,
}: GlossaryFormDialogProps) {
  const [name, setName] = useState('');
  const [definition, setDefinition] = useState('');
  const [abbreviation, setAbbreviation] = useState('');
  const [category, setCategory] = useState('');
  const [synonymInput, setSynonymInput] = useState('');
  const [synonyms, setSynonyms] = useState<string[]>([]);

  const isEditing = !!term;

  useEffect(() => {
    if (term) {
      setName(term.name);
      setDefinition(term.definition);
      setAbbreviation(term.abbreviation || '');
      setCategory(term.category || '');
      setSynonyms(term.synonyms || []);
    } else {
      setName('');
      setDefinition('');
      setAbbreviation('');
      setCategory('');
      setSynonyms([]);
    }
    setSynonymInput('');
  }, [term, open]);

  const handleAddSynonym = () => {
    const trimmed = synonymInput.trim();
    if (trimmed && !synonyms.includes(trimmed)) {
      setSynonyms([...synonyms, trimmed]);
      setSynonymInput('');
    }
  };

  const handleRemoveSynonym = (synonym: string) => {
    setSynonyms(synonyms.filter((s) => s !== synonym));
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleAddSynonym();
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim() || !definition.trim()) return;

    const data: CreateGlossaryTermRequest | UpdateGlossaryTermRequest = {
      name: name.trim(),
      definition: definition.trim(),
      abbreviation: abbreviation.trim() || undefined,
      category: category.trim() || undefined,
      synonyms: synonyms.length > 0 ? synonyms : undefined,
    };

    onSubmit(data);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>{isEditing ? 'Edit Term' : 'Add New Term'}</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="name">Term Name *</Label>
            <Input
              id="name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g., API Gateway"
              required
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="definition">Definition *</Label>
            <Textarea
              id="definition"
              value={definition}
              onChange={(e) => setDefinition(e.target.value)}
              placeholder="Describe what this term means..."
              rows={4}
              required
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="abbreviation">Abbreviation</Label>
              <Input
                id="abbreviation"
                value={abbreviation}
                onChange={(e) => setAbbreviation(e.target.value)}
                placeholder="e.g., API"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="category">Category</Label>
              <Input
                id="category"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                placeholder="e.g., Architecture"
              />
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="synonyms">Synonyms</Label>
            <div className="flex gap-2">
              <Input
                id="synonyms"
                value={synonymInput}
                onChange={(e) => setSynonymInput(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="Type and press Enter to add"
              />
              <Button type="button" variant="outline" onClick={handleAddSynonym}>
                Add
              </Button>
            </div>
            {synonyms.length > 0 && (
              <div className="flex flex-wrap gap-1 pt-2">
                {synonyms.map((synonym) => (
                  <Badge key={synonym} variant="secondary" className="pr-1">
                    {synonym}
                    <button
                      type="button"
                      onClick={() => handleRemoveSynonym(synonym)}
                      className="ml-1 rounded-full p-0.5 hover:bg-muted-foreground/20"
                    >
                      <X className="h-3 w-3" />
                    </button>
                  </Badge>
                ))}
              </div>
            )}
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={isLoading || !name.trim() || !definition.trim()}>
              {isLoading ? 'Saving...' : isEditing ? 'Update' : 'Create'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
