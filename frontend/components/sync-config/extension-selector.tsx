'use client';

import { X } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useState } from 'react';

interface ExtensionSelectorProps {
  value: string[];
  onChange: (extensions: string[]) => void;
  availableExtensions?: string[];
  disabled?: boolean;
}

// 카테고리별 추천 확장자
const EXTENSION_CATEGORIES = {
  documents: {
    label: 'Documents',
    extensions: ['md', 'adoc', 'rst', 'txt'],
  },
  config: {
    label: 'Config',
    extensions: ['yml', 'yaml', 'json', 'toml', 'xml'],
  },
  code: {
    label: 'Code',
    extensions: ['java', 'py', 'ts', 'tsx', 'js', 'jsx', 'go', 'rs', 'kt'],
  },
  data: {
    label: 'Data',
    extensions: ['sql', 'graphql', 'csv'],
  },
};

export function ExtensionSelector({
  value,
  onChange,
  availableExtensions = [],
  disabled = false,
}: ExtensionSelectorProps) {
  const [customExt, setCustomExt] = useState('');

  const handleAdd = (ext: string) => {
    const normalized = ext.toLowerCase().replace(/^\./, '');
    if (normalized && !value.includes(normalized)) {
      onChange([...value, normalized]);
    }
  };

  const handleRemove = (ext: string) => {
    onChange(value.filter((e) => e !== ext));
  };

  const handleAddCustom = () => {
    if (customExt.trim()) {
      handleAdd(customExt.trim());
      setCustomExt('');
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleAddCustom();
    }
  };

  return (
    <div className="space-y-4">
      {/* 선택된 확장자 */}
      <div>
        <label className="text-sm font-medium mb-2 block">Selected Extensions</label>
        <div className="flex flex-wrap gap-2 min-h-[40px] p-2 border rounded-md bg-muted/30">
          {value.length === 0 ? (
            <span className="text-sm text-muted-foreground">No extensions selected</span>
          ) : (
            value.map((ext) => (
              <Badge key={ext} variant="secondary" className="gap-1">
                .{ext}
                <button
                  type="button"
                  onClick={() => handleRemove(ext)}
                  className="ml-1 hover:text-destructive"
                  disabled={disabled}
                >
                  <X className="h-3 w-3" />
                </button>
              </Badge>
            ))
          )}
        </div>
      </div>

      {/* 추천 확장자 (카테고리별) */}
      <div>
        <label className="text-sm font-medium mb-2 block">Recommended Extensions</label>
        <div className="space-y-3">
          {Object.entries(EXTENSION_CATEGORIES).map(([key, category]) => (
            <div key={key} className="flex items-start gap-2">
              <span className="text-xs text-muted-foreground w-20 pt-1">
                {category.label}
              </span>
              <div className="flex flex-wrap gap-1">
                {category.extensions.map((ext) => (
                  <Button
                    key={ext}
                    variant={value.includes(ext) ? 'secondary' : 'outline'}
                    size="sm"
                    className="h-6 text-xs px-2"
                    onClick={() => value.includes(ext) ? handleRemove(ext) : handleAdd(ext)}
                    disabled={disabled}
                  >
                    .{ext}
                  </Button>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* 레포지토리에서 발견된 확장자 */}
      {availableExtensions.length > 0 && (
        <div>
          <label className="text-sm font-medium mb-2 block">Found in Repository</label>
          <div className="flex flex-wrap gap-1">
            {availableExtensions
              .filter((ext) => !value.includes(ext))
              .slice(0, 20)
              .map((ext) => (
                <Button
                  key={ext}
                  variant="ghost"
                  size="sm"
                  className="h-6 text-xs px-2"
                  onClick={() => handleAdd(ext)}
                  disabled={disabled}
                >
                  .{ext}
                </Button>
              ))}
          </div>
        </div>
      )}

      {/* 커스텀 확장자 입력 */}
      <div>
        <label className="text-sm font-medium mb-2 block">Custom Extension</label>
        <div className="flex gap-2">
          <Input
            value={customExt}
            onChange={(e) => setCustomExt(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="e.g., proto"
            className="flex-1"
            disabled={disabled}
          />
          <Button onClick={handleAddCustom} disabled={disabled || !customExt.trim()}>
            Add
          </Button>
        </div>
      </div>
    </div>
  );
}
