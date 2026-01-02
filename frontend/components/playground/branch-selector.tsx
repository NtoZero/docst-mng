'use client';

import { useState } from 'react';
import { GitBranch, Plus, Loader2, Check, ChevronsUpDown } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
} from '@/components/ui/command';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useBranches } from '@/hooks/use-branches';
import { cn } from '@/lib/utils';

interface BranchSelectorProps {
  repositoryId: string;
  onBranchChange?: (branch: string) => void;
}

/**
 * 브랜치 선택기 컴포넌트
 *
 * - 브랜치 목록 표시
 * - 브랜치 전환
 * - 새 브랜치 생성
 */
export function BranchSelector({ repositoryId, onBranchChange }: BranchSelectorProps) {
  const {
    branches,
    currentBranch,
    isLoadingBranches,
    isLoadingCurrentBranch,
    createBranch,
    switchBranch,
    isCreatingBranch,
    isSwitchingBranch,
  } = useBranches(repositoryId);

  const [open, setOpen] = useState(false);
  const [showCreateDialog, setShowCreateDialog] = useState(false);
  const [newBranchName, setNewBranchName] = useState('');
  const [fromBranch, setFromBranch] = useState('');

  const handleSelectBranch = async (branchName: string) => {
    if (branchName === currentBranch) {
      setOpen(false);
      return;
    }

    try {
      await switchBranch(branchName);
      setOpen(false);
      onBranchChange?.(branchName);
    } catch (error) {
      console.error('Failed to switch branch:', error);
    }
  };

  const handleCreateBranch = async () => {
    if (!newBranchName.trim()) return;

    try {
      await createBranch(newBranchName, fromBranch || currentBranch);
      setShowCreateDialog(false);
      setNewBranchName('');
      setFromBranch('');
      onBranchChange?.(newBranchName);
    } catch (error) {
      console.error('Failed to create branch:', error);
    }
  };

  const isLoading = isLoadingBranches || isLoadingCurrentBranch;
  const isProcessing = isSwitchingBranch || isCreatingBranch;

  if (isLoading) {
    return (
      <Button variant="outline" disabled className="w-[200px]">
        <Loader2 className="h-4 w-4 mr-2 animate-spin" />
        Loading...
      </Button>
    );
  }

  return (
    <>
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Button
            variant="outline"
            role="combobox"
            aria-expanded={open}
            className="w-[200px] justify-between"
            disabled={isProcessing}
          >
            <GitBranch className="h-4 w-4 mr-2" />
            <span className="truncate">
              {currentBranch || 'Select branch...'}
            </span>
            {isProcessing ? (
              <Loader2 className="h-4 w-4 ml-2 shrink-0 animate-spin" />
            ) : (
              <ChevronsUpDown className="h-4 w-4 ml-2 shrink-0 opacity-50" />
            )}
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-[200px] p-0">
          <Command>
            <CommandInput placeholder="Search branch..." />
            <CommandList>
              <CommandEmpty>No branch found.</CommandEmpty>
              <CommandGroup>
                {branches?.map((branch) => (
                  <CommandItem
                    key={branch}
                    value={branch}
                    onSelect={() => handleSelectBranch(branch)}
                  >
                    <Check
                      className={cn(
                        'mr-2 h-4 w-4',
                        currentBranch === branch ? 'opacity-100' : 'opacity-0'
                      )}
                    />
                    {branch}
                  </CommandItem>
                ))}
              </CommandGroup>
              <CommandSeparator />
              <CommandGroup>
                <CommandItem
                  onSelect={() => {
                    setOpen(false);
                    setShowCreateDialog(true);
                  }}
                >
                  <Plus className="mr-2 h-4 w-4" />
                  Create new branch...
                </CommandItem>
              </CommandGroup>
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>

      {/* Create Branch Dialog */}
      <Dialog open={showCreateDialog} onOpenChange={setShowCreateDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create New Branch</DialogTitle>
            <DialogDescription>
              Create a new branch from an existing branch.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="branch-name">Branch Name *</Label>
              <Input
                id="branch-name"
                value={newBranchName}
                onChange={(e) => setNewBranchName(e.target.value)}
                placeholder="feature/my-new-feature"
                disabled={isCreatingBranch}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="from-branch">From Branch (optional)</Label>
              <Input
                id="from-branch"
                value={fromBranch}
                onChange={(e) => setFromBranch(e.target.value)}
                placeholder={currentBranch || 'main'}
                disabled={isCreatingBranch}
              />
              <p className="text-xs text-muted-foreground">
                Leave empty to branch from current branch ({currentBranch})
              </p>
            </div>
          </div>

          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setShowCreateDialog(false)}
              disabled={isCreatingBranch}
            >
              Cancel
            </Button>
            <Button
              onClick={handleCreateBranch}
              disabled={!newBranchName.trim() || isCreatingBranch}
            >
              {isCreatingBranch && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
              Create Branch
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
