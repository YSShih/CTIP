import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it } from 'vitest';
import { server } from '../test/server';
import { notFoundError, sampleIoc } from '../test/handlers';
import { ApiError, apiGet, apiPost, setAuthTokenProvider } from './client';

afterEach(() => setAuthTokenProvider(() => null));

describe('apiGet', () => {
  it('returns parsed JSON on 200', async () => {
    const page = await apiGet('/api/v1/iocs', {
      query: { params: { type: 'DOMAIN', limit: 20 } },
    });
    expect(page.items).toHaveLength(1);
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
    await apiGet('/api/v1/iocs', { query: { params: { type: 'DOMAIN', limit: 20 } } });
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
    await apiGet('/api/v1/iocs', { query: { params: { type: undefined, cursor: '' } } });
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
