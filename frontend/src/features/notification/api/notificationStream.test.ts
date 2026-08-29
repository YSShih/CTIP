import { describe, expect, it, vi } from 'vitest';
import {
  AUTH_SUBPROTOCOL,
  backoffDelay,
  connectNotificationStream,
  type StreamStatus,
  type WebSocketLike,
} from './notificationStream';

/** 可控的 WebSocket 替身:測試自己決定何時 open、何時斷線。 */
class FakeSocket implements WebSocketLike {
  onopen: ((event: unknown) => void) | null = null;
  onclose: ((event: unknown) => void) | null = null;
  onerror: ((event: unknown) => void) | null = null;
  onmessage: ((event: { data: unknown }) => void) | null = null;
  closed = false;

  close(): void {
    this.closed = true;
  }
}

interface Harness {
  sockets: FakeSocket[];
  protocols: string[][];
  statuses: StreamStatus[];
  messages: unknown[];
  timers: { delay: number; run: () => void }[];
}

function connect(overrides: Partial<Parameters<typeof connectNotificationStream>[0]> = {}) {
  const harness: Harness = {
    sockets: [],
    protocols: [],
    statuses: [],
    messages: [],
    timers: [],
  };
  const handle = connectNotificationStream(
    {
      url: 'ws://localhost/api/v1/ws',
      token: 'jwt-token',
      baseDelayMs: 1000,
      maxDelayMs: 30000,
      jitter: () => 1,
      socketFactory: (_url, protocols) => {
        const socket = new FakeSocket();
        harness.sockets.push(socket);
        harness.protocols.push(protocols);
        return socket;
      },
      schedule: (callback, delay) => {
        harness.timers.push({ delay, run: callback });
        return harness.timers.length;
      },
      cancel: () => undefined,
      ...overrides,
    },
    {
      onStatus: (status) => harness.statuses.push(status),
      onMessage: (message) => harness.messages.push(message),
    },
  );
  return { harness, handle };
}

describe('backoffDelay', () => {
  it('doubles per attempt and is capped', () => {
    expect(backoffDelay(0, 1000, 30000, 1)).toBe(1000);
    expect(backoffDelay(1, 1000, 30000, 1)).toBe(2000);
    expect(backoffDelay(2, 1000, 30000, 1)).toBe(4000);
    expect(backoffDelay(10, 1000, 30000, 1)).toBe(30000);
  });

  /** 抖動不是裝飾:沒有它,伺服器重啟時所有 client 會在同一毫秒一起重連。 */
  it('applies jitter between 50% and 100% of the exponential delay', () => {
    expect(backoffDelay(1, 1000, 30000, 0)).toBe(1000);
    expect(backoffDelay(1, 1000, 30000, 0.5)).toBe(1500);
    expect(backoffDelay(1, 1000, 30000, 1)).toBe(2000);
  });
});

describe('connectNotificationStream', () => {
  it('carries the token in the subprotocol and never in the URL', () => {
    const { harness } = connect();
    expect(harness.protocols[0]).toEqual([AUTH_SUBPROTOCOL, `${AUTH_SUBPROTOCOL}.jwt-token`]);
  });

  it('reports open once the socket connects', () => {
    const { harness } = connect();
    harness.sockets[0].onopen?.({});
    expect(harness.statuses).toEqual(['connecting', 'open']);
  });

  it('delivers parsed messages', () => {
    const { harness } = connect();
    harness.sockets[0].onopen?.({});
    harness.sockets[0].onmessage?.({
      data: JSON.stringify({ type: 'NEW_IOC', eventId: 'e1', payload: { title: 'x' } }),
    });
    expect(harness.messages).toHaveLength(1);
  });

  /** 壞掉的一則訊息不該讓整條連線失去即時性。 */
  it('drops malformed messages without disconnecting', () => {
    const { harness } = connect();
    harness.sockets[0].onopen?.({});
    harness.sockets[0].onmessage?.({ data: 'not json' });
    harness.sockets[0].onmessage?.({ data: JSON.stringify({ nope: true }) });
    expect(harness.messages).toHaveLength(0);
    expect(harness.sockets[0].closed).toBe(false);
  });

  it('reconnects with exponential backoff after the server closes', () => {
    const { harness } = connect();
    harness.sockets[0].onopen?.({});

    harness.sockets[0].onclose?.({});
    expect(harness.statuses.at(-1)).toBe('reconnecting');
    expect(harness.timers[0].delay).toBe(1000);

    harness.timers[0].run();
    expect(harness.sockets).toHaveLength(2);

    harness.sockets[1].onclose?.({});
    expect(harness.timers[1].delay).toBe(2000);
    harness.timers[1].run();
    harness.sockets[2].onclose?.({});
    expect(harness.timers[2].delay).toBe(4000);
  });

  /** 一次成功的連線把退避歸零——不然短暫的網路抖動會讓延遲永遠停在上限。 */
  it('resets the backoff after a successful reconnect', () => {
    const { harness } = connect();
    harness.sockets[0].onclose?.({});
    harness.timers[0].run();
    harness.sockets[1].onopen?.({});
    harness.sockets[1].onclose?.({});
    expect(harness.timers[1].delay).toBe(1000);
  });

  /** 建構子本身丟例外(例如被 CSP 擋掉)也要當成一次連線失敗,不得整個放棄。 */
  it('treats a throwing constructor as a failed attempt', () => {
    const factory = vi.fn(() => {
      throw new Error('blocked');
    });
    const { harness } = connect({ socketFactory: factory });
    expect(harness.statuses.at(-1)).toBe('reconnecting');
    expect(harness.timers).toHaveLength(1);
  });

  it('stops reconnecting once the handle is stopped', () => {
    const { harness, handle } = connect();
    harness.sockets[0].onopen?.({});
    handle.stop();
    expect(harness.sockets[0].closed).toBe(true);
    expect(harness.statuses.at(-1)).toBe('offline');

    harness.sockets[0].onclose?.({});
    expect(harness.timers).toHaveLength(0);
  });
});
