import { client, unwrap } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type { LoginRequest, SignupRequest, SignupResponse, TokenResponse } from '@/types/member';

export const signup = (payload: SignupRequest) =>
  unwrap(client.post<ApiResponse<SignupResponse>>('/auth/signup', payload));

export const login = (payload: LoginRequest) =>
  unwrap(client.post<ApiResponse<TokenResponse>>('/auth/login', payload));

export const logout = async () => {
  await client.post<ApiResponse<void>>('/auth/logout');
};

export const reissue = () => unwrap(client.post<ApiResponse<TokenResponse>>('/auth/reissue'));
