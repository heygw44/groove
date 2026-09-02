import axios, { type InternalAxiosRequestConfig } from 'axios';

import { useAuthStore } from '@/store/authStore';
import type { ApiResponse } from '@/types/api';

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

export const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  withCredentials: true,
});

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

    if (status === 401 && config && !config._retry) {
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
        const newAccessToken = data.data.accessToken;
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
