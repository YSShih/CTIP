/**
 * 通知即時推送的連線管理(§9.1「即時推送」)。
 *
 * <p>刻意是一個<strong>不依賴 React</strong> 的小狀態機:重連退避與 fallback 的行為
 * 是這一段程式碼裡最容易寫錯的部分,把它從 hook 裡分出來才驗得到(§14 的可測試性)。
 */

export type StreamStatus = 'connecting' | 'open' | 'reconnecting' | 'offline';

export interface StreamMessage {
  type: string;
  eventId: string;
  payload: {
    id: string;
    title: string;
    body: string | null;
    severity: string;
    resourceType: string | null;
    resourceId: string | null;
    createdAt: string;
  };
}

export interface StreamCallbacks {
  onStatus(status: StreamStatus): void;
  onMessage(message: StreamMessage): void;
}

export interface StreamOptions {
  /** WebSocket 端點(預設由 VITE_WS_URL / 目前來源推導)。 */
  url: string;
  /** access token;走 Sec-WebSocket-Protocol 攜帶(瀏覽器的 WS API 無法設標頭)。 */
  token: string;
  /** 退避基準,預設 1000ms。 */
  baseDelayMs?: number;
  /** 退避上限,預設 30000ms。 */
  maxDelayMs?: number;
  /** 供測試注入;預設為全域的 WebSocket / setTimeout。 */
  socketFactory?: (url: string, protocols: string[]) => WebSocketLike;
  schedule?: (callback: () => void, delayMs: number) => number;
  cancel?: (handle: number) => void;
  /** 退避抖動,回傳 0–1;預設 Math.random。 */
  jitter?: () => number;
}

/** {@link WebSocket} 需要的最小介面;測試以此替身注入。 */
export interface WebSocketLike {
  close(): void;
  onopen: ((event: unknown) => void) | null;
  onclose: ((event: unknown) => void) | null;
  onerror: ((event: unknown) => void) | null;
  onmessage: ((event: { data: unknown }) => void) | null;
}

export interface StreamHandle {
  stop(): void;
}

/**
 * 伺服器選定的子協定。token 走 {@code ctip.auth.<jwt>},但同時提供不帶 token 的
 * {@code ctip.auth} 讓伺服器有東西可以回選——回應標頭會進反向代理與瀏覽器的 log,
 * 不該把 token 原樣送回來。
 */
export const AUTH_SUBPROTOCOL = 'ctip.auth';

/** 由 VITE_WS_URL 推導端點;沒設定時用目前來源換成 ws/wss。 */
export function defaultStreamUrl(): string {
  const configured = import.meta.env.VITE_WS_URL as string | undefined;
  const origin = configured?.replace(/\/$/, '') ?? currentOrigin();
  return `${origin}/api/v1/ws`;
}

function currentOrigin(): string {
  if (typeof window === 'undefined') return '';
  return window.location.origin.replace(/^http/, 'ws');
}

/**
 * 退避序列:{@code base * 2^attempt},上限 {@code maxDelayMs},再乘上 0.5–1.0 的抖動。
 *
 * <p>抖動不是裝飾:伺服器重啟時所有 client 會在同一瞬間斷線,沒有抖動的話它們會
 * 一起在同一毫秒重連,把剛起來的伺服器再打掛一次。
 */
export function backoffDelay(
  attempt: number,
  baseDelayMs: number,
  maxDelayMs: number,
  jitter: number,
): number {
  const exponential = Math.min(baseDelayMs * 2 ** attempt, maxDelayMs);
  return Math.round(exponential * (0.5 + 0.5 * jitter));
}

/**
 * 連上通知串流,斷線後自動以指數退避重連,直到 {@link StreamHandle#stop} 被呼叫。
 *
 * <p>不做「重試幾次就放棄」:通知是長時間開著的頁面,使用者闔上筆電再打開時
 * 應該自己接回來,而不是要求他重新整理。狀態改為 {@code reconnecting} 已經
 * 足以讓 UI 誠實地說出目前收不到即時通知。
 */
export function connectNotificationStream(
  options: StreamOptions,
  callbacks: StreamCallbacks,
): StreamHandle {
  const baseDelayMs = options.baseDelayMs ?? 1000;
  const maxDelayMs = options.maxDelayMs ?? 30000;
  const socketFactory =
    options.socketFactory ??
    ((url, protocols) => new WebSocket(url, protocols) as unknown as WebSocketLike);
  const schedule =
    options.schedule ?? ((callback, delayMs) => window.setTimeout(callback, delayMs));
  const cancel = options.cancel ?? ((handle) => window.clearTimeout(handle));
  const jitter = options.jitter ?? Math.random;

  let stopped = false;
  let attempt = 0;
  let socket: WebSocketLike | null = null;
  let timer: number | null = null;

  function open(): void {
    if (stopped) return;
    callbacks.onStatus(attempt === 0 ? 'connecting' : 'reconnecting');
    let created: WebSocketLike;
    try {
      created = socketFactory(options.url, [
        AUTH_SUBPROTOCOL,
        `${AUTH_SUBPROTOCOL}.${options.token}`,
      ]);
    } catch {
      // WebSocket 建構子本身就丟例外(例如被 CSP 擋掉);當成一次連線失敗處理
      scheduleRetry();
      return;
    }
    socket = created;

    created.onopen = () => {
      if (stopped) return;
      attempt = 0;
      callbacks.onStatus('open');
    };
    created.onmessage = (event) => {
      if (stopped || typeof event.data !== 'string') return;
      const parsed = parseMessage(event.data);
      if (parsed) callbacks.onMessage(parsed);
    };
    created.onerror = () => {
      // onerror 之後一定會有 onclose;重連只由 onclose 觸發,避免同一次斷線排兩個計時器
    };
    created.onclose = () => {
      if (stopped) return;
      socket = null;
      scheduleRetry();
    };
  }

  function scheduleRetry(): void {
    callbacks.onStatus('reconnecting');
    const delay = backoffDelay(attempt, baseDelayMs, maxDelayMs, jitter());
    attempt += 1;
    timer = schedule(() => {
      timer = null;
      open();
    }, delay);
  }

  open();

  return {
    stop(): void {
      stopped = true;
      if (timer !== null) cancel(timer);
      timer = null;
      socket?.close();
      socket = null;
      callbacks.onStatus('offline');
    },
  };
}

/** 壞掉的訊息只丟棄不中斷連線——一則訊息的格式問題不該讓整個頁面失去即時性。 */
function parseMessage(raw: string): StreamMessage | null {
  try {
    const parsed = JSON.parse(raw) as Partial<StreamMessage>;
    if (typeof parsed.type !== 'string' || typeof parsed.eventId !== 'string') return null;
    return parsed as StreamMessage;
  } catch {
    return null;
  }
}
