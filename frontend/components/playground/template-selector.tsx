'use client';

import { useState, useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { FileText, Sparkles } from 'lucide-react';
import { getPromptTemplates } from '@/lib/llm-api';
import type { PromptTemplate } from '@/lib/types';

interface TemplateSelectorProps {
  onSelect: (prompt: string) => void;
}

export function TemplateSelector({ onSelect }: TemplateSelectorProps) {
  const [open, setOpen] = useState(false);
  const [templates, setTemplates] = useState<PromptTemplate[]>([]);
  const [selectedTemplate, setSelectedTemplate] = useState<PromptTemplate | null>(null);
  const [variables, setVariables] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (open && templates.length === 0) {
      loadTemplates();
    }
  }, [open]);

  const loadTemplates = async () => {
    try {
      setLoading(true);
      const data = await getPromptTemplates();
      setTemplates(data);
    } catch (error) {
      console.error('Failed to load templates:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleTemplateChange = (templateId: string) => {
    const template = templates.find((t) => t.id === templateId);
    setSelectedTemplate(template || null);

    // Initialize variables with default values
    if (template) {
      const initialValues: Record<string, string> = {};
      template.variables.forEach((v) => {
        initialValues[v.name] = v.defaultValue || '';
      });
      setVariables(initialValues);
    }
  };

  const handleVariableChange = (name: string, value: string) => {
    setVariables((prev) => ({ ...prev, [name]: value }));
  };

  const handleApply = () => {
    if (!selectedTemplate) return;

    // Render template with variables
    let prompt = selectedTemplate.template;
    selectedTemplate.variables.forEach((v) => {
      const value = variables[v.name] || v.defaultValue || '';
      prompt = prompt.replace(`{{${v.name}}}`, value);
    });

    onSelect(prompt);
    setOpen(false);
    setSelectedTemplate(null);
    setVariables({});
  };

  const groupedTemplates = templates.reduce((acc, template) => {
    const category = template.category || 'other';
    if (!acc[category]) acc[category] = [];
    acc[category].push(template);
    return acc;
  }, {} as Record<string, PromptTemplate[]>);

  const categoryLabels: Record<string, string> = {
    search: '검색',
    summarize: '요약',
    create: '생성',
    update: '수정',
    list: '목록',
    git: 'Git',
    explain: '설명',
    other: '기타',
  };

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button variant="outline" size="icon" title="프롬프트 템플릿 선택">
          <Sparkles className="h-4 w-4" />
        </Button>
      </SheetTrigger>
      <SheetContent side="right" className="w-[400px] sm:w-[540px] overflow-y-auto">
        <SheetHeader>
          <SheetTitle>프롬프트 템플릿</SheetTitle>
          <SheetDescription>
            자주 사용하는 프롬프트 패턴을 빠르게 선택하세요
          </SheetDescription>
        </SheetHeader>

        <div className="mt-6 space-y-6">
          {/* Template Selector */}
          <div className="space-y-2">
            <Label>템플릿 선택</Label>
            <Select
              value={selectedTemplate?.id}
              onValueChange={handleTemplateChange}
              disabled={loading}
            >
              <SelectTrigger>
                <SelectValue placeholder={loading ? '로딩 중...' : '템플릿을 선택하세요'} />
              </SelectTrigger>
              <SelectContent>
                {Object.entries(groupedTemplates).map(([category, items]) => (
                  <div key={category}>
                    <div className="px-2 py-1.5 text-xs font-semibold text-muted-foreground">
                      {categoryLabels[category] || category}
                    </div>
                    {items.map((template) => (
                      <SelectItem key={template.id} value={template.id}>
                        <div className="flex items-center gap-2">
                          <FileText className="h-3 w-3" />
                          <span>{template.name}</span>
                        </div>
                      </SelectItem>
                    ))}
                  </div>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* Template Description */}
          {selectedTemplate && (
            <div className="p-3 bg-muted rounded-lg text-sm">
              <p className="text-muted-foreground">{selectedTemplate.description}</p>
            </div>
          )}

          {/* Variable Inputs */}
          {selectedTemplate && selectedTemplate.variables.length > 0 && (
            <div className="space-y-4">
              <Label>변수 입력</Label>
              {selectedTemplate.variables.map((variable) => (
                <div key={variable.name} className="space-y-2">
                  <Label htmlFor={variable.name} className="text-sm">
                    {variable.label}
                  </Label>
                  <Input
                    id={variable.name}
                    placeholder={variable.placeholder}
                    value={variables[variable.name] || ''}
                    onChange={(e) => handleVariableChange(variable.name, e.target.value)}
                  />
                </div>
              ))}
            </div>
          )}

          {/* Preview */}
          {selectedTemplate && (
            <div className="space-y-2">
              <Label>미리보기</Label>
              <div className="p-3 bg-muted rounded-lg text-sm font-mono whitespace-pre-wrap">
                {(() => {
                  let preview = selectedTemplate.template;
                  selectedTemplate.variables.forEach((v) => {
                    const value = variables[v.name] || v.defaultValue || `{{${v.name}}}`;
                    preview = preview.replace(`{{${v.name}}}`, value);
                  });
                  return preview;
                })()}
              </div>
            </div>
          )}

          {/* Actions */}
          <div className="flex gap-2">
            <Button
              onClick={handleApply}
              disabled={!selectedTemplate}
              className="flex-1"
            >
              적용
            </Button>
            <Button
              variant="outline"
              onClick={() => {
                setSelectedTemplate(null);
                setVariables({});
              }}
              disabled={!selectedTemplate}
            >
              초기화
            </Button>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}
