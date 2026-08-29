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

export const sampleThreat = {
  id: '5b8f9d2e-1c3a-4f7b-9e0d-2a4c6b8d0f13',
  type: 'MALWARE_FAMILY',
  name: 'AgentTesla',
  aliases: ['Agent Tesla'],
  description: 'Commodity infostealer distributed via phishing attachments.',
  severity: 'HIGH',
  confidence: 70,
  tlp: 'CLEAR',
  status: 'ACTIVE',
  firstSeen: '2026-01-15T00:00:00Z',
  lastSeen: '2026-08-20T00:00:00Z',
  tags: ['infostealer'],
  // 關聯總數 2,但下方 sampleThreatIndicators 只有 1 筆:另一筆不在匿名的可見範圍內
  indicatorCount: 2,
  externalReferences: [{ sourceName: 'mitre-attack', externalId: 'S0331' }],
} satisfies ApiSchemas['ThreatDto'];

export const secondThreat = {
  ...sampleThreat,
  id: '6c9f0e3f-2d4b-4a8c-8f1e-3b5d7c9e1f24',
  type: 'CAMPAIGN',
  name: 'Operation Nightjar',
  aliases: [],
  tlp: 'GREEN',
  severity: 'MEDIUM',
  indicatorCount: 0,
  externalReferences: [],
} satisfies ApiSchemas['ThreatDto'];

export const sampleThreatPage = {
  items: [sampleThreat, secondThreat],
  hasMore: false,
} satisfies PageOf<ApiSchemas['ThreatDto']>;

export const sampleThreatIndicators = [
  { role: 'C2', addedAt: '2026-08-20T10:00:00Z', ioc: sampleIoc },
] satisfies ApiSchemas['ThreatIndicatorDto'][];

export const sampleThreatStixObject = {
  type: 'malware',
  spec_version: '2.1',
  id: `malware--${sampleThreat.id}`,
  created: '2026-08-01T00:00:00.000Z',
  modified: '2026-08-20T00:00:00.000Z',
  name: sampleThreat.name,
  is_family: true,
  aliases: ['Agent Tesla'],
};

export const notFoundError = {
  code: 'NOT_FOUND',
  message: 'indicator not found',
  status: 404,
  traceId: '00000000000000000000000000000abc',
} satisfies ApiSchemas['ErrorResponse'];

type NotificationDto = ApiSchemas['NotificationDto'];
type WebhookDto = ApiSchemas['WebhookDto'];

export const sampleNotification = {
  id: '6f1d2f52-6f0a-4a6f-9a0f-2f1b6d0a1c33',
  eventType: 'NEW_IOC',
  title: '新增 IOC:198.51.100.7',
  body: '型別 IPV4,TLP CLEAR',
  severity: 'MEDIUM',
  resourceType: 'indicator',
  resourceId: '1f0d2c4e-93a5-4f6b-8c1d-2e3a4b5c6d7e',
  read: false,
  createdAt: '2026-08-29T09:15:04Z',
} satisfies NotificationDto;

export const readNotification = {
  ...sampleNotification,
  id: '7a2e3f63-7f1b-4b70-8b10-3f2c7e1b2d44',
  eventType: 'SOURCE_FAILURE',
  title: '來源已停用',
  severity: 'HIGH',
  read: true,
} satisfies NotificationDto;

export const sampleNotificationPage = {
  items: [sampleNotification, readNotification],
  nextCursor: undefined,
  hasMore: false,
} satisfies PageOf<NotificationDto>;

export const sampleWebhook = {
  id: '9d2b7d3e-1a44-4f0b-9a2f-0c1d2e3f4a5b',
  name: 'SOC pipeline',
  targetUrl: 'https://soc.example.test/hooks/ctip',
  eventTypes: ['NEW_IOC', 'IOC_REVOKED'],
  filter: { iocTypes: ['IPV4'], minSeverity: 'HIGH', tags: [], sourceIds: [] },
  status: 'ACTIVE',
  consecutiveFailures: 0,
  lastDeliveryAt: undefined,
  lastSuccessAt: undefined,
  createdAt: '2026-08-29T09:00:00Z',
} satisfies WebhookDto;

export const issuedWebhook = {
  secret: 'whsec-only-shown-once-0123456789',
  webhook: sampleWebhook,
} satisfies ApiSchemas['IssuedWebhookDto'];

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

