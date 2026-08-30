import { useState } from 'react';
import { ErrorState, LoadingState } from '../components/StateViews';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { DataSubjectPanel } from '../features/admin/components/DataSubjectPanel';
import { TenantAdminTable } from '../features/admin/components/TenantAdminTable';
import {
  useAssignPlan,
  useDataSubject,
  useEraseDataSubject,
  useRebuildStix,
  useTenants,
} from '../features/admin/hooks/useAdmin';

/**
 * §12.5 /admin(需登入 + `system:admin`)。
 *
 * 收攏 [09](../../docs/spec/09-api.md) §9.1「管理」的端點與 13 §13.4 的資料主體操作。
 * 每一次操作在後端都會留下 `ADMIN_ACTION` 稽核(13 §13.5),前端不需要也不應該自己記。
 */
export default function AdminPage() {
  const tenants = useTenants();
  const assignPlan = useAssignPlan();
  const rebuildStix = useRebuildStix();
  const lookupSubject = useDataSubject();
  const eraseSubject = useEraseDataSubject();
  const [rebuilt, setRebuilt] = useState<number | null>(null);

  let tenantList: React.ReactNode;
  if (tenants.isPending) {
    tenantList = <LoadingState rows={3} label="載入租戶" />;
  } else if (tenants.isError) {
    tenantList = <ErrorState error={tenants.error} onRetry={() => void tenants.refetch()} />;
  } else {
    tenantList = (
      <TenantAdminTable
        tenants={tenants.data}
        assigningTenantId={assignPlan.isPending ? (assignPlan.variables?.tenantId ?? null) : null}
        onAssign={(tenantId, planCode) => assignPlan.mutate({ tenantId, planCode })}
      />
    );
  }

  return (
    <section aria-labelledby="admin-title" className="space-y-4">
      <h1 id="admin-title" className="font-mono text-xl font-bold tracking-tight">
        平台管理
      </h1>

      <Card className="p-6">
        <h2 className="mb-4 text-sm font-semibold uppercase tracking-[0.12em] text-muted-foreground">
          租戶與方案
        </h2>
        {tenantList}
      </Card>

      <Card className="p-6">
        <h2 className="mb-4 text-sm font-semibold uppercase tracking-[0.12em] text-muted-foreground">
          STIX 投影
        </h2>
        <p className="mb-3 text-sm text-muted-foreground">
          `stix_objects` 是衍生資料,隨時可由 indicators 重算(07 §7.8.6)。
          投影寫出曾經失敗、或投影規則變更後,用這個重建。
        </p>
        <Button
          variant="outline"
          disabled={rebuildStix.isPending}
          onClick={() =>
            rebuildStix.mutate(undefined, {
              onSuccess: (result) => setRebuilt(result.indicatorsRebuilt ?? 0),
            })
          }
        >
          重建全部 STIX 投影
        </Button>
        {rebuilt === null ? null : (
          <p className="mt-3 text-sm" data-testid="stix-rebuild-result">
            已重投影 {rebuilt} 筆 indicator。
          </p>
        )}
      </Card>

      <Card className="p-6">
        <h2 className="mb-4 text-sm font-semibold uppercase tracking-[0.12em] text-muted-foreground">
          資料主體查詢與刪除
        </h2>
        <DataSubjectPanel
          report={lookupSubject.data ?? null}
          erasure={eraseSubject.data ?? null}
          busy={lookupSubject.isPending || eraseSubject.isPending}
          onLookup={(userId) => lookupSubject.mutate(userId)}
          onErase={(userId) => eraseSubject.mutate(userId)}
        />
      </Card>
    </section>
  );
}
