import { apiGet, type PageOf } from '../../../api/client';
import type { AuditLogDto, AuditLogPageParams } from '../types';

/**
 * 稽核軌跡(docs/spec/09-api.md §9.1「通知與稽核」的 `GET /audit-logs`,權限 `audit:read`)。
 * 範圍固定為呼叫者自己的租戶——沒有參數可以指定別人的。
 */
export function fetchAuditLogs(params: AuditLogPageParams = {}): Promise<PageOf<AuditLogDto>> {
  return apiGet('/api/v1/audit-logs', { query: params }) as Promise<PageOf<AuditLogDto>>;
}
