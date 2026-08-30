import { useState, type FormEvent } from 'react';
import { ApiError } from '../../../api/client';
import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';

export interface ChangePasswordFormProps {
  submitting: boolean;
  error: ApiError | null;
  onSubmit: (values: { currentPassword: string; newPassword: string }) => void;
}

/** §9.1 的 ChangePasswordRequest:newPassword 至少 12 字元、上限 72 bytes(BCrypt)。 */
const MIN_PASSWORD_LENGTH = 12;

function messageFor(error: ApiError): string {
  if (error.code === 'UNAUTHENTICATED') return '目前密碼不正確。';
  if (error.code === 'INVALID_REQUEST') return '新密碼不符合要求(至少 12 個字元)。';
  if (error.code === 'RATE_LIMIT_EXCEEDED') return '嘗試次數過多,請稍後再試。';
  return error.message;
}

/**
 * 變更密碼表單(§12.6:label、aria、鍵盤操作、錯誤狀態)。
 *
 * <p>「新密碼」要求輸入兩次:送出後全部工作階段都會被撤銷,打錯字的代價是被鎖在門外,
 * 而這個錯誤在送出當下沒有任何伺服器端能攔的機會。
 */
export function ChangePasswordForm({ submitting, error, onSubmit }: ChangePasswordFormProps) {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');

  const mismatched = confirmation.length > 0 && confirmation !== newPassword;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (mismatched) return;
    onSubmit({ currentPassword, newPassword });
  }

  return (
    <form className="space-y-4" onSubmit={handleSubmit} noValidate>
      <div className="space-y-1.5">
        <label htmlFor="current-password" className="text-sm font-medium">
          目前密碼
        </label>
        <Input
          id="current-password"
          type="password"
          autoComplete="current-password"
          required
          value={currentPassword}
          onChange={(event) => setCurrentPassword(event.target.value)}
        />
      </div>

      <div className="space-y-1.5">
        <label htmlFor="new-password" className="text-sm font-medium">
          新密碼
        </label>
        <Input
          id="new-password"
          type="password"
          autoComplete="new-password"
          required
          minLength={MIN_PASSWORD_LENGTH}
          aria-describedby="new-password-hint"
          value={newPassword}
          onChange={(event) => setNewPassword(event.target.value)}
        />
        <p id="new-password-hint" className="text-xs text-muted-foreground">
          至少 {MIN_PASSWORD_LENGTH} 個字元。變更後<strong>全部裝置都會登出</strong>
          ,包含目前這一個。
        </p>
      </div>

      <div className="space-y-1.5">
        <label htmlFor="confirm-password" className="text-sm font-medium">
          再輸入一次新密碼
        </label>
        <Input
          id="confirm-password"
          type="password"
          autoComplete="new-password"
          required
          aria-invalid={mismatched}
          aria-describedby={mismatched ? 'confirm-password-error' : undefined}
          value={confirmation}
          onChange={(event) => setConfirmation(event.target.value)}
        />
        {mismatched ? (
          <p id="confirm-password-error" role="alert" className="text-xs text-destructive">
            兩次輸入的新密碼不一致。
          </p>
        ) : null}
      </div>

      {error ? (
        <p role="alert" className="text-sm text-destructive">
          {messageFor(error)}
        </p>
      ) : null}

      <Button type="submit" disabled={submitting || mismatched}>
        {submitting ? '變更中…' : '變更密碼'}
      </Button>
    </form>
  );
}
