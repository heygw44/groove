import axios, { type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';

import { queryClient } from '@/lib/queryClient';
import { useAuthStore } from '@/store/authStore';
import type { ApiError, ApiResponse } from '@/types/api';

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

// 폴백은 Vite 프록시(dev)와 Nginx 리버스 프록시(운영) 양쪽에서 맞는 경로다.
const baseURL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1';

// indexes: null 로 배열은 genreIds=1&genreIds=3 형태로 직렬화한다(axios 기본값인 genreIds[]=1 은 백엔드가 못 받는다).
export const client = axios.create({
  baseURL,
  withCredentials: true,
  paramsSerializer: { indexes: null },
});

/**
 * 재발급 전용 인스턴스. 인터셉터를 달지 않는 것이 핵심이다.
 * 같은 인스턴스로 재발급을 보내면 그 응답이 401 일 때 인터셉터가 자기 자신에
 * 다시 들어가고, isRefreshing 이 이미 true 라 큐에만 쌓인 채 아무도 깨우지
 * 않아 요청이 영원히 매달린다.
 */
export const refreshClient = axios.create({ baseURL, withCredentials: true });

const PUBLIC_PATHS = ['/auth/signup', '/auth/login', '/auth/reissue'];

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

const getErrorCode = (error: unknown): string | undefined => {
  if (!axios.isAxiosError(error)) {
    return undefined;
  }
  const data = error.response?.data as { error?: ApiError } | undefined;
  return data?.error?.code;
};

/** 재발급으로 살릴 수 없는 상태. 스토어·캐시를 비우고 로그인으로 보낸다. */
const handleSessionExpired = () => {
  useAuthStore.getState().clearAuth();
  queryClient.clear();

  const { pathname, search } = window.location;
  if (pathname === '/login') {
    return;
  }
  const redirect = encodeURIComponent(`${pathname}${search}`);
  window.location.href = `/login?redirect=${redirect}`;
};

interface PendingRequest {
  resolve: (accessToken: string) => void;
  reject: (reason: unknown) => void;
}

let isRefreshing = false;
let pending: PendingRequest[] = [];

const flushPending = (accessToken: string) => {
  const waiting = pending;
  pending = [];
  waiting.forEach(({ resolve }) => resolve(accessToken));
};

const rejectPending = (reason: unknown) => {
  const waiting = pending;
  pending = [];
  waiting.forEach(({ reject }) => reject(reason));
};

client.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    const config = axios.isAxiosError(error)
      ? (error.config as RetryableRequestConfig | undefined)
      : undefined;
    const status = axios.isAxiosError(error) ? error.response?.status : undefined;
    const code = getErrorCode(error);

    /*
     * 정지된 계정은 재발급으로 살릴 수 없는 상태다(서버가 모든 인증 API 에서
     * 403 AUTH_MEMBER_SUSPENDED 를 준다). 세션 만료와 같은 경로로 로그인 화면으로 보낸다.
     */
    if (status === 403 && code === 'AUTH_MEMBER_SUSPENDED') {
      handleSessionExpired();
      return Promise.reject(error);
    }

    if (status !== 401 || !config) {
      return Promise.reject(error);
    }

    /*
     * 비로그인 엔드포인트(백엔드 SecurityConfig.PUBLIC_PATHS 와 같은 목록)만
     * 재발급 대상에서 뺀다. 로그인 실패도 401 이라 이걸 안 빼면 재발급을 물고
     * 들어간다. logout 은 인증이 필요한 엔드포인트라 여기 넣지 않는다 -
     * 만료 상태에서 로그아웃이 401 로 끝나면 서버가 쿠키와 Redis 토큰을 지우지
     * 못해, 로그아웃했는데 새로고침하면 부팅 재발급으로 되살아난다.
     */
    if (PUBLIC_PATHS.some((path) => config.url?.startsWith(path))) {
      return Promise.reject(error);
    }

    /*
     * 만료가 아닌 401(토큰 없음·서명 오류)은 재발급해도 살아나지 않는다.
     * 다만 애초에 로그인 상태가 아니었다면 라우트 가드가 처리할 몫이라
     * 여기서 리다이렉트하지 않는다.
     */
    if (code !== 'AUTH_EXPIRED_TOKEN') {
      if (useAuthStore.getState().accessToken) {
        handleSessionExpired();
      }
      return Promise.reject(error);
    }

    if (config._retry) {
      handleSessionExpired();
      return Promise.reject(error);
    }
    config._retry = true;

    const retryWith = (accessToken: string) => {
      config.headers.Authorization = `Bearer ${accessToken}`;
      return client(config);
    };

    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        pending.push({
          resolve: (accessToken) => resolve(retryWith(accessToken)),
          reject,
        });
      });
    }

    isRefreshing = true;
    try {
      const { data } =
        await refreshClient.post<ApiResponse<{ accessToken: string }>>('/auth/reissue');
      const accessToken = data.data!.accessToken;
      useAuthStore.getState().setAccessToken(accessToken);
      flushPending(accessToken);
      return retryWith(accessToken);
    } catch (reissueError) {
      /* 대기 중이던 요청을 반드시 깨운다. 비우기만 하면 영원히 pending 이다. */
      rejectPending(reissueError);
      handleSessionExpired();
      return Promise.reject(reissueError);
    } finally {
      isRefreshing = false;
    }
  },
);
