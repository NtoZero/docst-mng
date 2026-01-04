'use client';

import { useState } from 'react';
import { ApiKeyList } from '@/components/settings/api-key-list';
import { ApiKeyFormDialog } from '@/components/settings/api-key-form-dialog';

export default function ApiKeysSettingsPage() {
  const [dialogOpen, setDialogOpen] = useState(false);

  return (
    <div className="container mx-auto py-8 space-y-6 max-w-6xl">
      <div>
        <h1 className="text-3xl font-bold">API Keys</h1>
        <p className="text-muted-foreground mt-2">
          Manage API keys for MCP client authentication (Claude Desktop, Claude Code).
          API keys provide persistent authentication without expiration.
        </p>
      </div>

      <ApiKeyList onCreateClick={() => setDialogOpen(true)} />

      <ApiKeyFormDialog open={dialogOpen} onOpenChange={setDialogOpen} />

      <div className="rounded-lg border bg-card p-6 space-y-4">
        <h2 className="text-lg font-semibold">How to use API Keys</h2>

        <div className="space-y-4 text-sm">
          <div>
            <h3 className="font-medium mb-2">Claude Desktop</h3>
            <p className="text-muted-foreground mb-2">
              Claude Desktop requires a stdio proxy. Add this to your configuration file:
            </p>
            <pre className="bg-muted p-4 rounded-md overflow-x-auto">
              <code>{`{
  "mcpServers": {
    "docst": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-client-http",
        "http://localhost:8342/mcp",
        "--header",
        "X-API-Key: <YOUR_API_KEY>"
      ]
    }
  }
}`}</code>
            </pre>
            <p className="text-xs text-muted-foreground mt-2">
              Note: Claude Desktop only supports stdio transport. The <code className="bg-muted px-1 rounded">mcp-client-http</code> package acts as a proxy.
            </p>
          </div>

          <div>
            <h3 className="font-medium mb-2">Claude Code - CLI Command</h3>
            <p className="text-muted-foreground mb-2">
              Run this command in your terminal:
            </p>
            <pre className="bg-muted p-4 rounded-md overflow-x-auto">
              <code>{`claude mcp add docst \\
  --url http://localhost:8342/mcp \\
  --header "X-API-Key: <YOUR_API_KEY>"`}</code>
            </pre>
          </div>

          <div>
            <h3 className="font-medium mb-2">Claude Code - Configuration File</h3>
            <p className="text-muted-foreground mb-2">
              Or create <code className="bg-muted px-1 rounded">.mcp.json</code> in your project root:
            </p>
            <pre className="bg-muted p-4 rounded-md overflow-x-auto">
              <code>{`{
  "mcpServers": {
    "docst": {
      "type": "http",
      "url": "http://localhost:8342/mcp",
      "headers": {
        "X-API-Key": "<YOUR_API_KEY>"
      }
    }
  }
}`}</code>
            </pre>
          </div>

          <div>
            <h3 className="font-medium mb-2">Security Best Practices</h3>
            <ul className="list-disc list-inside space-y-1 text-muted-foreground">
              <li>Never commit API keys to version control</li>
              <li>Store API keys securely (e.g., in password managers)</li>
              <li>Revoke unused or compromised keys immediately</li>
              <li>Use expiring keys for temporary access</li>
              <li>Create separate keys for different clients</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
}
