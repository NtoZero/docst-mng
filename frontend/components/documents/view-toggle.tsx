import { LayoutGrid, List } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

export type ViewMode = 'content' | 'tree';

interface ViewToggleProps {
  view: ViewMode;
  onViewChange: (view: ViewMode) => void;
}

export function ViewToggle({ view, onViewChange }: ViewToggleProps) {
  return (
    <div className="flex items-center gap-1 rounded-md border p-1">
      <Button
        variant="ghost"
        size="sm"
        className={cn(
          'h-8 px-3',
          view === 'content' && 'bg-accent',
        )}
        onClick={() => onViewChange('content')}
      >
        <LayoutGrid className="h-4 w-4 mr-2" />
        Content
      </Button>
      <Button
        variant="ghost"
        size="sm"
        className={cn(
          'h-8 px-3',
          view === 'tree' && 'bg-accent',
        )}
        onClick={() => onViewChange('tree')}
      >
        <List className="h-4 w-4 mr-2" />
        Tree
      </Button>
    </div>
  );
}
