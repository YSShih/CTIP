import { useState } from 'react';
import { ApiError } from '../api/client';
import { EmptyState, ErrorState, LoadingState } from '../components/StateViews';
import { Card } from '../components/ui/card';
import { ApiKeyCreateForm, GRANTABLE_SCOPES } from '../features/apikey/components/ApiKeyCreateForm';
import { ApiKeyTable } from '../features/apikey/components/ApiKeyTable';
import { IssuedKeyNotice } from '../features/apikey/components/IssuedKeyNotice';
import { useApiKeys, useCreateApiKey, useRevokeApiKey } from '../features/apikey/hooks/useApiKeys';
import { useAppSelector } from '../stores/hooks';

/**
 * §12.5 /settings/api-keys(需登入 + apikey:create)。
 * 可勾選的 scope 限縮為「自己也有的權限」——後端仍以不變量 K4 再擋一次。
 */
export default function ApiKeysPage() {
  const permissions = useAppSelector((state) => state.auth.permissions);
  const keys = useApiKeys();
  const createMutation = useCreateApiKey();
  const revokeMutation = useRevokeApiKey();
  const [issuedKey, setIssuedKey] = useState<string | null>(null);

  const availableScopes = GRANTABLE_SCOPES.filter((scope) => permissions.includes(scope));

  let list: React.ReactNode;
  if (keys.isPending) {
    list = <LoadingState rows={3} label="載入 API key" />;
  } else if (keys.isError) {
    list = <ErrorState error={keys.error} onRetry={() => void keys.refetch()} />;
  } else if (keys.data.length === 0) {
    list = <EmptyState title="尚未建立任何 API key" description="建立後可用於機器對機器存取。" />;
  } else {
    list = (
      <ApiKeyTable
        keys={keys.data}
        revokingId={revokeMutation.isPending ? revokeMutation.variables : null}
        onRevoke={(id) => revokeMutation.mutate(id)}
      />
    );
  }

  return (
    <section aria-labelledby="api-keys-title" className="space-y-4">
      <h1 id="api-keys-title" className="font-mono text-xl font-bold tracking-tight">
        API Key 管理
      </h1>

      {issuedKey ? <IssuedKeyNotice fullKey={issuedKey} /> : null}

      <Card className="p-6">
        <h2 className="mb-4 text-sm font-semibold uppercase tracking-[0.12em] text-muted-foreground">
          建立新的 API key
        </h2>
        <ApiKeyCreateForm
          submitting={createMutation.isPending}
          error={createMutation.error instanceof ApiError ? createMutation.error : null}
          availableScopes={availableScopes}
          onSubmit={(values) =>
            createMutation.mutate(values, {
              onSuccess: (issued) => setIssuedKey(issued.key ?? null),
            })
          }
        />
      </Card>

      <Card className="p-6">{list}</Card>
    </section>
  );
}
