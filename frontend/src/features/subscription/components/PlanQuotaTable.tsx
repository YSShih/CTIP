import type { PlanQuotasDto } from '../api/subscriptionApi';

export interface PlanQuotaTableProps {
  quotas: PlanQuotasDto;
}

/**
 * §10.6 的 14 個配額維度。
 *
 * <p>{@code null} = 無限制、{@code 0} = 停用——兩者必須顯示成不同的東西:
 * 把「依合約」印成 0 會讓 ENTERPRISE 看起來什麼都不能用。
 */
const ROWS: ReadonlyArray<{ key: keyof PlanQuotasDto; label: string; kind: 'number' | 'boolean' }> =
  [
    { key: 'requestsPerMinute', label: '請求／分鐘', kind: 'number' },
    { key: 'requestsPerDay', label: '請求／日', kind: 'number' },
    { key: 'maxPageSize', label: '單次分頁上限', kind: 'number' },
    { key: 'maxBatchLookup', label: '批次驗證單次上限', kind: 'number' },
    { key: 'minSyncIntervalSeconds', label: '同步最短間隔(秒)', kind: 'number' },
    { key: 'publicBloomEnabled', label: 'Public Bloom', kind: 'boolean' },
    { key: 'tenantBloomCapacity', label: 'Tenant Bloom 容量', kind: 'number' },
    { key: 'websocketEnabled', label: 'WebSocket', kind: 'boolean' },
    { key: 'maxWebhooks', label: 'Webhook 數量', kind: 'number' },
    { key: 'maxApiKeys', label: 'API Key 數量', kind: 'number' },
    { key: 'customFeedEnabled', label: '自訂 feed', kind: 'boolean' },
    { key: 'stixExportMaxObjects', label: 'STIX bundle 匯出上限', kind: 'number' },
    { key: 'maxManualSubmissionsPerDay', label: '手動提交／日', kind: 'number' },
    { key: 'maxImportRowsPerFile', label: '單檔匯入筆數上限', kind: 'number' },
  ];

export function formatQuota(value: PlanQuotasDto[keyof PlanQuotasDto], kind: string): string {
  if (kind === 'boolean') return value === true ? '✓' : '✗';
  if (value === null || value === undefined) return '無限制';
  if (value === 0) return '停用';
  return String(value);
}

export function PlanQuotaTable({ quotas }: PlanQuotaTableProps) {
  return (
    <table className="w-full text-sm">
      <caption className="sr-only">方案配額</caption>
      <tbody>
        {ROWS.map((row) => (
          <tr key={String(row.key)} className="border-b last:border-0">
            <th scope="row" className="py-2 text-left font-normal text-muted-foreground">
              {row.label}
            </th>
            <td className="py-2 text-right font-mono">{formatQuota(quotas[row.key], row.kind)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
