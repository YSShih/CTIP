import { useState } from 'react';
import { ApiError } from '../api/client';
import { EmptyState, ErrorState, LoadingState } from '../components/StateViews';
import { Card } from '../components/ui/card';
import { IssuedSecretNotice } from '../features/notification/components/IssuedSecretNotice';
import { WebhookCreateForm } from '../features/notification/components/WebhookCreateForm';
import { WebhookTable } from '../features/notification/components/WebhookTable';
import {
  useCreateWebhook,
  useDeleteWebhook,
  useWebhooks,
} from '../features/notification/hooks/useWebhooks';

/**
 * §12.5 /settings/webhooks(需登入 + webhook:manage)。
 *
 * <p>[09](../../docs/spec/09-api.md) 有三個 `/webhooks` 端點與 `webhook:manage` 權限,
 * 而 12 §12.5 的頁面表原本沒有對應頁(ADR 0022 的孤兒交付物)——本頁補上。
 */
export default function WebhooksPage() {
  const webhooks = useWebhooks();
  const createMutation = useCreateWebhook();
  const deleteMutation = useDeleteWebhook();
  const [issuedSecret, setIssuedSecret] = useState<string | null>(null);

  let list: React.ReactNode;
  if (webhooks.isPending) {
    list = <LoadingState rows={3} label="載入 webhook" />;
  } else if (webhooks.isError) {
    list = <ErrorState error={webhooks.error} onRetry={() => void webhooks.refetch()} />;
  } else if (webhooks.data.length === 0) {
    list = (
      <EmptyState
        title="尚未建立任何 webhook"
        description="建立後,符合訂閱條件的事件會即時送到你的端點。"
      />
    );
  } else {
    list = (
      <WebhookTable
        webhooks={webhooks.data}
        deletingId={deleteMutation.isPending ? deleteMutation.variables : null}
        onDelete={(id) => deleteMutation.mutate(id)}
      />
    );
  }

  return (
    <section aria-labelledby="webhooks-title" className="space-y-4">
      <h1 id="webhooks-title" className="font-mono text-xl font-bold tracking-tight">
        Webhook 管理
      </h1>

      {issuedSecret ? <IssuedSecretNotice secret={issuedSecret} /> : null}

      <Card className="p-6">
        <h2 className="mb-4 text-sm font-semibold uppercase tracking-[0.12em] text-muted-foreground">
          建立新的 webhook
        </h2>
        <WebhookCreateForm
          submitting={createMutation.isPending}
          error={createMutation.error instanceof ApiError ? createMutation.error : null}
          onSubmit={(values) =>
            createMutation.mutate(values, {
              onSuccess: (issued) => setIssuedSecret(issued.secret ?? null),
            })
          }
        />
      </Card>

      <Card className="p-6">{list}</Card>
    </section>
  );
}
