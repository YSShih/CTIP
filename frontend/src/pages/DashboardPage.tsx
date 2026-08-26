/** M1 儀表板(統計卡 + 趨勢圖於 Phase 12 接上 /api/v1/stats)。 */
export default function DashboardPage() {
  return (
    <section aria-labelledby="dashboard-title" className="space-y-4">
      <h1 id="dashboard-title" className="font-mono text-xl font-bold tracking-tight">
        儀表板
      </h1>
      <p className="text-sm text-muted-foreground">公開情資統計總覽(匿名可存取)。</p>
    </section>
  );
}
