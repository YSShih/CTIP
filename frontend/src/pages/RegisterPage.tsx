import { Link, useNavigate } from 'react-router';
import { ApiError } from '../api/client';
import { Card } from '../components/ui/card';
import { CredentialsForm } from '../features/auth/components/CredentialsForm';
import { useRegister } from '../features/auth/hooks/useAuthSession';

/** §12.5 /register(匿名可存取)。註冊同時建立租戶並直接建立 session。 */
export default function RegisterPage() {
  const navigate = useNavigate();
  const registerMutation = useRegister();

  return (
    <section aria-labelledby="register-title" className="mx-auto max-w-md space-y-4">
      <h1 id="register-title" className="font-mono text-xl font-bold tracking-tight">
        建立帳號
      </h1>
      <Card className="p-6">
        <CredentialsForm
          mode="register"
          submitting={registerMutation.isPending}
          error={registerMutation.error instanceof ApiError ? registerMutation.error : null}
          onSubmit={(values) =>
            registerMutation.mutate(values, { onSuccess: () => void navigate('/') })
          }
        />
      </Card>
      <p className="text-sm text-muted-foreground">
        已經有帳號?
        <Link to="/login" className="ml-1 underline underline-offset-4">
          直接登入
        </Link>
      </p>
    </section>
  );
}
