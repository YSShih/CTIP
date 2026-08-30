import type { ApiSchemas } from '../../api/client';

export type AuditLogDto = ApiSchemas['AuditLogDto'];

/**
 * 26 種稽核行為(docs/spec/04-data-dictionary.md §4.5)。
 * 供篩選下拉使用;值本身由後端的 CHECK 約束把關,前端只負責呈現。
 */
export const AUDIT_ACTIONS = [
  'LOGIN',
  'LOGIN_FAILED',
  'LOGOUT',
  'TOKEN_REFRESH',
  'TOKEN_REUSE_DETECTED',
  'API_ACCESS',
  'IOC_QUERY',
  'IOC_DOWNLOAD',
  'IOC_SUBMIT',
  'IOC_IMPORT',
  'IOC_REPORT_FP',
  'STIX_EXPORT',
  'SYNC_MANIFEST',
  'SYNC_BLOOM',
  'SYNC_DELTA',
  'INGESTION_STARTED',
  'INGESTION_COMPLETED',
  'INGESTION_FAILED',
  'ADMIN_ACTION',
  'TENANT_CREATED',
  'USER_CREATED',
  'API_KEY_CREATED',
  'API_KEY_REVOKED',
  'SUBSCRIPTION_CHANGED',
  'WEBHOOK_CREATED',
  'WEBHOOK_DELETED',
] as const;

export type AuditAction = (typeof AUDIT_ACTIONS)[number];

export interface AuditLogPageParams {
  cursor?: string;
  limit?: number;
  action?: string;
}
