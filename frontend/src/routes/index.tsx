import { lazy, Suspense } from 'react';
import { createBrowserRouter, type RouteObject } from 'react-router';
import { LoadingState } from '../components/StateViews';
import { AppLayout } from '../layouts/AppLayout';
import ApiKeysPage from '../pages/ApiKeysPage';
import AdminPage from '../pages/AdminPage';
import AuditLogPage from '../pages/AuditLogPage';
import DashboardPage from '../pages/DashboardPage';
import IocDetailPage from '../pages/IocDetailPage';
import IocImportPage from '../pages/IocImportPage';
import IocSearchPage from '../pages/IocSearchPage';
import IocSubmitPage from '../pages/IocSubmitPage';
import LoginPage from '../pages/LoginPage';
import NotFoundPage from '../pages/NotFoundPage';
import NotificationCenterPage from '../pages/NotificationCenterPage';
import RegisterPage from '../pages/RegisterPage';
import SettingsPage from '../pages/SettingsPage';
import SubscriptionPage from '../pages/SubscriptionPage';
import SyncPage from '../pages/SyncPage';
import ThreatDetailPage from '../pages/ThreatDetailPage';
import ThreatFeedPage from '../pages/ThreatFeedPage';
import WebhooksPage from '../pages/WebhooksPage';
import { RequireAuth } from './RequireAuth';
import { RequirePermission } from './RequirePermission';
import { RootErrorBoundary } from './RootErrorBoundary';

// STIX Viewer 是唯一用到 Cytoscape.js 的頁面(約 370 kB)。其餘頁面不該為它付這個下載成本,
// 因此只有這一條路由 code-split。
const StixViewerPage = lazy(() => import('../pages/StixViewerPage'));

/**
 * 路由表(§12.5)。匿名可存取:儀表板、IOC 檢索/詳情、威脅情報/詳情、STIX Viewer、Bloom 同步說明、登入、註冊;
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
      // /iocs/new 與 /iocs/import 宣告在下方的守衛區塊內;react-router 依「靜態段優先於動態段」
      // 評分,不看宣告順序,因此它們不會被這條 :id 吃掉(router.test.tsx 有對應案例)
      { path: 'iocs/:id', element: <IocDetailPage /> },
      { path: 'threats', element: <ThreatFeedPage /> },
      { path: 'threats/:id', element: <ThreatDetailPage /> },
      {
        path: 'stix/:id',
        element: (
          <Suspense fallback={<LoadingState rows={6} label="載入 STIX Viewer" />}>
            <StixViewerPage />
          </Suspense>
        ),
      },
      { path: 'sync', element: <SyncPage /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'register', element: <RegisterPage /> },
      {
        element: <RequireAuth />,
        children: [
          // 只需登入,不需額外權限:帳號資訊、外觀與變更密碼人人都有
          { path: 'settings', element: <SettingsPage /> },
          {
            element: <RequirePermission permission="apikey:create" />,
            children: [{ path: 'settings/api-keys', element: <ApiKeysPage /> }],
          },
          {
            element: <RequirePermission permission="subscription:read" />,
            children: [{ path: 'settings/subscription', element: <SubscriptionPage /> }],
          },
          {
            element: <RequirePermission permission="ioc:submit" />,
            children: [{ path: 'iocs/new', element: <IocSubmitPage /> }],
          },
          {
            element: <RequirePermission permission="ioc:import" />,
            children: [{ path: 'iocs/import', element: <IocImportPage /> }],
          },
          {
            element: <RequirePermission permission="notification:read" />,
            children: [{ path: 'notifications', element: <NotificationCenterPage /> }],
          },
          {
            element: <RequirePermission permission="webhook:manage" />,
            children: [{ path: 'settings/webhooks', element: <WebhooksPage /> }],
          },
          {
            element: <RequirePermission permission="audit:read" />,
            children: [{ path: 'audit', element: <AuditLogPage /> }],
          },
          {
            element: <RequirePermission permission="system:admin" />,
            children: [{ path: 'admin', element: <AdminPage /> }],
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
