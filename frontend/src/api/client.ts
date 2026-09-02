import axios, { type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';

import { useAuthStore } from '@/store/authStore';
import type { ApiResponse } from '@/types/api';

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

export const client = axios.create({
  // 폴백은 Vite 프록시(dev)와 Nginx 리버스 프록시(운영) 양쪽에서 맞는 경로다.
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  withCredentials: true,
});

/**
 * ApiResponse 껍데기를 벗겨 data 만 돌려준다. 성공 응답에는 data 가 반드시
 * 있지만 타입상 optional 이라(반환값 없는 API 때문에) 여기서 한 번만 단언한다.
 */
export const unwrap = async <T>(request: Promise<AxiosResponse<ApiResponse<T>>>): Promise<T> => {
  const response = await request;
  return response.data.data as T;
};

client.interceptors.request.use((config) => {
  const accessToken = useAuthStore.getState().accessToken;
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

let isRefreshing = false;
let queue: Array<(token: string) => void> = [];

client.interceptors.response.use(
  (res) => res,
  async (error) => {
    const config = error.config as RetryableRequestConfig | undefined;
    const status = error.response?.status;

    /*
     * /auth/** 는 재발급 대상이 아니다. 로그인 실패(401)로 재발급을 부르면
     * 그 재발급도 401 이라 인터셉터가 자기 자신에 다시 들어가 요청이 매달린다.
     */
    const isAuthRequest = config?.url?.startsWith('/auth/') ?? false;

    if (status === 401 && config && !config._retry && !isAuthRequest) {
      config._retry = true;

      if (isRefreshing) {
        return new Promise((resolve) => {
          queue.push((token) => {
            config.headers.Authorization = `Bearer ${token}`;
            resolve(client(config));
          });
        });
      }

      isRefreshing = true;
      try {
        const { data } = await client.post<ApiResponse<{ accessToken: string }>>('/auth/reissue');
        const newAccessToken = data.data!.accessToken;
        useAuthStore.getState().setAccessToken(newAccessToken);
        queue.forEach((cb) => cb(newAccessToken));
        queue = [];
        config.headers.Authorization = `Bearer ${newAccessToken}`;
        return client(config);
      } catch (reissueError) {
        queue = [];
        useAuthStore.getState().clearAuth();
        window.location.href = '/login';
        return Promise.reject(reissueError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  },
);
