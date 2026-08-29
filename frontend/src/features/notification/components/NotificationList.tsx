import { Badge } from '../../../components/ui/badge';
import { Button } from '../../../components/ui/button';
import type { NotificationDto } from '../api/notificationApi';

export interface NotificationListProps {
  notifications: NotificationDto[];
  markingId: string | null;
  onMarkRead: (id: string) => void;
}

const SEVERITY_TONE: Record<string, 'muted' | 'outline' | 'warn' | 'danger'> = {
  INFO: 'muted',
  LOW: 'muted',
  MEDIUM: 'outline',
  HIGH: 'warn',
  CRITICAL: 'danger',
};

function formatInstant(value: string | undefined | null): string {
  return value ? new Date(value).toLocaleString() : '—';
}

export function NotificationList({ notifications, markingId, onMarkRead }: NotificationListProps) {
  return (
    <ul className="divide-y" aria-label="通知清單">
      {notifications.map((notification) => (
        <li
          key={notification.id}
          className="flex items-start justify-between gap-4 py-3"
          data-testid="notification-item"
          data-read={notification.read ? 'true' : 'false'}
        >
          <div className="min-w-0 space-y-1">
            <div className="flex flex-wrap items-center gap-2">
              {!notification.read ? (
                <span
                  aria-label="未讀"
                  className="inline-block size-2 rounded-full bg-primary"
                  data-testid="unread-dot"
                />
              ) : null}
              <span className="font-medium">{notification.title}</span>
              <Badge variant={SEVERITY_TONE[notification.severity ?? 'INFO'] ?? 'muted'}>
                {notification.severity}
              </Badge>
              <Badge variant="outline">{notification.eventType}</Badge>
            </div>
            {notification.body ? (
              <p className="text-sm text-muted-foreground">{notification.body}</p>
            ) : null}
            <p className="font-mono text-xs text-muted-foreground">
              {formatInstant(notification.createdAt)}
            </p>
          </div>
          {!notification.read ? (
            <Button
              variant="ghost"
              size="sm"
              disabled={markingId === notification.id}
              onClick={() => onMarkRead(notification.id!)}
            >
              標為已讀
            </Button>
          ) : null}
        </li>
      ))}
    </ul>
  );
}
