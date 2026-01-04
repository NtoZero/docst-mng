'use client';

import { useState } from 'react';
import { useCreateApiKey } from '@/hooks/use-api';
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
import { Button } from '@/components/ui/button';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Copy, Check, AlertTriangle, Loader2 } from 'lucide-react';
import { toast } from 'sonner';

interface ApiKeyFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function ApiKeyFormDialog({ open, onOpenChange }: ApiKeyFormDialogProps) {
  const [name, setName] = useState('');
  const [expiresInDays, setExpiresInDays] = useState<number | undefined>(90);
  const [createdKey, setCreatedKey] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const createApiKey = useCreateApiKey();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    try {
      const result = await createApiKey.mutateAsync({ name, expiresInDays });
      setCreatedKey(result.key);
      toast.success('API key created successfully!');
    } catch (error: any) {
      const errorMessage = error?.message || 'Failed to create API key';
      toast.error(errorMessage);
    }
  };

  const handleCopy = async () => {
    if (createdKey) {
      await navigator.clipboard.writeText(createdKey);
      setCopied(true);
      toast.success('API key copied to clipboard!');
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const handleClose = () => {
    setName('');
    setExpiresInDays(90);
    setCreatedKey(null);
    setCopied(false);
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-[600px]">
        {createdKey ? (
          // Show the created key with copy button and warning
          <>
            <DialogHeader>
              <DialogTitle>API Key Created</DialogTitle>
              <DialogDescription>
                Your API key has been created successfully.
              </DialogDescription>
            </DialogHeader>

            <Alert variant="destructive">
              <AlertTriangle className="h-4 w-4" />
              <AlertTitle>Copy your API key now!</AlertTitle>
              <AlertDescription>
                This is the only time you will see this key. Make sure to copy and store it securely.
              </AlertDescription>
            </Alert>

            <div className="space-y-4">
              <div className="flex items-center gap-2 p-3 bg-muted rounded-md font-mono text-sm break-all">
                <code className="flex-1">{createdKey}</code>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={handleCopy}
                  className="shrink-0"
                >
                  {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
                  {copied ? 'Copied!' : 'Copy'}
                </Button>
              </div>

              <div className="text-sm text-muted-foreground">
                <p><strong>Name:</strong> {name}</p>
                <p><strong>Expires:</strong> {expiresInDays ? `in ${expiresInDays} days` : 'Never'}</p>
              </div>
            </div>

            <DialogFooter>
              <Button onClick={handleClose}>Done</Button>
            </DialogFooter>
          </>
        ) : (
          // Show creation form
          <form onSubmit={handleSubmit}>
            <DialogHeader>
              <DialogTitle>Create API Key</DialogTitle>
              <DialogDescription>
                Generate a new API key for MCP client authentication (Claude Desktop, Claude Code).
              </DialogDescription>
            </DialogHeader>

            <div className="grid gap-4 py-4">
              <div className="grid gap-2">
                <Label htmlFor="name">Name *</Label>
                <Input
                  id="name"
                  placeholder="e.g., Claude Desktop, CI/CD Pipeline"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  required
                  maxLength={100}
                />
                <p className="text-xs text-muted-foreground">
                  A descriptive name to identify this API key
                </p>
              </div>

              <div className="grid gap-2">
                <Label htmlFor="expiration">Expiration</Label>
                <Select
                  value={expiresInDays?.toString() ?? 'never'}
                  onValueChange={(value) =>
                    setExpiresInDays(value === 'never' ? undefined : parseInt(value))
                  }
                >
                  <SelectTrigger id="expiration">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="30">30 days</SelectItem>
                    <SelectItem value="90">90 days (Recommended)</SelectItem>
                    <SelectItem value="180">180 days</SelectItem>
                    <SelectItem value="365">1 year</SelectItem>
                    <SelectItem value="never">Never expires</SelectItem>
                  </SelectContent>
                </Select>
                <p className="text-xs text-muted-foreground">
                  When this API key will expire. Choose "Never expires" for persistent authentication.
                </p>
              </div>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={handleClose}>
                Cancel
              </Button>
              <Button type="submit" disabled={createApiKey.isPending || !name.trim()}>
                {createApiKey.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                Create API Key
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
