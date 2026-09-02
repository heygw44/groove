import { QueryClient } from '@tanstack/react-query';
import axios from 'axios';

/**
 * 4xx 는 재시도하지 않는다. 인증 만료(401)는 axios 인터셉터가 재발급으로
 * 처리하므로, 여기서 또 재시도하면 같은 실패만 반복된다.
 */
const retry = (failureCount: number, error: unknown) => {
  const status = axios.isAxiosError(error) ? error.response?.status : undefined;
  if (status !== undefined && status < 500) {
    return false;
  }
  return failureCount < 2;
};

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60 * 1000,
      refetchOnWindowFocus: false,
      retry,
    },
    mutations: {
      retry: false,
    },
  },
});
