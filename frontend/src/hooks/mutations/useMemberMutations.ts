import { useMutation, useQueryClient } from '@tanstack/react-query';

import { changePassword, updateNickname, withdraw } from '@/api/member';
import { memberKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { NicknameUpdateRequest, PasswordChangeRequest } from '@/types/member';

export const useUpdateNickname = () => {
  const queryClient = useQueryClient();
  const setMember = useAuthStore((s) => s.setMember);

  return useMutation({
    mutationFn: (payload: NicknameUpdateRequest) => updateNickname(payload),
    /* 헤더는 스토어를, 마이페이지는 쿼리 캐시를 본다. 둘 다 갱신해야 한다. */
    onSuccess: (member) => {
      setMember(member);
      queryClient.setQueryData(memberKeys.me, member);
    },
  });
};

export const useChangePassword = () =>
  useMutation({ mutationFn: (payload: PasswordChangeRequest) => changePassword(payload) });

export const useWithdraw = () =>
  useMutation({
    mutationFn: withdraw,
    /*
     * 라우터 이동이 아니라 페이지를 새로 연다. 스토어를 비우는 순간 아직
     * /mypage 에 있는 PrivateRoute 가 토큰 없음을 보고 /login?redirect=/mypage
     * 로 보내버려, 탈퇴한 사람에게 다시 로그인하라는 화면이 뜬다. 둘의 순서를
     * 바꿔도 데이터 라우터의 네비게이션이 비동기라 경합이 남는다.
     * 세션이 끝났다는 점에서 인터셉터의 세션 만료 처리와 같은 성격이고,
     * 새로 열면 스토어와 캐시가 함께 사라져 정리할 것도 없다.
     */
    onSuccess: () => {
      window.location.replace('/');
    },
  });
