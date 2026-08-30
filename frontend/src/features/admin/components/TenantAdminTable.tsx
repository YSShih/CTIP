import { useState } from 'react';
import { Badge } from '../../../components/ui/badge';
import { Button } from '../../../components/ui/button';
import { Select } from '../../../components/ui/select';
import type { AssignablePlanCode, TenantOverviewDto } from '../api/adminApi';

export interface TenantAdminTableProps {
  tenants: TenantOverviewDto[];
  assigningTenantId: string | null;
  onAssign: (tenantId: string, planCode: AssignablePlanCode) => void;
}

/** `CANCEL` 是保留值:取消目前訂閱(不變量 B3)。 */
const PLAN_CHOICES: AssignablePlanCode[] = ['FREE', 'PREMIUM', 'ENTERPRISE', 'CANCEL'];

export function TenantAdminTable({ tenants, assigningTenantId, onAssign }: TenantAdminTableProps) {
  const [selection, setSelection] = useState<Record<string, AssignablePlanCode>>({});

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <caption className="sr-only">平台上的全部租戶</caption>
        <thead className="text-xs uppercase tracking-[0.12em] text-muted-foreground">
          <tr>
            <th scope="col" className="py-2 pr-4">
              租戶
            </th>
            <th scope="col" className="py-2 pr-4">
              類型
            </th>
            <th scope="col" className="py-2 pr-4">
              狀態
            </th>
            <th scope="col" className="py-2 pr-4">
              方案
            </th>
            <th scope="col" className="py-2">
              指派方案
            </th>
          </tr>
        </thead>
        <tbody className="divide-y">
          {tenants.map((tenant) => {
            const tenantId = tenant.id ?? '';
            const chosen = selection[tenantId] ?? 'PREMIUM';
            return (
              <tr key={tenantId} data-testid="tenant-row">
                <td className="py-2 pr-4 font-mono text-xs">{tenant.slug}</td>
                <td className="py-2 pr-4 text-xs">{tenant.type}</td>
                <td className="py-2 pr-4">
                  <Badge variant={tenant.status === 'ACTIVE' ? 'ok' : 'warn'}>
                    {tenant.status}
                  </Badge>
                </td>
                <td className="py-2 pr-4 font-mono text-xs">{tenant.planCode}</td>
                <td className="flex items-center gap-2 py-2">
                  <label className="sr-only" htmlFor={`plan-${tenantId}`}>
                    {tenant.slug} 的方案
                  </label>
                  <Select
                    id={`plan-${tenantId}`}
                    value={chosen}
                    onChange={(event) =>
                      setSelection((current) => ({
                        ...current,
                        [tenantId]: event.target.value as AssignablePlanCode,
                      }))
                    }
                  >
                    {PLAN_CHOICES.map((plan) => (
                      <option key={plan} value={plan}>
                        {plan}
                      </option>
                    ))}
                  </Select>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={assigningTenantId === tenantId}
                    onClick={() => onAssign(tenantId, chosen)}
                  >
                    套用
                  </Button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
