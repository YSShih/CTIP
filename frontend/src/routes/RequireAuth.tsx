import { Outlet } from 'react-router';
import { ForbiddenState } from '../components/StateViews';
import { useAppSelector } from '../stores/hooks';
import { selectIsAuthenticated } from '../stores/authSlice';

/**
 * §12.5:需登入頁面的路由層守衛。M1 全部頁面匿名可存取,路由表尚未掛載本守衛
 * (M2 登入頁面加入時掛上);行為已完整:未登入 → ForbiddenState,不得空白(§12.6 #4)。
 * 前端授權僅為 UX,後端必須再次驗證。
 */
export function RequireAuth() {
  const authenticated = useAppSelector(selectIsAuthenticated);
  if (!authenticated) {
    return <ForbiddenState reason="login" />;
  }
  return <Outlet />;
}
