import { QueryClient } from '@tanstack/react-query';
import { ApiError } from '../api/client';

/** server state 一律 TanStack Query(§12.3);4xx 不重試,避免對後端限流火上加油。 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        retry: (failureCount, error) => {
          if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
            return false;
          }
          return failureCount < 1;
        },
      },
    },
  });
}
