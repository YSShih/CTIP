import { Card } from '../../../components/ui/card';

export interface IssuedSecretNoticeProps {
  secret: string;
}

/**
 * 不變量 W2:簽章密鑰只在建立當下回傳一次,之後任何端點都不再吐出它。
 * 這段警語不得移除——使用者若沒複製就只能刪掉重建一個 webhook。
 */
export function IssuedSecretNotice({ secret }: IssuedSecretNoticeProps) {
  return (
    <Card role="status" className="space-y-2 border-primary p-4">
      <p className="text-sm font-semibold">請立刻複製這組簽章密鑰,它只會顯示這一次。</p>
      <code className="block overflow-x-auto rounded bg-muted p-2 font-mono text-xs">{secret}</code>
      <p className="text-xs text-muted-foreground">
        接收端以 <code>HMAC-SHA256(secret, timestamp + &quot;.&quot; + body)</code> 驗簽,並拒絕
        timestamp 偏差超過 5 分鐘的請求;完整契約見 <code>docs/api/webhooks.md</code>。
      </p>
    </Card>
  );
}
