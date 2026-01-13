'use client';

import { useState, useEffect } from 'react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';
import { Label } from '@/components/ui/label';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Loader2, Info } from 'lucide-react';
import { useRepositorySyncConfig, useUpdateRepositorySyncConfig, useFolderTree } from '@/hooks/use-api';
import { ExtensionSelector } from './extension-selector';
import { PathSelector } from './path-selector';
import type { RepositorySyncConfig } from '@/lib/types';

interface SyncConfigDialogProps {
  repositoryId: string;
  repositoryName: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function SyncConfigDialog({
  repositoryId,
  repositoryName,
  open,
  onOpenChange,
}: SyncConfigDialogProps) {
  const { data: config, isLoading } = useRepositorySyncConfig(open ? repositoryId : undefined);
  const { data: treeData } = useFolderTree(open ? repositoryId : undefined);
  const updateConfig = useUpdateRepositorySyncConfig();

  // 로컬 상태
  const [fileExtensions, setFileExtensions] = useState<string[]>([]);
  const [includePaths, setIncludePaths] = useState<string[]>([]);
  const [excludePaths, setExcludePaths] = useState<string[]>([]);
  const [scanOpenApi, setScanOpenApi] = useState(true);
  const [scanSwagger, setScanSwagger] = useState(true);

  // config가 로드되면 로컬 상태 업데이트
  useEffect(() => {
    if (config) {
      setFileExtensions(config.fileExtensions || []);
      setIncludePaths(config.includePaths || []);
      setExcludePaths(config.excludePaths || []);
      setScanOpenApi(config.scanOpenApi ?? true);
      setScanSwagger(config.scanSwagger ?? true);
    }
  }, [config]);

  const handleSave = async () => {
    try {
      await updateConfig.mutateAsync({
        id: repositoryId,
        data: {
          fileExtensions,
          includePaths,
          excludePaths,
          scanOpenApi,
          scanSwagger,
        },
      });
      onOpenChange(false);
    } catch (error) {
      console.error('Failed to update sync config:', error);
    }
  };

  const handleReset = () => {
    // 기본값으로 리셋
    setFileExtensions(['md', 'adoc']);
    setIncludePaths([]);
    setExcludePaths(['.git', 'node_modules', 'target', 'build', '.gradle', 'dist', 'out']);
    setScanOpenApi(true);
    setScanSwagger(true);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[600px] max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Sync Configuration</DialogTitle>
          <DialogDescription>
            Configure which files to sync for <strong>{repositoryName}</strong>
          </DialogDescription>
        </DialogHeader>

        {isLoading ? (
          <div className="flex items-center justify-center py-8">
            <Loader2 className="h-6 w-6 animate-spin" />
          </div>
        ) : (
          <Tabs defaultValue="extensions" className="mt-4">
            <TabsList className="grid w-full grid-cols-2">
              <TabsTrigger value="extensions">File Extensions</TabsTrigger>
              <TabsTrigger value="paths">Path Filters</TabsTrigger>
            </TabsList>

            <TabsContent value="extensions" className="space-y-4 mt-4">
              {/* 확장자 선택 */}
              <ExtensionSelector
                value={fileExtensions}
                onChange={setFileExtensions}
                availableExtensions={treeData?.extensions || []}
              />

              {/* OpenAPI/Swagger 토글 */}
              <div className="border-t pt-4 space-y-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Label htmlFor="scan-openapi">Scan OpenAPI specs</Label>
                    <span className="text-xs text-muted-foreground">
                      (*.openapi.yaml/yml/json)
                    </span>
                  </div>
                  <Switch
                    id="scan-openapi"
                    checked={scanOpenApi}
                    onCheckedChange={setScanOpenApi}
                  />
                </div>

                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Label htmlFor="scan-swagger">Scan Swagger specs</Label>
                    <span className="text-xs text-muted-foreground">
                      (*.swagger.yaml/yml/json)
                    </span>
                  </div>
                  <Switch
                    id="scan-swagger"
                    checked={scanSwagger}
                    onCheckedChange={setScanSwagger}
                  />
                </div>
              </div>
            </TabsContent>

            <TabsContent value="paths" className="mt-4">
              <PathSelector
                includePaths={includePaths}
                excludePaths={excludePaths}
                onIncludePathsChange={setIncludePaths}
                onExcludePathsChange={setExcludePaths}
                repositoryId={repositoryId}
              />
            </TabsContent>
          </Tabs>
        )}

        {/* 안내 메시지 */}
        <div className="flex items-start gap-2 rounded-md bg-muted/50 p-3 text-sm">
          <Info className="h-4 w-4 mt-0.5 text-muted-foreground" />
          <p className="text-muted-foreground">
            Changes will be applied on the next synchronization. Run a full sync to
            apply the new configuration immediately.
          </p>
        </div>

        <DialogFooter className="gap-2 sm:gap-0">
          <Button variant="outline" onClick={handleReset}>
            Reset to Default
          </Button>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={updateConfig.isPending}>
            {updateConfig.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
