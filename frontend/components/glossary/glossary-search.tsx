'use client';

import { Search } from 'lucide-react';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';

interface GlossarySearchProps {
  query: string;
  onQueryChange: (query: string) => void;
  mode: 'keyword' | 'semantic';
  onModeChange: (mode: 'keyword' | 'semantic') => void;
  placeholder?: string;
}

export function GlossarySearch({
  query,
  onQueryChange,
  mode,
  onModeChange,
  placeholder = 'Search terms...',
}: GlossarySearchProps) {
  return (
    <div className="flex gap-2">
      <div className="relative flex-1">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          type="search"
          value={query}
          onChange={(e) => onQueryChange(e.target.value)}
          placeholder={placeholder}
          className="pl-10"
        />
      </div>
      <Select value={mode} onValueChange={(v) => onModeChange(v as 'keyword' | 'semantic')}>
        <SelectTrigger className="w-[140px]">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="keyword">Keyword</SelectItem>
          <SelectItem value="semantic">Semantic</SelectItem>
        </SelectContent>
      </Select>
    </div>
  );
}
