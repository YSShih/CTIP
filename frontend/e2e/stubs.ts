import type { Page } from '@playwright/test';

/**
 * E2E 的 API 邊界替身。
 *
 * 沒有 `E2E_BASE_URL` 時後端不存在,因此在瀏覽器層攔截 `/api/v1/**`——測到的仍是真實的
 * bundle、路由、Query 快取與渲染,只有 HTTP 回應是固定的。指向整套環境(`E2E_BASE_URL`)時
 * 完全不安裝攔截,直接打真的後端。
 *
 * 回應形狀刻意與 `src/test/handlers.ts` 的 MSW fixture 一致:兩邊若各寫一套,
 * 契約漂移時只有一邊會紅。
 */
interface Stub {
  readonly method: string;
  readonly pattern: RegExp;
  readonly status?: number;
  readonly body: unknown;
}

export const STUB_IOC = {
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
  validUntil: null,
  attribution: [{ sourceName: 'Mock OpenPhish', homepage: 'https://example.test' }],
};

export const STUB_MANIFEST = {
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
};

/** 這個身分持有 M2-26 四個情境所需的權限(路由守衛與畫面都依它決定)。 */
export const STUB_SESSION = {
  accessToken: 'e2e-access-token',
  refreshToken: 'e2e-refresh-token',
  tokenType: 'Bearer',
  expiresIn: 900,
  user: {
    userId: '3f1b0c2e-9a4d-4c1a-8b77-2b0f1a9c5d10',
    tenantId: '8b1a9c33-2f4e-4d55-9f6a-0c1d2e3f4a5b',
    role: 'TENANT_ADMIN',
    permissions: [
      'ioc:read',
      'ioc:submit',
      'apikey:create',
      'apikey:revoke',
      'notification:read',
      'webhook:manage',
    ],
    displayName: 'Alice Analyst',
  },
};

export const STUB_ISSUED_KEY = {
  id: '9c7a1e42-5f3b-4a10-9d2c-7e8f0a1b2c3d',
  name: 'ci-pipeline',
  keyPrefix: 'aB3xY9kQ',
  scopes: ['ioc:read'],
  key: 'ctip_mvp_aB3xY9kQe2eOnlyShownOnce',
};

const IOC_PAGE = { items: [STUB_IOC], hasMore: false, nextCursor: null };

export const STUB_NOTIFICATION = {
  id: '6f1d2f52-6f0a-4a6f-9a0f-2f1b6d0a1c33',
  eventType: 'NEW_IOC',
  title: '新增 IOC:198.51.100.7',
  body: '型別 IPV4,TLP CLEAR',
  severity: 'MEDIUM',
  resourceType: 'indicator',
  resourceId: '1f0d2c4e-93a5-4f6b-8c1d-2e3a4b5c6d7e',
  read: false,
  createdAt: '2026-08-29T09:15:04Z',
};

const NOTIFICATION_PAGE = { items: [STUB_NOTIFICATION], hasMore: false, nextCursor: null };

const STUBS: readonly Stub[] = [
  { method: 'GET', pattern: /\/api\/v1\/sync\/manifest$/, body: STUB_MANIFEST },
  { method: 'GET', pattern: /\/api\/v1\/iocs(\?|$)/, body: IOC_PAGE },
  { method: 'GET', pattern: /\/api\/v1\/iocs\/[0-9a-f-]+$/, body: STUB_IOC },
  { method: 'GET', pattern: /\/api\/v1\/iocs\/[0-9a-f-]+\/sources$/, body: [] },
  { method: 'POST', pattern: /\/api\/v1\/iocs\/search$/, body: IOC_PAGE },
  { method: 'POST', pattern: /\/api\/v1\/iocs$/, status: 201, body: STUB_IOC },
  { method: 'POST', pattern: /\/api\/v1\/auth\/login$/, body: STUB_SESSION },
  { method: 'GET', pattern: /\/api\/v1\/api-keys$/, body: [] },
  { method: 'POST', pattern: /\/api\/v1\/api-keys$/, status: 201, body: STUB_ISSUED_KEY },
  {
    method: 'GET',
    pattern: /\/api\/v1\/stats\/summary$/,
    body: {
      totalActive: 254,
      byType: { DOMAIN: 120, IPV4: 80, URL: 54 },
      trend: [{ date: '2026-08-26', count: 17 }],
    },
  },
  { method: 'GET', pattern: /\/api\/v1\/stats\/sources$/, body: [] },
  { method: 'GET', pattern: /\/api\/v1\/notifications(\?|$)/, body: NOTIFICATION_PAGE },
  {
    method: 'PATCH',
    pattern: /\/api\/v1\/notifications\/[0-9a-f-]+\/read$/,
    status: 204,
    body: null,
  },
  { method: 'GET', pattern: /\/api\/v1\/webhooks$/, body: [] },
];

export async function stubApi(page: Page): Promise<void> {
  if (process.env.E2E_BASE_URL) return;
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const match = STUBS.find(
      (stub) => stub.method === request.method() && stub.pattern.test(request.url()),
    );
    if (!match) {
      // 明確回 404 而不是放行:漏掉的 stub 應該讓測試失敗,不該安靜地打到不存在的後端
      await route.fulfill({
        status: 404,
        json: { code: 'NOT_FOUND', message: `no stub for ${request.method()} ${request.url()}` },
      });
      return;
    }
    await route.fulfill({ status: match.status ?? 200, json: match.body });
  });
}
