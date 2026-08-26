import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { ReactNode } from 'react';
import { Provider as ReduxProvider } from 'react-redux';
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router';
import { makeStore, type AppStore } from '../stores';

/** 頁面測試用的 provider 組合;retry 關閉讓錯誤狀態測試不等待重試。 */
export function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 0 } },
  });
}

export interface RenderRouteOptions {
  routes: RouteObject[];
  initialEntry: string;
  store?: AppStore;
  queryClient?: QueryClient;
}

export function renderRoute({ routes, initialEntry, store, queryClient }: RenderRouteOptions) {
  const testStore = store ?? makeStore();
  const client = queryClient ?? createTestQueryClient();
  const router = createMemoryRouter(routes, { initialEntries: [initialEntry] });
  const view = render(
    <ReduxProvider store={testStore}>
      <QueryClientProvider client={client}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ReduxProvider>,
  );
  return { ...view, store: testStore, queryClient: client, router };
}

export function wrapProviders(children: ReactNode, store: AppStore, client: QueryClient) {
  return (
    <ReduxProvider store={store}>
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    </ReduxProvider>
  );
}
