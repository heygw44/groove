import { useMutation, useQueryClient } from '@tanstack/react-query';

import { changeAdminMemberStatus } from '@/api/admin';
import { adminMemberKeys } from '@/hooks/queries/queryKeys';
import type { AdminMemberChangeableStatus, AdminMemberDetail } from '@/types/adminMember';

export const useChangeMemberStatus = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      memberId,
      status,
      reason,
    }: {
      memberId: number;
      status: AdminMemberChangeableStatus;
      reason?: string;
    }) => changeAdminMemberStatus(memberId, { status, reason }),
    onSuccess: (data: AdminMemberDetail, { memberId }) => {
      queryClient.setQueryData(adminMemberKeys.detail(memberId), data);
      queryClient.invalidateQueries({ queryKey: adminMemberKeys.lists });
    },
  });
};
