import { Lock } from 'lucide-react';

export interface ForbiddenStateProps {
  /** login = 需登入;upgrade = 權限/方案不足 */
  reason: 'login' | 'upgrade';
}

const COPY: Record<ForbiddenStateProps['reason'], { title: string; description: string }> = {
  login: {
    title: '需要登入',
    description:
      '這部分情資僅提供已登入的租戶成員檢視。M1 尚未開放註冊與登入;正式版將在此引導您登入。',
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
