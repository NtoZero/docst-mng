'use client';

import { Folder } from 'lucide-react';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { useProjects } from '@/hooks/use-api';

interface ProjectSelectorProps {
  selectedProjectId?: string;
  onProjectChange: (projectId: string) => void;
}

export function ProjectSelector({ selectedProjectId, onProjectChange }: ProjectSelectorProps) {
  const { data: projects, isLoading } = useProjects();

  if (isLoading) {
    return (
      <Select disabled>
        <SelectTrigger className="w-[280px]">
          <SelectValue placeholder="Loading projects..." />
        </SelectTrigger>
      </Select>
    );
  }

  if (!projects || projects.length === 0) {
    return (
      <Select disabled>
        <SelectTrigger className="w-[280px]">
          <SelectValue placeholder="No projects available" />
        </SelectTrigger>
      </Select>
    );
  }

  return (
    <Select value={selectedProjectId} onValueChange={onProjectChange}>
      <SelectTrigger className="w-[280px]">
        <SelectValue placeholder="Select a project">
          {selectedProjectId && (
            <div className="flex items-center gap-2">
              <Folder className="h-4 w-4" />
              <span>{projects.find((p) => p.id === selectedProjectId)?.name}</span>
            </div>
          )}
        </SelectValue>
      </SelectTrigger>
      <SelectContent>
        {projects.map((project) => (
          <SelectItem key={project.id} value={project.id}>
            <div className="flex items-center gap-2">
              <Folder className="h-4 w-4" />
              <div className="flex flex-col">
                <span className="font-medium">{project.name}</span>
                {project.description && (
                  <span className="text-xs text-muted-foreground">{project.description}</span>
                )}
              </div>
            </div>
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
