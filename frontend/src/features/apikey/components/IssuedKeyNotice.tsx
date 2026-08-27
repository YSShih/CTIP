import { Card } from '../../../components/ui/card';

export interface IssuedKeyNoticeProps {
  fullKey: string;
}

/**
 * 不變量 K1:完整金鑰只在建立當下回傳一次,之後永不可查。
 * 這段警語不得移除——使用者若沒複製就只能撤銷重建。
 */
export function IssuedKeyNotice({ fullKey }: IssuedKeyNoticeProps) {
  return (
    <Card role="status" className="space-y-2 border-primary p-4">
      <p className="text-sm font-semibold">請立刻複製這把金鑰,它只會顯示這一次。</p>
      <code className="block overflow-x-auto rounded bg-muted p-2 font-mono text-xs">
        {fullKey}
      </code>
      <p className="text-xs text-muted-foreground">
        伺服器只保存它的 SHA-256 雜湊;遺失只能撤銷後重新建立。
      </p>
    </Card>
  );
}
