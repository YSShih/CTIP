import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it } from 'vitest';
import { server } from '../test/server';
import { notFoundError, sampleIoc } from '../test/handlers';
import {
  ApiError,
  apiDelete,
  apiGet,
  apiPost,
  setAuthTokenProvider,
  setSessionRefresher,
} from './client';

afterEach(() => setAuthTokenProvider(() => null));

describe('apiGet', () => {
  it('returns parsed JSON on 200', async () => {
    const page = await apiGet('/api/v1/iocs', {
      query: { type: 'DOMAIN', limit: 20 },
    });
    expect(page.items).toHaveLength(2);
    expect((page.items as (typeof sampleIoc)[])[0].value).toBe(sampleIoc.value);
  });

  it('flattens object query params to wire format', async () => {
    let capturedUrl = '';
    server.use(
      http.get('*/api/v1/iocs', ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ items: [], hasMore: false });
      }),
    );
    await apiGet('/api/v1/iocs', { query: { type: 'DOMAIN', limit: 20 } });
    const url = new URL(capturedUrl);
    expect(url.searchParams.get('type')).toBe('DOMAIN');
    expect(url.searchParams.get('limit')).toBe('20');
    expect(url.searchParams.has('params')).toBe(false);
  });

  it('substitutes path parameters and encodes them', async () => {
    const ioc = await apiGet('/api/v1/iocs/{id}', { path: { id: sampleIoc.id } });
    expect(ioc.id).toBe(sampleIoc.id);
  });

  it('converts ErrorResponse to ApiError with code and traceId', async () => {
    const failing = apiGet('/api/v1/iocs/{id}', {
      path: { id: '00000000-0000-0000-0000-000000000000' },
    });
    await expect(failing).rejects.toBeInstanceOf(ApiError);
    const error = (await failing.catch((e: unknown) => e)) as ApiError;
    expect(error.status).toBe(404);
    expect(error.code).toBe(notFoundError.code);
    expect(error.traceId).toBe(notFoundError.traceId);
  });

  it('maps network failure to NETWORK_ERROR', async () => {
    server.use(http.get('*/api/v1/health', () => HttpResponse.error()));
    const failing = apiGet('/api/v1/health');
    const error = (await failing.catch((e: unknown) => e)) as ApiError;
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(0);
    expect(error.code).toBe('NETWORK_ERROR');
  });

  it('sends Authorization header when a token provider is set', async () => {
    let authHeader: string | null = null;
    server.use(
      http.get('*/api/v1/health', ({ request }) => {
        authHeader = request.headers.get('Authorization');
        return HttpResponse.json({ status: 'UP' });
      }),
    );
    setAuthTokenProvider(() => 'test-token');
    await apiGet('/api/v1/health');
    expect(authHeader).toBe('Bearer test-token');
  });

  it('omits undefined and empty query values', async () => {
    let capturedUrl = '';
    server.use(
      http.get('*/api/v1/iocs', ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ items: [], hasMore: false });
      }),
    );
    await apiGet('/api/v1/iocs', { query: { type: undefined, cursor: '' } });
    expect(new URL(capturedUrl).search).toBe('');
  });
});

describe('apiPost', () => {
  it('posts a JSON body and returns typed JSON', async () => {
    server.use(
      http.post('*/api/v1/iocs/lookup', async ({ request }) => {
        const body = (await request.json()) as { values?: string[] };
        return HttpResponse.json({
          results: (body.values ?? []).map((value) => ({ value, found: false })),
        });
      }),
    );
    const response = await apiPost('/api/v1/iocs/lookup', { values: ['203.0.113.7'] });
    expect(response.results).toHaveLength(1);
  });
});

describe('401 自動輪替', () => {
  afterEach(() => {
    setAuthTokenProvider(() => null);
    setSessionRefresher(null);
  });

  it('refreshes once and retries the original request', async () => {
    let calls = 0;
    const seenTokens: (string | null)[] = [];
    server.use(
      http.get('*/api/v1/health', ({ request }) => {
        calls += 1;
        seenTokens.push(request.headers.get('Authorization'));
        if (calls === 1) {
          return HttpResponse.json({ code: 'TOKEN_EXPIRED', message: 'expired' }, { status: 401 });
        }
        return HttpResponse.json({ status: 'UP' });
      }),
    );
    let current = 'stale-token';
    setAuthTokenProvider(() => current);
    setSessionRefresher(async () => {
      current = 'fresh-token';
      return current;
    });

    await expect(apiGet('/api/v1/health')).resolves.toEqual({ status: 'UP' });
    expect(seenTokens).toEqual(['Bearer stale-token', 'Bearer fresh-token']);
  });

  it('propagates the 401 when the refresher gives up', async () => {
    server.use(
      http.get('*/api/v1/health', () =>
        HttpResponse.json({ code: 'UNAUTHENTICATED', message: 'nope' }, { status: 401 }),
      ),
    );
    setAuthTokenProvider(() => 'stale-token');
    setSessionRefresher(async () => null);

    await expect(apiGet('/api/v1/health')).rejects.toMatchObject({
      status: 401,
      code: 'UNAUTHENTICATED',
    });
  });

  /** refresh token 單次使用:並行請求必須共用同一次輪替,否則會觸發重用偵測(不變量 U5)。 */
  it('coalesces concurrent refreshes into a single rotation', async () => {
    let refreshes = 0;
    let served = 0;
    server.use(
      http.get('*/api/v1/health', () => {
        served += 1;
        if (served <= 2) {
          return HttpResponse.json({ code: 'TOKEN_EXPIRED', message: 'expired' }, { status: 401 });
        }
        return HttpResponse.json({ status: 'UP' });
      }),
    );
    setAuthTokenProvider(() => 'stale-token');
    setSessionRefresher(async () => {
      refreshes += 1;
      return 'fresh-token';
    });

    await Promise.all([apiGet('/api/v1/health'), apiGet('/api/v1/health')]);
    expect(refreshes).toBe(1);
  });

  it('does not attempt a refresh for anonymous requests', async () => {
    let refreshes = 0;
    server.use(
      http.get('*/api/v1/health', () =>
        HttpResponse.json({ code: 'UNAUTHENTICATED', message: 'nope' }, { status: 401 }),
      ),
    );
    setAuthTokenProvider(() => null);
    setSessionRefresher(async () => {
      refreshes += 1;
      return 'fresh-token';
    });

    await expect(apiGet('/api/v1/health')).rejects.toMatchObject({ status: 401 });
    expect(refreshes).toBe(0);
  });
});

describe('apiDelete', () => {
  it('sends DELETE and tolerates an empty 204 body', async () => {
    let method = '';
    server.use(
      http.delete('*/api/v1/api-keys/:id', ({ request }) => {
        method = request.method;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    await expect(
      apiDelete('/api/v1/api-keys/{id}', { path: { id: 'abc' } }),
    ).resolves.toBeUndefined();
    expect(method).toBe('DELETE');
  });
});
