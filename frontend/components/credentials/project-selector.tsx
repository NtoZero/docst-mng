'use client';

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { useProjects } from '@/hooks/use-api';
import { Loader2 } from 'lucide-react';

interface ProjectSelectorProps {
  selectedProjectId: string | undefined;
  onProjectChange: (projectId: string) => void;
}

export function ProjectSelector({
  selectedProjectId,
  onProjectChange,
}: ProjectSelectorProps) {
  const { data: projects, isLoading } = useProjects();

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin" />
        <span className="text-sm">Loading projects...</span>
      </div>
    );
  }

  if (!projects || projects.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        No projects available. Create a project first.
      </p>
    );
  }

  return (
    <Select value={selectedProjectId} onValueChange={onProjectChange}>
      <SelectTrigger className="w-[300px]">
        <SelectValue placeholder="Select a project" />
      </SelectTrigger>
      <SelectContent>
        {projects.map((project) => (
          <SelectItem key={project.id} value={project.id}>
            {project.name}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
