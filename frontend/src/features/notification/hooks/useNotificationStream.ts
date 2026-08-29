import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { useAppSelector } from '../../../stores/hooks';
import {
  connectNotificationStream,
  defaultStreamUrl,
  type StreamMessage,
  type StreamStatus,
} from '../api/notificationStream';

export interface NotificationStream {
  status: StreamStatus;
  /** 最近一則推播;通知清單本身仍以 Query 為準(§12.3:重整後要重新取得的屬 Query)。 */
  lastMessage: StreamMessage | null;
}

/**
 * 訂閱通知推播,並在收到事件時讓 {@code ['notifications']} 失效。
 *
 * <p>推播<strong>不是</strong>清單的真相來源:它只負責「有新東西了」這個訊號,
 * 內容仍由 Query 重新取得(§12.3 的狀態歸屬判準)。這樣一來,推播漏掉的那幾則
 * 在下一次重新取得時就會補上,不會出現只存在於記憶體的通知。
 *
 * <p>未登入時不連線:握手需要 access token,無謂的 401 只會製造重連迴圈。
 */
export function useNotificationStream(): NotificationStream {
  const token = useAppSelector((state) => state.auth.accessToken);
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<StreamStatus>('offline');
  const [lastMessage, setLastMessage] = useState<StreamMessage | null>(null);
  const queryClientRef = useRef(queryClient);
  queryClientRef.current = queryClient;

  useEffect(() => {
    if (!token) {
      setStatus('offline');
      return;
    }
    const handle = connectNotificationStream(
      { url: defaultStreamUrl(), token },
      {
        onStatus: setStatus,
        onMessage: (message) => {
          setLastMessage(message);
          void queryClientRef.current.invalidateQueries({ queryKey: ['notifications'] });
        },
      },
    );
    return () => handle.stop();
  }, [token]);

  return { status, lastMessage };
}
