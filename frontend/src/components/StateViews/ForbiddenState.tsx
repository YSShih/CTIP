import { Lock } from 'lucide-react';

export interface ForbiddenStateProps {
  /** login = 需登入;upgrade = 權限/方案不足 */
  reason: 'login' | 'upgrade';
}

const COPY: Record<ForbiddenStateProps['reason'], { title: string; description: string }> = {
  login: {
    title: '需要登入',
    // 原文寫「M1 尚未開放註冊與登入」——那是 Phase 12 的實況,而登入與註冊自 Phase 13 起就存在
    // (`/login`、`/register` 是實際路由)。§12.6 #4 要求顯示**原因**;顯示一個已經不成立的原因
    // 比空白更糟,因此改為指向實際入口(2026-08-30 補拍 demo 截圖時發現)。
    description: '這部分情資僅提供已登入的租戶成員檢視。請由右上角登入,或先註冊一個租戶。',
  },
  upgrade: {
    title: '權限不足',
    description: '您目前的方案或角色無法檢視此內容。請聯絡租戶管理員或升級方案以取得存取權。',
  },
};

/**
 * §12.6 #4:匿名使用者看到需登入資料時,必須顯示原因,
 * 不得顯示空白或假資料。
 */
export function ForbiddenState({ reason }: ForbiddenStateProps) {
  const copy = COPY[reason];
  return (
    <div className="flex w-full flex-col items-center gap-3 rounded-lg border border-warn/40 bg-warn/5 px-6 py-14 text-center">
      <Lock aria-hidden className="size-8 text-warn" />
      <p className="font-mono text-sm font-semibold uppercase tracking-wider text-warn">
        {copy.title}
      </p>
      <p className="max-w-md text-sm text-muted-foreground">{copy.description}</p>
    </div>
  );
}
