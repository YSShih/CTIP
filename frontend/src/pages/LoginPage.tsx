import { Link, useNavigate } from 'react-router';
import { ApiError } from '../api/client';
import { Card } from '../components/ui/card';
import { CredentialsForm } from '../features/auth/components/CredentialsForm';
import { useLogin } from '../features/auth/hooks/useAuthSession';

/** §12.5 /login(匿名可存取)。登入成功導回儀表板。 */
export default function LoginPage() {
  const navigate = useNavigate();
  const loginMutation = useLogin();

  return (
    <section aria-labelledby="login-title" className="mx-auto max-w-md space-y-4">
      <h1 id="login-title" className="font-mono text-xl font-bold tracking-tight">
        登入
      </h1>
      <Card className="p-6">
        <CredentialsForm
          mode="login"
          submitting={loginMutation.isPending}
          error={loginMutation.error instanceof ApiError ? loginMutation.error : null}
          onSubmit={(values) =>
            loginMutation.mutate(
              { email: values.email, password: values.password },
              { onSuccess: () => void navigate('/') },
            )
          }
        />
      </Card>
      <p className="text-sm text-muted-foreground">
        還沒有帳號?
        <Link to="/register" className="ml-1 underline underline-offset-4">
          建立一個
        </Link>
      </p>
    </section>
  );
}
