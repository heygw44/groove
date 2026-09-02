import { client } from '@/api/client';
import type { ApiResponse } from '@/types/api';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
}

export const login = (payload: LoginRequest) =>
  client.post<ApiResponse<LoginResponse>>('/auth/login', payload);

export const logout = () => client.post<ApiResponse<null>>('/auth/logout');

export const reissue = () => client.post<ApiResponse<LoginResponse>>('/auth/reissue');
