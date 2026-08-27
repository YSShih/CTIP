import { createBrowserRouter, type RouteObject } from 'react-router';
import { AppLayout } from '../layouts/AppLayout';
import ApiKeysPage from '../pages/ApiKeysPage';
import DashboardPage from '../pages/DashboardPage';
import IocDetailPage from '../pages/IocDetailPage';
import IocSearchPage from '../pages/IocSearchPage';
import LoginPage from '../pages/LoginPage';
import NotFoundPage from '../pages/NotFoundPage';
import RegisterPage from '../pages/RegisterPage';
import { RequireAuth } from './RequireAuth';
import { RequirePermission } from './RequirePermission';
import { RootErrorBoundary } from './RootErrorBoundary';

/**
 * 路由表(§12.5)。匿名可存取:儀表板、IOC 檢索/詳情、登入、註冊;
 * 需登入的頁面掛 RequireAuth,需權限者再套 RequirePermission。
 * 前端守衛只是 UX——後端一律再驗一次(§12.5)。
 * routes 獨立匯出供測試以 createMemoryRouter 掛載。
 */
export const routes: RouteObject[] = [
  {
    path: '/',
    element: <AppLayout />,
    errorElement: <RootErrorBoundary />,
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'iocs', element: <IocSearchPage /> },
      { path: 'iocs/:id', element: <IocDetailPage /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'register', element: <RegisterPage /> },
      {
        element: <RequireAuth />,
        children: [
          {
            element: <RequirePermission permission="apikey:create" />,
            children: [{ path: 'settings/api-keys', element: <ApiKeysPage /> }],
          },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];

export function createAppRouter() {
  return createBrowserRouter(routes);
}
