import { http, HttpResponse } from 'msw';
import type { ApiSchemas, PageOf } from '../api/client';

/**
 * MSW handlers(12 §12.8):response 形狀由 generated 型別驅動(satisfies),
 * 後端契約漂移時在 tsc 就會爆,不得手寫自由形狀。
 */

type IocDto = ApiSchemas['IocDto'];

export const sampleIoc = {
  id: '1f0d2c4e-93a5-4f6b-8c1d-2e3a4b5c6d7e',
  type: 'DOMAIN',
  value: 'mal-8.ctip-sample.net',
  tlp: 'CLEAR',
  severity: 'HIGH',
  status: 'ACTIVE',
  confidence: 60,
  score: 42,
  sourceCount: 1,
  tags: ['sample', 'phishing'],
  firstSeen: '2026-08-01T00:00:00Z',
  lastSeen: '2026-08-20T10:00:00Z',
  validUntil: '2026-11-18T10:00:00Z',
  attribution: [{ sourceName: 'Mock OpenPhish', homepage: 'https://example.test' }],
} satisfies IocDto;

export const sampleIocPage = {
  items: [sampleIoc],
  hasMore: false,
  nextCursor: undefined,
} satisfies PageOf<IocDto>;

export const notFoundError = {
  code: 'NOT_FOUND',
  message: 'indicator not found',
  status: 404,
  traceId: '00000000000000000000000000000abc',
} satisfies ApiSchemas['ErrorResponse'];

export const handlers = [
  http.get('*/api/v1/health', () =>
    HttpResponse.json({ status: 'UP' } satisfies ApiSchemas['HealthDto']),
  ),
  http.get('*/api/v1/version', () =>
    HttpResponse.json({ version: '0.1.0', apiVersion: 'v1' } satisfies ApiSchemas['VersionDto']),
  ),
  http.get('*/api/v1/iocs', () => HttpResponse.json(sampleIocPage)),
  http.get('*/api/v1/iocs/:id', ({ params }) => {
    if (params.id === sampleIoc.id) return HttpResponse.json(sampleIoc);
    return HttpResponse.json(notFoundError, { status: 404 });
  }),
];
