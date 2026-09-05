import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { getAdminMember, getAdminMembers } from '@/api/admin';
import { adminMemberKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { AdminMemberListParams } from '@/types/adminMember';

export const useAdminMembers = (params: AdminMemberListParams) => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: adminMemberKeys.list(params),
    queryFn: () => getAdminMembers(params),
    enabled: Boolean(accessToken) && !isBootstrapping,
    placeholderData: keepPreviousData,
  });
};

export const useAdminMember = (memberId: number) => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: adminMemberKeys.detail(memberId),
    queryFn: () => getAdminMember(memberId),
    enabled: Boolean(accessToken) && !isBootstrapping && Number.isInteger(memberId) && memberId > 0,
  });
};
