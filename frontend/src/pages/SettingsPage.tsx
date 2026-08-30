import { CreditCard, KeyRound, Monitor, Moon, Sun, Webhook } from 'lucide-react';
import { Link } from 'react-router';
import { Badge } from '../components/ui/badge';
import { Button } from '../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { ChangePasswordForm } from '../features/auth/components/ChangePasswordForm';
import { useChangePassword } from '../features/auth/hooks/useAuthSession';
import { useHasPermission } from '../hooks/useSession';
import { useAppDispatch, useAppSelector } from '../stores/hooks';
import { selectTheme, themeChanged, type ThemePreference } from '../stores/uiSlice';
import { toastPushed } from '../stores/toastSlice';

const THEME_OPTIONS: { value: ThemePreference; label: string; Icon: typeof Sun }[] = [
  { value: 'light', label: '亮色', Icon: Sun },
  { value: 'dark', label: '深色', Icon: Moon },
  { value: 'system', label: '跟隨系統', Icon: Monitor },
];

/**
 * §12.5 `/settings`(需登入)。帳號層級的設定總覽:身分、外觀、變更密碼,
 * 以及通往各分頁的入口。
 *
 * <p>變更密碼是這一頁存在的主要理由:`POST /api/v1/auth/change-password`(Phase 21 交付)
 * 在此之前**沒有任何前端入口**。送出成功後全部工作階段都被撤銷(ADR 0015),
 * 因此頁面就地清掉 session,由路由守衛帶回登入頁——這是誠實的結果,不是錯誤。
 */
export default function SettingsPage() {
  const dispatch = useAppDispatch();
  const theme = useAppSelector(selectTheme);
  const user = useAppSelector((state) => state.auth.user);
  const tenantId = useAppSelector((state) => state.auth.tenantId);
  const role = useAppSelector((state) => state.auth.role);
  const permissions = useAppSelector((state) => state.auth.permissions);
  const canManageApiKeys = useHasPermission('apikey:create');
  const canReadSubscription = useHasPermission('subscription:read');
  const canManageWebhooks = useHasPermission('webhook:manage');
  const changePassword = useChangePassword();

  function submit(values: { currentPassword: string; newPassword: string }) {
    changePassword.mutate(values, {
      onSuccess: (revokedSessions) =>
        dispatch(
          toastPushed({
            kind: 'success',
            message: `密碼已變更,${revokedSessions} 個工作階段已登出,請重新登入。`,
          }),
        ),
    });
  }

  return (
    <section aria-labelledby="settings-title" className="space-y-4">
      <header>
        <h1 id="settings-title" className="font-mono text-xl font-bold tracking-tight">
          設定
        </h1>
      </header>

      <Card>
        <CardHeader>
          <CardTitle>帳號</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-3 sm:grid-cols-2" aria-label="帳號資訊">
            <div>
              <dt className="text-xs text-muted-foreground">使用者</dt>
              <dd className="font-mono text-sm">{user?.name ?? '—'}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">角色</dt>
              <dd>{role ? <Badge variant="outline">{role}</Badge> : '—'}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">租戶</dt>
              <dd className="font-mono text-xs break-all">{tenantId ?? '—'}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">權限</dt>
              <dd className="font-mono text-sm">{permissions.length} 項</dd>
            </div>
          </dl>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>外觀</CardTitle>
        </CardHeader>
        <CardContent>
          <div role="group" aria-label="主題" className="flex flex-wrap gap-2">
            {THEME_OPTIONS.map(({ value, label, Icon }) => (
              <Button
                key={value}
                variant={theme === value ? 'default' : 'outline'}
                size="sm"
                aria-pressed={theme === value}
                onClick={() => dispatch(themeChanged(value))}
              >
                <Icon aria-hidden className="size-4" />
                {label}
              </Button>
            ))}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>變更密碼</CardTitle>
        </CardHeader>
        <CardContent>
          <ChangePasswordForm
            submitting={changePassword.isPending}
            error={changePassword.error}
            onSubmit={submit}
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>其他設定</CardTitle>
        </CardHeader>
        <CardContent>
          <ul className="space-y-2 text-sm">
            {canReadSubscription ? (
              <li>
                <Link
                  to="/settings/subscription"
                  className="inline-flex items-center gap-2 text-muted-foreground hover:text-foreground"
                >
                  <CreditCard aria-hidden className="size-4" />
                  方案與用量
                </Link>
              </li>
            ) : null}
            {canManageApiKeys ? (
              <li>
                <Link
                  to="/settings/api-keys"
                  className="inline-flex items-center gap-2 text-muted-foreground hover:text-foreground"
                >
                  <KeyRound aria-hidden className="size-4" />
                  API Key 管理
                </Link>
              </li>
            ) : null}
            {canManageWebhooks ? (
              <li>
                <Link
                  to="/settings/webhooks"
                  className="inline-flex items-center gap-2 text-muted-foreground hover:text-foreground"
                >
                  <Webhook aria-hidden className="size-4" />
                  Webhook
                </Link>
              </li>
            ) : null}
            {!canReadSubscription && !canManageApiKeys && !canManageWebhooks ? (
              <li className="text-muted-foreground">目前的角色沒有其他可設定的項目。</li>
            ) : null}
          </ul>
        </CardContent>
      </Card>
    </section>
  );
}
