import { useState, type FormEvent } from 'react';
import { ApiError } from '../../../api/client';
import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';

/** 可授予 API key 的權限;實際仍受後端 K3/K4 檢查(scope ⊆ 建立者權限)。 */
export const GRANTABLE_SCOPES = [
  'ioc:read',
  'ioc:export',
  'stix:export',
  'sync:bloom',
  'sync:delta',
] as const;

export interface ApiKeyCreateFormProps {
  submitting: boolean;
  error: ApiError | null;
  availableScopes: readonly string[];
  onSubmit: (values: { name: string; scopes: string[] }) => void;
}

export function ApiKeyCreateForm({
  submitting,
  error,
  availableScopes,
  onSubmit,
}: ApiKeyCreateFormProps) {
  const [name, setName] = useState('');
  const [scopes, setScopes] = useState<string[]>([]);

  function toggle(scope: string) {
    setScopes((current) =>
      current.includes(scope) ? current.filter((s) => s !== scope) : [...current, scope],
    );
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit({ name, scopes });
  }

  return (
    <form className="space-y-4" onSubmit={handleSubmit} noValidate>
      <div className="space-y-1.5">
        <label htmlFor="apikey-name" className="text-sm font-medium">
          名稱
        </label>
        <Input
          id="apikey-name"
          required
          maxLength={128}
          value={name}
          onChange={(event) => setName(event.target.value)}
        />
      </div>

      <fieldset className="space-y-2">
        <legend className="text-sm font-medium">權限範圍</legend>
        {availableScopes.length === 0 ? (
          <p className="text-xs text-muted-foreground">你目前沒有可授予的權限。</p>
        ) : (
          <ul className="grid gap-1.5 sm:grid-cols-2">
            {availableScopes.map((scope) => (
              <li key={scope}>
                <label className="flex items-center gap-2 font-mono text-xs">
                  <input
                    type="checkbox"
                    checked={scopes.includes(scope)}
                    onChange={() => toggle(scope)}
                  />
                  {scope}
                </label>
              </li>
            ))}
          </ul>
        )}
      </fieldset>

      {error ? (
        <p role="alert" className="text-sm text-destructive">
          {error.code === 'INVALID_REQUEST' ? '權限範圍不得超出你自己的權限。' : error.message}
        </p>
      ) : null}

      <Button type="submit" disabled={submitting || scopes.length === 0 || name.trim() === ''}>
        {submitting ? '建立中…' : '建立 API key'}
      </Button>
    </form>
  );
}
