import { useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';

import { login, logout, signup } from '@/api/auth';
import { getMe } from '@/api/member';
import { memberKeys } from '@/hooks/queries/queryKeys';
import { queryClient } from '@/lib/queryClient';
import { useAuthStore } from '@/store/authStore';
import type { LoginRequest, SignupRequest } from '@/types/member';

export const useSignup = () =>
  useMutation({ mutationFn: (payload: SignupRequest) => signup(payload) });

export const useLogin = () => {
  const setAuth = useAuthStore((s) => s.setAuth);

  return useMutation({
    /*
     * 로그인 응답에는 회원 정보가 없어 곧바로 내 정보를 한 번 더 불러온다.
     * 토큰만 먼저 저장하면 member 가 비어 있는 반쪽 인증 상태가 생기고,
     * 조회가 실패했을 때 되돌리는 코드가 따로 필요해진다. 둘 다 성공해야
     * 스토어에 반영되도록 한 함수 안에서 이어 붙였다.
     */
    mutationFn: async (payload: LoginRequest) => {
      const { accessToken } = await login(payload);
      const member = await getMe({ headers: { Authorization: `Bearer ${accessToken}` } });
      return { accessToken, member };
    },
    onSuccess: ({ accessToken, member }) => {
      setAuth(accessToken, member);
      queryClient.setQueryData(memberKeys.me, member);
      /*
       * 로그인 여부에 따라 응답이 달라지는 공개 쿼리(상품 wishlisted, 리뷰 mine,
       * 리뷰 작성 가능 여부)가 로그인 전 값으로 staleTime 동안 남지 않게 전부 다시 받는다.
       */
      queryClient.invalidateQueries();
    },
  });
};

export const useLogout = () => {
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const navigate = useNavigate();

  return useMutation({
    mutationFn: logout,
    /*
     * 서버 호출이 실패해도 클라이언트는 반드시 로그아웃한다. 실패했다고
     * 로그인 상태에 갇히는 쪽이 더 나쁘다.
     */
    onSettled: () => {
      clearAuth();
      queryClient.clear();
      navigate('/login', { replace: true });
    },
  });
};
