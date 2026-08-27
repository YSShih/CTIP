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

export const secondIoc = {
  ...sampleIoc,
  id: '2a1b3c4d-0000-4f6b-8c1d-2e3a4b5c6d7e',
  type: 'IPV4',
  value: '203.0.113.7',
  severity: 'MEDIUM',
  score: 18,
  tags: ['sample', 'c2'],
  attribution: [],
} satisfies IocDto;

export const sampleIocPage = {
  items: [sampleIoc, secondIoc],
  hasMore: true,
  nextCursor: 'cursor-page-2',
} satisfies PageOf<IocDto>;

export const sampleIocSources = [
  {
    sourceId: '6f0d2c4e-93a5-4f6b-8c1d-2e3a4b5c6d7e',
    sourceName: 'Mock OpenPhish',
    sourceConfidence: 60,
    sourceSeverity: 'HIGH',
    sourceTlp: 'CLEAR',
    sourceFirstSeen: '2026-08-01T00:00:00Z',
    sourceLastSeen: '2026-08-20T10:00:00Z',
    reportCount: 2,
    status: 'ACTIVE',
    redistributionPolicy: 'ATTRIBUTION_REQUIRED',
  },
] satisfies ApiSchemas['IocSourceDto'][];

export const sampleStatsSummary = {
  totalActive: 254,
  byType: { DOMAIN: 120, IPV4: 80, URL: 54 },
  trend: [
    { date: '2026-08-20', count: 10 },
    { date: '2026-08-21', count: 0 },
    { date: '2026-08-22', count: 34 },
    { date: '2026-08-23', count: 21 },
    { date: '2026-08-24', count: 8 },
    { date: '2026-08-25', count: 55 },
    { date: '2026-08-26', count: 17 },
  ],
} satisfies ApiSchemas['StatsSummaryDto'];

export const sampleSourceStats = [
  {
    sourceId: '6f0d2c4e-93a5-4f6b-8c1d-2e3a4b5c6d7e',
    sourceType: 'MOCK_OPENPHISH',
    displayName: 'Mock OpenPhish',
    status: 'ACTIVE',
    enabled: true,
    indicatorCount: 340,
    lastSuccessAt: '2026-08-26T03:00:00Z',
  },
  {
    sourceId: '7f0d2c4e-93a5-4f6b-8c1d-2e3a4b5c6d7e',
    sourceType: 'MANUAL',
    displayName: 'Manual submissions',
    status: 'ACTIVE',
    enabled: true,
    indicatorCount: 0,
  },
] satisfies ApiSchemas['SourceStatsDto'][];

export const sampleStixObject = {
  type: 'indicator',
  spec_version: '2.1',
  id: `indicator--${sampleIoc.id}`,
  created: '2026-08-01T00:00:00.000Z',
  modified: '2026-08-20T10:00:00.000Z',
  name: sampleIoc.value,
  pattern_type: 'stix',
  pattern: `[domain-name:value = '${sampleIoc.value}']`,
  valid_from: '2026-08-01T00:00:00.000Z',
};

export const notFoundError = {
  code: 'NOT_FOUND',
  message: 'indicator not found',
  status: 404,
  traceId: '00000000000000000000000000000abc',
} satisfies ApiSchemas['ErrorResponse'];

export const sampleSession = {
  accessToken: 'access-token-1',
  refreshToken: 'refresh-token-1',
  tokenType: 'Bearer',
  expiresIn: 900,
  user: {
    userId: '3f1b0c2e-9a4d-4c1a-8b77-2b0f1a9c5d10',
    tenantId: '8b1a9c33-2f4e-4d55-9f6a-0c1d2e3f4a5b',
    role: 'TENANT_ADMIN',
    permissions: ['ioc:read', 'ioc:export', 'stix:export', 'apikey:create', 'apikey:revoke'],
    displayName: 'Alice Analyst',
  },
} satisfies ApiSchemas['AuthResponse'];

export const sampleApiKey = {
  id: '9c7a1e42-5f3b-4a10-9d2c-7e8f0a1b2c3d',
  name: 'ci-pipeline',
  keyPrefix: 'aB3xY9kQ',
  scopes: ['ioc:read'],
  expiresAt: undefined,
  lastUsedAt: undefined,
  revokedAt: undefined,
  createdAt: '2026-08-27T07:00:00Z',
} satisfies ApiSchemas['ApiKeyDto'];

export const issuedApiKey = {
  key: 'ctip_mvp_aB3xY9kQ7fLm2pR8sT4uV6wX0yZ1cD5e',
  apiKey: sampleApiKey,
} satisfies ApiSchemas['IssuedApiKeyDto'];

const unauthenticatedError = {
  timestamp: '2026-08-27T08:00:00Z',
  status: 401,
  code: 'UNAUTHENTICATED',
  message: 'Invalid credentials',
  path: '/api/v1/auth/login',
  traceId: 'trace-401',
  details: [],
} satisfies ApiSchemas['ErrorResponse'];

export const handlers = [
  http.get('*/api/v1/health', () =>
    HttpResponse.json({ status: 'UP' } satisfies ApiSchemas['HealthDto']),
  ),
  http.get('*/api/v1/version', () =>
    HttpResponse.json({ version: '0.1.0', apiVersion: 'v1' } satisfies ApiSchemas['VersionDto']),
  ),
  http.get('*/api/v1/iocs', () => HttpResponse.json(sampleIocPage)),
  http.post('*/api/v1/iocs/search', () => HttpResponse.json(sampleIocPage)),
  http.get('*/api/v1/iocs/:id', ({ params }) => {
    if (params.id === sampleIoc.id) return HttpResponse.json(sampleIoc);
    return HttpResponse.json(notFoundError, { status: 404 });
  }),
  http.get('*/api/v1/iocs/:id/sources', ({ params }) => {
    if (params.id === sampleIoc.id) return HttpResponse.json(sampleIocSources);
    return HttpResponse.json(notFoundError, { status: 404 });
  }),
  http.get('*/api/v1/stats/summary', () => HttpResponse.json(sampleStatsSummary)),
  http.get('*/api/v1/stats/sources', () => HttpResponse.json(sampleSourceStats)),
  http.get('*/api/v1/stix/:stixId', ({ params }) => {
    if (params.stixId === sampleStixObject.id) return HttpResponse.json(sampleStixObject);
    return HttpResponse.json(notFoundError, { status: 404 });
  }),
  http.post('*/api/v1/auth/register', () => HttpResponse.json(sampleSession, { status: 201 })),
  http.post('*/api/v1/auth/login', async ({ request }) => {
    const body = (await request.json()) as ApiSchemas['LoginRequest'];
    if (body.password === 'wrong-password') {
      return HttpResponse.json(unauthenticatedError, { status: 401 });
    }
    return HttpResponse.json(sampleSession);
  }),
  http.post('*/api/v1/auth/refresh', () => HttpResponse.json(sampleSession)),
  http.post('*/api/v1/auth/logout', () => new HttpResponse(null, { status: 204 })),
  http.get('*/api/v1/api-keys', () => HttpResponse.json([sampleApiKey])),
  http.post('*/api/v1/api-keys', () => HttpResponse.json(issuedApiKey, { status: 201 })),
  http.delete('*/api/v1/api-keys/:id', () => new HttpResponse(null, { status: 204 })),
];
