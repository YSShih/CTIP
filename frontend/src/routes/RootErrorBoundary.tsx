import { useNavigate, useRouteError } from 'react-router';
import { ErrorState } from '../components/StateViews';

/** 路由層錯誤邊界:render 錯誤同樣以統一 ErrorState 呈現(§12.6)。 */
export function RootErrorBoundary() {
  const error = useRouteError();
  const navigate = useNavigate();
  return (
    <div className="mx-auto max-w-3xl p-8">
      <ErrorState error={error} onRetry={() => void navigate(0)} />
    </div>
  );
}
