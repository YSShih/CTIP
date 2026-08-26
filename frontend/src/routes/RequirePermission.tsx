import { Outlet } from 'react-router';
import { ForbiddenState } from '../components/StateViews';
import { useAppSelector } from '../stores/hooks';
import { selectHasPermission } from '../stores/authSlice';

export interface RequirePermissionProps {
  permission: string;
}

/**
 * §12.5:權限守衛(RBAC 於 M2 Phase 13 接上)。行為已完整:
 * 缺權限 → ForbiddenState(upgrade),不得空白或假資料。
 */
export function RequirePermission({ permission }: RequirePermissionProps) {
  const allowed = useAppSelector((state) => selectHasPermission(state, permission));
  if (!allowed) {
    return <ForbiddenState reason="upgrade" />;
  }
  return <Outlet />;
}
