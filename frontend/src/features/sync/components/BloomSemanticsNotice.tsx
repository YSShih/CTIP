import { AlertTriangle } from 'lucide-react';

/**
 * Bloom 的語意警告(12 §12.6 UI 責任第 3 條、11 §11.1、不變量 L8)。
 *
 * 這一段是<strong>規格明文要求的頁面內容</strong>,不是裝飾:命中不代表確定惡意、
 * 未命中也不代表安全(public Bloom 只覆蓋 `TLP:CLEAR`,`TLP:GREEN` 完全沒有覆蓋)。
 * 撤銷與過期的 IOC 無法透過 delta 移除,只有 full snapshot 才會反映(§11.3)。
 */
export function BloomSemanticsNotice({ notCovered }: { notCovered: string[] }) {
  return (
    <div
      className="rounded-lg border border-warn/40 bg-warn/10 p-5"
      role="note"
      aria-labelledby="bloom-semantics"
    >
      <h2 id="bloom-semantics" className="mb-3 flex items-center gap-2 text-sm font-semibold">
        <AlertTriangle aria-hidden className="size-4 text-warn" />
        Bloom 結果怎麼讀
      </h2>
      <dl className="space-y-3 text-sm">
        <div>
          <dt className="font-semibold">命中(PRESENT)不代表確定惡意</dt>
          <dd className="text-muted-foreground">
            Bloom filter 有偽陽性。命中只表示「可能存在於這份情資集合中」,必須再呼叫
            <code className="mx-1 font-mono text-xs">POST /api/v1/iocs/lookup</code>
            精確驗證後才能據以行動。
          </dd>
        </div>
        <div>
          <dt className="font-semibold">未命中(NOT PRESENT)不代表安全</dt>
          <dd className="text-muted-foreground">
            未命中只表示「不在這份 Bloom 的成員集合中」。public Bloom 只含
            <code className="mx-1 font-mono text-xs">TLP:CLEAR</code>
            的公開情資;
            {notCovered.length > 0 ? (
              <>
                <span className="font-semibold">{notCovered.join('、')}</span>
                <span> 沒有任何 Bloom 覆蓋</span>
              </>
            ) : (
              <span>覆蓋範圍見下方各層的說明</span>
            )}
            。未命中的值仍有可能是惡意的。
          </dd>
        </div>
        <div>
          <dt className="font-semibold">撤銷與過期不會經 delta 消失</dt>
          <dd className="text-muted-foreground">
            標準 Bloom filter 無法移除成員,delta 只會新增位元。撤銷(REVOKED)或過期(EXPIRED) 的 IOC
            要等下一份 full snapshot 才會從集合中消失。
          </dd>
        </div>
      </dl>
    </div>
  );
}
