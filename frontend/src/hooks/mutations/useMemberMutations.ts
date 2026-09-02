import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';

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

export const useWithdraw = () => {
  const queryClient = useQueryClient();
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const navigate = useNavigate();

  return useMutation({
    mutationFn: withdraw,
    onSuccess: () => {
      clearAuth();
      queryClient.clear();
      navigate('/', { replace: true });
    },
  });
};
