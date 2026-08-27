import { RouterProvider } from 'react-router';
import { setAuthTokenProvider, setSessionRefresher } from './api/client';
import { refresh } from './features/auth/api/authApi';
import { toSessionPayload } from './features/auth/api/toSession';
import { sessionCleared, sessionEstablished } from './stores/authSlice';
import { AppProviders } from './app/providers';
import { createQueryClient } from './app/queryClient';
import { createAppRouter } from './routes';
import { createAppStore } from './stores';

const store = createAppStore();
const queryClient = createQueryClient();
const router = createAppRouter();

// api 層不 import stores(§12.2 分層):token 由 app 層注入
setAuthTokenProvider(() => store.getState().auth.accessToken);

/**
 * access token 15 分鐘即過期(§10.4),401 時以 refresh token 輪替一次再重送。
 * 輪替失敗代表 family 已撤銷或過期,直接清 session,由路由守衛導向登入。
 */
setSessionRefresher(async () => {
  const current = store.getState().auth.refreshToken;
  if (!current) return null;
  try {
    const rotated = await refresh(current);
    store.dispatch(
      sessionEstablished(toSessionPayload(rotated, store.getState().auth.user?.name ?? '')),
    );
    return rotated.accessToken ?? null;
  } catch {
    store.dispatch(sessionCleared());
    return null;
  }
});

export default function App() {
  return (
    <AppProviders store={store} queryClient={queryClient}>
      <RouterProvider router={router} />
    </AppProviders>
  );
}
