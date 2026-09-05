import { client, refreshClient, unwrap } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type { LoginRequest, SignupRequest, SignupResponse, TokenResponse } from '@/types/member';

export const signup = (payload: SignupRequest) =>
  unwrap(client.post<ApiResponse<SignupResponse>>('/auth/signup', payload));

export const login = (payload: LoginRequest) =>
  unwrap(client.post<ApiResponse<TokenResponse>>('/auth/login', payload));

export const logout = async () => {
  await client.post<ApiResponse<void>>('/auth/logout');
};

/** 인터셉터가 달리지 않은 인스턴스로 보낸다 - client.ts 의 refreshClient 주석 참고. */
export const reissue = () =>
  unwrap(refreshClient.post<ApiResponse<TokenResponse>>('/auth/reissue'));
