import type { components, paths } from './generated/schema';

/**
 * API 薄包裝(12 §12.4):baseURL、認證標頭、錯誤轉換。
 * 型別全部由 generated schema 推導,不手寫後端型別。
 */

export type ApiSchemas = components['schemas'];
export type ErrorResponse = ApiSchemas['ErrorResponse'];

/**
 * PageResponse.items 在 openapi 中為 untyped(泛型於 runtime 擦除),
 * 此為規格允許的「窄化」:結構仍來自 generated。
 */
export type PageOf<T> = Omit<ApiSchemas['PageResponse'], 'items'> & { items: T[] };

type OpsOf<M extends 'get' | 'post'> = {
  [P in keyof paths]: paths[P][M] extends { responses: unknown } ? P : never;
}[keyof paths];

type Op<P extends keyof paths, M extends keyof paths[P]> = NonNullable<paths[P][M]>;

type OkJson<O> = O extends { responses: { 200: { content: { 'application/json': infer J } } } }
  ? J
  : never;

type QueryOf<O> = O extends { parameters: { query?: infer Q } } ? Q : never;

type BodyOf<O> = O extends { requestBody: { content: { 'application/json': infer B } } }
  ? B
  : never;

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly traceId: string | undefined;
  readonly details: ApiSchemas['FieldIssue'][] | undefined;

  constructor(
    status: number,
    code: string,
    message: string,
    traceId?: string,
    details?: ApiSchemas['FieldIssue'][],
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.traceId = traceId;
    this.details = details;
  }
}

type TokenProvider = () => string | null;

let tokenProvider: TokenProvider = () => null;

/** 由 app 層注入(讀 authSlice);api 層不得 import stores(§12.2 分層)。 */
export function setAuthTokenProvider(provider: TokenProvider): void {
  tokenProvider = provider;
}

function baseUrl(): string {
  const configured = import.meta.env.VITE_API_URL as string | undefined;
  if (configured) return configured.replace(/\/$/, '');
  return typeof window === 'undefined' ? '' : window.location.origin;
}

function appendQuery(search: URLSearchParams, key: string, value: unknown): void {
  if (value === undefined || value === null || value === '') return;
  if (Array.isArray(value)) {
    for (const item of value) appendQuery(search, key, item);
    return;
  }
  if (typeof value === 'object') {
    // springdoc 將 record 參數包成單一物件(如 IocListParams);wire 實際為攤平欄位。
    for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
      appendQuery(search, k, v);
    }
    return;
  }
  search.append(key, String(value));
}

function buildUrl(
  template: string,
  pathParams: Record<string, string> | undefined,
  query: Record<string, unknown> | undefined,
): string {
  const path = template.replace(/\{(\w+)\}/g, (_, name: string) => {
    const value = pathParams?.[name];
    if (value === undefined) throw new Error(`Missing path parameter: ${name}`);
    return encodeURIComponent(value);
  });
  const search = new URLSearchParams();
  if (query) {
    for (const [key, value] of Object.entries(query)) appendQuery(search, key, value);
  }
  const qs = search.toString();
  return `${baseUrl()}${path}${qs ? `?${qs}` : ''}`;
}

async function toApiError(response: Response): Promise<ApiError> {
  let body: ErrorResponse | undefined;
  try {
    body = (await response.json()) as ErrorResponse;
  } catch {
    body = undefined;
  }
  return new ApiError(
    response.status,
    body?.code ?? 'UNKNOWN',
    body?.message ?? `HTTP ${response.status}`,
    body?.traceId,
    body?.details,
  );
}

async function request<T>(url: string, init: RequestInit): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  const token = tokenProvider();
  if (token) headers.set('Authorization', `Bearer ${token}`);

  let response: Response;
  try {
    response = await fetch(url, { ...init, headers });
  } catch (cause) {
    throw new ApiError(0, 'NETWORK_ERROR', cause instanceof Error ? cause.message : '連線失敗');
  }
  if (!response.ok) throw await toApiError(response);
  return (await response.json()) as T;
}

interface GetOptions<O> {
  path?: Record<string, string>;
  query?: QueryOf<O>;
  signal?: AbortSignal;
}

export async function apiGet<P extends OpsOf<'get'>>(
  path: P,
  options: GetOptions<Op<P, 'get'>> = {},
): Promise<OkJson<Op<P, 'get'>>> {
  const url = buildUrl(path, options.path, options.query as Record<string, unknown> | undefined);
  return request(url, { method: 'GET', signal: options.signal ?? null });
}

interface PostOptions {
  path?: Record<string, string>;
  signal?: AbortSignal;
}

export async function apiPost<P extends OpsOf<'post'>>(
  path: P,
  body: BodyOf<Op<P, 'post'>>,
  options: PostOptions = {},
): Promise<OkJson<Op<P, 'post'>>> {
  const url = buildUrl(path, options.path, undefined);
  return request(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal: options.signal ?? null,
  });
}
