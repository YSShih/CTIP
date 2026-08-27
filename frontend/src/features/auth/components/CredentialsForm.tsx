import { useState, type FormEvent } from 'react';
import { ApiError } from '../../../api/client';
import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';

export interface CredentialsFormValues {
  email: string;
  password: string;
  displayName?: string;
  tenantName?: string;
}

export interface CredentialsFormProps {
  mode: 'login' | 'register';
  submitting: boolean;
  error: ApiError | null;
  onSubmit: (values: CredentialsFormValues) => void;
}

const MIN_PASSWORD_LENGTH = 12;

function messageFor(error: ApiError): string {
  if (error.code === 'UNAUTHENTICATED') return '帳號或密碼不正確,或帳號已被暫時鎖定。';
  if (error.code === 'CONFLICT') return '這個電子郵件已經註冊過了。';
  if (error.code === 'INVALID_REQUEST') return '輸入內容不符合要求,請檢查各欄位。';
  if (error.code === 'RATE_LIMIT_EXCEEDED') return '嘗試次數過多,請稍後再試。';
  return error.message;
}

/** 登入 / 註冊共用表單(§12.6:label、aria、鍵盤操作、錯誤狀態)。 */
export function CredentialsForm({ mode, submitting, error, onSubmit }: CredentialsFormProps) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [tenantName, setTenantName] = useState('');
  const registering = mode === 'register';

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit(
      registering
        ? {
            email,
            password,
            displayName: displayName || undefined,
            tenantName: tenantName || undefined,
          }
        : { email, password },
    );
  }

  return (
    <form className="space-y-4" onSubmit={handleSubmit} noValidate>
      <div className="space-y-1.5">
        <label htmlFor="auth-email" className="text-sm font-medium">
          電子郵件
        </label>
        <Input
          id="auth-email"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
        />
      </div>

      <div className="space-y-1.5">
        <label htmlFor="auth-password" className="text-sm font-medium">
          密碼
        </label>
        <Input
          id="auth-password"
          type="password"
          autoComplete={registering ? 'new-password' : 'current-password'}
          required
          minLength={MIN_PASSWORD_LENGTH}
          aria-describedby={registering ? 'auth-password-hint' : undefined}
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />
        {registering ? (
          <p id="auth-password-hint" className="text-xs text-muted-foreground">
            至少 {MIN_PASSWORD_LENGTH} 個字元。
          </p>
        ) : null}
      </div>

      {registering ? (
        <>
          <div className="space-y-1.5">
            <label htmlFor="auth-display-name" className="text-sm font-medium">
              顯示名稱(選填)
            </label>
            <Input
              id="auth-display-name"
              autoComplete="name"
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
            />
          </div>
          <div className="space-y-1.5">
            <label htmlFor="auth-tenant-name" className="text-sm font-medium">
              組織名稱(選填)
            </label>
            <Input
              id="auth-tenant-name"
              value={tenantName}
              onChange={(event) => setTenantName(event.target.value)}
            />
            <p className="text-xs text-muted-foreground">
              註冊會建立一個專屬租戶,你會成為它的管理者。
            </p>
          </div>
        </>
      ) : null}

      {error ? (
        <p role="alert" className="text-sm text-destructive">
          {messageFor(error)}
        </p>
      ) : null}

      <Button type="submit" disabled={submitting} className="w-full">
        {submitting ? '處理中…' : registering ? '建立帳號' : '登入'}
      </Button>
    </form>
  );
}
