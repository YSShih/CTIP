import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { Provider as ReduxProvider } from 'react-redux';
import type { AppStore } from '../stores';
import { ThemeApplier } from './ThemeApplier';

export interface AppProvidersProps {
  store: AppStore;
  queryClient: QueryClient;
  children: ReactNode;
}

/** §12.2:provider 組合(Redux store、QueryClient、Theme);Router 由 App 掛載。 */
export function AppProviders({ store, queryClient, children }: AppProvidersProps) {
  return (
    <ReduxProvider store={store}>
      <QueryClientProvider client={queryClient}>
        <ThemeApplier />
        {children}
      </QueryClientProvider>
    </ReduxProvider>
  );
}
