import { createBrowserRouter, type RouteObject } from 'react-router';
import { AppLayout } from '../layouts/AppLayout';
import DashboardPage from '../pages/DashboardPage';
import IocDetailPage from '../pages/IocDetailPage';
import IocSearchPage from '../pages/IocSearchPage';
import NotFoundPage from '../pages/NotFoundPage';
import { RootErrorBoundary } from './RootErrorBoundary';

/**
 * M1 路由(§12.5):三頁皆匿名可存取,RequireAuth / RequirePermission
 * 於 M2 需登入頁面加入時掛載(守衛本身已完整實作並測試)。
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
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];

export function createAppRouter() {
  return createBrowserRouter(routes);
}
