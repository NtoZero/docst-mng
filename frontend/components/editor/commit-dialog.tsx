'use client';

import { useState } from 'react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Loader2, GitCommit } from 'lucide-react';

interface CommitDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCommit: (message: string) => Promise<void>;
  documentPath: string;
  isLoading?: boolean;
}

export function CommitDialog({
  open,
  onOpenChange,
  onCommit,
  documentPath,
  isLoading = false,
}: CommitDialogProps) {
  const [commitMessage, setCommitMessage] = useState('');
  const [commitDescription, setCommitDescription] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const message = commitMessage.trim() || defaultMessage;
    const fullMessage = commitDescription
      ? `${message}\n\n${commitDescription}`
      : message;
    await onCommit(fullMessage);
    // Reset form after successful commit
    setCommitMessage('');
    setCommitDescription('');
  };

  const defaultMessage = `Update ${documentPath.split('/').pop()}`;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <GitCommit className="h-5 w-5" />
            Commit Changes
          </DialogTitle>
          <DialogDescription>
            Save your changes to{' '}
            <code className="text-xs bg-muted px-1 py-0.5 rounded">{documentPath}</code>
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit}>
          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="commit-message">Commit message</Label>
              <Input
                id="commit-message"
                value={commitMessage}
                onChange={(e) => setCommitMessage(e.target.value)}
                placeholder={defaultMessage}
                autoFocus
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="commit-description">Description (optional)</Label>
              <Textarea
                id="commit-description"
                value={commitDescription}
                onChange={(e) => setCommitDescription(e.target.value)}
                placeholder="Add more details about your changes..."
                rows={3}
              />
            </div>
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={isLoading}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={isLoading}>
              {isLoading ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Committing...
                </>
              ) : (
                <>
                  <GitCommit className="mr-2 h-4 w-4" />
                  Commit
                </>
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
