/** IOC 檢索頁(篩選列 + 虛擬化表格 + cursor 分頁於 Phase 12 接上)。 */
export default function IocSearchPage() {
  return (
    <section aria-labelledby="ioc-search-title" className="space-y-4">
      <h1 id="ioc-search-title" className="font-mono text-xl font-bold tracking-tight">
        IOC 檢索
      </h1>
      <p className="text-sm text-muted-foreground">
        以值、型別、嚴重度、TLP 等條件檢索公開情資;搜尋條件保存在網址列。
      </p>
    </section>
  );
}
