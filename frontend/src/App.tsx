import { RouterProvider } from 'react-router';
import { setAuthTokenProvider } from './api/client';
import { AppProviders } from './app/providers';
import { createQueryClient } from './app/queryClient';
import { createAppRouter } from './routes';
import { createAppStore } from './stores';

const store = createAppStore();
const queryClient = createQueryClient();
const router = createAppRouter();

// api 層不 import stores(§12.2 分層):token 由 app 層注入
setAuthTokenProvider(() => store.getState().auth.accessToken);

export default function App() {
  return (
    <AppProviders store={store} queryClient={queryClient}>
      <RouterProvider router={router} />
    </AppProviders>
  );
}
