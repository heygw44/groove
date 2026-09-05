import type { AxiosRequestConfig } from 'axios';

import { client, unwrap } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type { Member, NicknameUpdateRequest, PasswordChangeRequest } from '@/types/member';

/**
 * config 를 받는 이유: 로그인 직후에는 스토어에 토큰이 아직 없어 요청
 * 인터셉터가 헤더를 붙이지 못한다. 그 한 번만 헤더를 직접 넘긴다.
 */
export const getMe = (config?: AxiosRequestConfig) =>
  unwrap(client.get<ApiResponse<Member>>('/members/me', config));

export const updateNickname = (payload: NicknameUpdateRequest) =>
  unwrap(client.patch<ApiResponse<Member>>('/members/me', payload));

export const changePassword = async (payload: PasswordChangeRequest) => {
  await client.patch<ApiResponse<void>>('/members/me/password', payload);
};

export const withdraw = async () => {
  await client.delete<ApiResponse<void>>('/members/me');
};
