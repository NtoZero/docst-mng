'use client';

import { use, useEffect, useMemo, useState } from 'react';
import { Link, useRouter } from '@/i18n/routing';
import { ArrowLeft, Book, Loader2, Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { GlossaryTermCard } from '@/components/glossary/glossary-term-card';
import { GlossaryFormDialog } from '@/components/glossary/glossary-form-dialog';
import { GlossarySearch } from '@/components/glossary/glossary-search';
import { useProject } from '@/hooks/use-api';
import {
  useGlossaryTerms,
  useGlossaryCategories,
  useGlossarySearch,
  useCreateGlossaryTerm,
  useUpdateGlossaryTerm,
  useDeleteGlossaryTerm,
} from '@/hooks/use-glossary';
import { useAuthStore } from '@/lib/store';
import { useToast } from '@/hooks/use-toast';
import type { GlossaryTerm, CreateGlossaryTermRequest, UpdateGlossaryTermRequest } from '@/lib/types';

export default function GlossaryPage({ params }: { params: Promise<{ projectId: string }> }) {
  const { projectId } = use(params);
  const router = useRouter();
  const user = useAuthStore((state) => state.user);
  const { toast } = useToast();

  const [searchQuery, setSearchQuery] = useState('');
  const [searchMode, setSearchMode] = useState<'keyword' | 'semantic'>('keyword');
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [editingTerm, setEditingTerm] = useState<GlossaryTerm | null>(null);
  const [deletingTerm, setDeletingTerm] = useState<GlossaryTerm | null>(null);

  const { data: project } = useProject(projectId);
  const { data: categories } = useGlossaryCategories(projectId);
  const { data: terms, isLoading: termsLoading } = useGlossaryTerms(
    projectId,
    selectedCategory || undefined
  );
  const { data: searchResults, isLoading: searchLoading } = useGlossarySearch(
    projectId,
    searchQuery,
    searchMode,
    20,
    !!searchQuery
  );

  const createMutation = useCreateGlossaryTerm();
  const updateMutation = useUpdateGlossaryTerm();
  const deleteMutation = useDeleteGlossaryTerm();

  useEffect(() => {
    if (!user) {
      router.push('/login');
    }
  }, [user, router]);

  const displayTerms = useMemo(() => {
    if (searchQuery && searchResults) {
      return searchResults.map((r) => r.term);
    }
    return terms || [];
  }, [searchQuery, searchResults, terms]);

  const isLoading = termsLoading || (searchQuery && searchLoading);

  const handleOpenCreate = () => {
    setEditingTerm(null);
    setFormOpen(true);
  };

  const handleEdit = (term: GlossaryTerm) => {
    setEditingTerm(term);
    setFormOpen(true);
  };

  const handleDelete = (term: GlossaryTerm) => {
    setDeletingTerm(term);
  };

  const handleFormSubmit = async (data: CreateGlossaryTermRequest | UpdateGlossaryTermRequest) => {
    try {
      if (editingTerm) {
        await updateMutation.mutateAsync({
          projectId,
          termId: editingTerm.id,
          data: data as UpdateGlossaryTermRequest,
        });
        toast.success(`"${data.name}" has been updated.`);
      } else {
        await createMutation.mutateAsync({
          projectId,
          data: data as CreateGlossaryTermRequest,
        });
        toast.success(`"${data.name}" has been added to the glossary.`);
      }
      setFormOpen(false);
      setEditingTerm(null);
    } catch (error) {
      toast.error('Failed to save term. Please try again.');
    }
  };

  const handleConfirmDelete = async () => {
    if (!deletingTerm) return;
    try {
      await deleteMutation.mutateAsync({ projectId, termId: deletingTerm.id });
      toast.success(`"${deletingTerm.name}" has been removed.`);
      setDeletingTerm(null);
    } catch (error) {
      toast.error('Failed to delete term. Please try again.');
    }
  };

  if (!user) return null;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="icon" asChild>
            <Link href={`/projects/${projectId}`}>
              <ArrowLeft className="h-4 w-4" />
            </Link>
          </Button>
          <div>
            <h1 className="text-3xl font-bold">Glossary</h1>
            {project && <p className="text-muted-foreground">{project.name}</p>}
          </div>
        </div>
        <Button onClick={handleOpenCreate}>
          <Plus className="mr-2 h-4 w-4" />
          Add Term
        </Button>
      </div>

      <div className="space-y-4">
        <GlossarySearch
          query={searchQuery}
          onQueryChange={setSearchQuery}
          mode={searchMode}
          onModeChange={setSearchMode}
          placeholder="Search glossary terms..."
        />

        {categories && categories.length > 0 && (
          <div className="flex flex-wrap gap-2">
            <Badge
              variant={selectedCategory === null ? 'default' : 'outline'}
              className="cursor-pointer"
              onClick={() => setSelectedCategory(null)}
            >
              All
            </Badge>
            {categories.map((cat) => (
              <Badge
                key={cat}
                variant={selectedCategory === cat ? 'default' : 'outline'}
                className="cursor-pointer"
                onClick={() => setSelectedCategory(cat)}
              >
                {cat}
              </Badge>
            ))}
          </div>
        )}
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
        </div>
      ) : displayTerms.length > 0 ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {displayTerms.map((term) => (
            <GlossaryTermCard
              key={term.id}
              term={term}
              canEdit={true}
              onEdit={handleEdit}
              onDelete={handleDelete}
            />
          ))}
        </div>
      ) : (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-12">
            <Book className="h-12 w-12 text-muted-foreground" />
            <h3 className="mt-4 text-lg font-semibold">
              {searchQuery ? 'No terms found' : 'No glossary terms yet'}
            </h3>
            <p className="mt-2 text-sm text-muted-foreground">
              {searchQuery
                ? 'Try different keywords or search mode'
                : 'Add terms to create a shared vocabulary for your project'}
            </p>
            {!searchQuery && (
              <Button className="mt-4" onClick={handleOpenCreate}>
                <Plus className="mr-2 h-4 w-4" />
                Add First Term
              </Button>
            )}
          </CardContent>
        </Card>
      )}

      <GlossaryFormDialog
        open={formOpen}
        onOpenChange={setFormOpen}
        term={editingTerm}
        onSubmit={handleFormSubmit}
        isLoading={createMutation.isPending || updateMutation.isPending}
      />

      <AlertDialog open={!!deletingTerm} onOpenChange={() => setDeletingTerm(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete Term</AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to delete "{deletingTerm?.name}"? This action cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleConfirmDelete}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