export const sampleSubscription = {
  planCode: 'PREMIUM',
  planName: 'Premium',
  tier: 2,
  status: 'ACTIVE',
  provider: 'MANUAL',
  currentPeriodStart: '2026-08-01T00:00:00Z',
  currentPeriodEnd: '2027-08-01T00:00:00Z',
  cancelledAt: undefined,
  quotas: {
    requestsPerMinute: 1200,
    requestsPerDay: 500000,
    maxPageSize: 500,
    maxBatchLookup: 1000,
    minSyncIntervalSeconds: 300,
    publicBloomEnabled: true,
    tenantBloomCapacity: 1000000,
    websocketEnabled: true,
    maxWebhooks: 5,
    maxApiKeys: 10,
    customFeedEnabled: false,
    stixExportMaxObjects: 50000,
    maxManualSubmissionsPerDay: 1000,
    maxImportRowsPerFile: 10000,
  },
} satisfies ApiSchemas['SubscriptionDto'];

export const sampleUsage = {
  planCode: 'PREMIUM',
  manualSubmissionsToday: { used: 12, limit: 1000, resetAt: '2026-08-29T00:00:00Z' },
  apiKeys: { used: 2, limit: 10, resetAt: undefined },
} satisfies ApiSchemas['SubscriptionUsageDto'];

export const sampleImportJob = {
  importJobId: '0f2d7b3c-9a41-4a7e-8b2f-1c5d6e7f8a90',
  status: 'PENDING',
  format: 'CSV',
  totalRows: 3,
  acceptedCount: 0,
  mergedCount: 0,
  rejectedCount: 0,
  errorMessage: undefined,
  startedAt: undefined,
  finishedAt: undefined,
  createdAt: '2026-08-28T09:00:00Z',
} satisfies ApiSchemas['ImportJobDto'];

export const finishedImportJob = {
  ...sampleImportJob,
  status: 'PARTIAL',
  acceptedCount: 2,
  rejectedCount: 1,
  startedAt: '2026-08-28T09:00:01Z',
  finishedAt: '2026-08-28T09:00:05Z',
} satisfies ApiSchemas['ImportJobDto'];

export const sampleSyncManifest = {
  public: {
    scope: 'PUBLIC',
    datasetVersion: 128,
    bloomVersion: 42,
    fingerprintAlgorithm: 'SHA256',
    hashFunctionCount: 10,
    bitSize: 143775880,
    capacity: 10000000,
    falsePositiveRate: 0.001,
    memberCount: 8342119,
    checksum: '3f5a1c9d0e2b4a6f8c1d3e5a7b9c1d3e5a7b9c1d3e5a7b9c1d3e5a7b9c1d3e5a',
    sizeBytes: 17971985,
    compression: 'ZSTD',
    generatedAt: '2026-08-21T04:00:00Z',
    coverage: 'TLP:CLEAR only',
  },
  notCovered: ['TLP:GREEN'],
  maxDeltaChain: 24,
} satisfies ApiSchemas['SyncManifestDto'];

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
  http.get('*/api/v1/threats', () => HttpResponse.json(sampleThreatPage)),
  http.get('*/api/v1/threats/:id', ({ params }) => {
    if (params.id === sampleThreat.id) return HttpResponse.json(sampleThreat);
    if (params.id === secondThreat.id) return HttpResponse.json(secondThreat);
    return HttpResponse.json(notFoundError, { status: 404 });
  }),
  http.get('*/api/v1/threats/:id/indicators', ({ params }) => {
    if (params.id === sampleThreat.id) return HttpResponse.json(sampleThreatIndicators);
    if (params.id === secondThreat.id) return HttpResponse.json([]);
    return HttpResponse.json(notFoundError, { status: 404 });
  }),
  http.get('*/api/v1/stix/:stixId', ({ params }) => {
    if (params.stixId === sampleStixObject.id) return HttpResponse.json(sampleStixObject);
    if (params.stixId === sampleThreatStixObject.id) {
      return HttpResponse.json(sampleThreatStixObject);
    }
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
  http.post('*/api/v1/iocs', () => HttpResponse.json(sampleIoc, { status: 201 })),
  http.post('*/api/v1/iocs/import', () => HttpResponse.json(sampleImportJob, { status: 202 })),
  http.get('*/api/v1/iocs/import/:jobId', () => HttpResponse.json(finishedImportJob)),
  http.post('*/api/v1/iocs/:id/report-false-positive', () => HttpResponse.json(sampleIoc)),
  http.get('*/api/v1/subscription', () => HttpResponse.json(sampleSubscription)),
  http.get('*/api/v1/subscription/usage', () => HttpResponse.json(sampleUsage)),
  http.get('*/api/v1/sync/manifest', () => HttpResponse.json(sampleSyncManifest)),
  http.get('*/api/v1/notifications', () => HttpResponse.json(sampleNotificationPage)),
  http.patch('*/api/v1/notifications/:id/read', () => new HttpResponse(null, { status: 204 })),
  http.get('*/api/v1/webhooks', () => HttpResponse.json([sampleWebhook])),
  http.post('*/api/v1/webhooks', () => HttpResponse.json(issuedWebhook, { status: 201 })),
  http.delete('*/api/v1/webhooks/:id', () => new HttpResponse(null, { status: 204 })),
];
