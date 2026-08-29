import { useState } from 'react';
import { EmptyState, ErrorState, LoadingState } from '../components/StateViews';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { ConnectionIndicator } from '../features/notification/components/ConnectionIndicator';
import { NotificationList } from '../features/notification/components/NotificationList';
import {
  useMarkNotificationRead,
  useNotifications,
} from '../features/notification/hooks/useNotifications';
import { useNotificationStream } from '../features/notification/hooks/useNotificationStream';

/**
 * §12.5 /notifications(需登入 + notification:read)。
 *
 * <p>清單一律以 Query 為真相來源;WebSocket 推播只負責讓它失效(§12.3 的狀態歸屬判準)。
 * 因此即使推播斷線,重新整理或下一次 refetch 仍然拿得到完整的通知——
 * 指示器誠實地說出「現在收不到即時通知」,而不是讓頁面看起來一切正常。
 */
export default function NotificationCenterPage() {
  const [unreadOnly, setUnreadOnly] = useState(false);
  const notifications = useNotifications({ unreadOnly });
  const markRead = useMarkNotificationRead();
  const stream = useNotificationStream();

  let list: React.ReactNode;
  if (notifications.isPending) {
    list = <LoadingState rows={4} label="載入通知" />;
  } else if (notifications.isError) {
    list = <ErrorState error={notifications.error} onRetry={() => void notifications.refetch()} />;
  } else if (notifications.data.items.length === 0) {
    list = (
      <EmptyState
        title={unreadOnly ? '沒有未讀通知' : '尚無通知'}
        description="新增 IOC、來源異常與方案異動都會出現在這裡。"
      />
    );
  } else {
    list = (
      <NotificationList
        notifications={notifications.data.items}
        markingId={markRead.isPending ? markRead.variables : null}
        onMarkRead={(id) => markRead.mutate(id)}
      />
    );
  }

  return (
    <section aria-labelledby="notifications-title" className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 id="notifications-title" className="font-mono text-xl font-bold tracking-tight">
          通知中心
        </h1>
        <ConnectionIndicator status={stream.status} />
      </div>

      <div className="flex items-center gap-2">
        <Button
          variant={unreadOnly ? 'default' : 'outline'}
          size="sm"
          aria-pressed={unreadOnly}
          onClick={() => setUnreadOnly((current) => !current)}
        >
          只看未讀
        </Button>
      </div>

      <Card className="p-6">{list}</Card>
    </section>
  );
}
