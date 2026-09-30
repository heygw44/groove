import { useQuery } from '@tanstack/react-query';

import { getAdminOrderClaimCounts } from '@/api/admin';
import { adminOrderClaimKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';

export const useAdminOrderClaimCounts = () => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: adminOrderClaimKeys.counts(),
    queryFn: getAdminOrderClaimCounts,
    enabled: Boolean(accessToken) && !isBootstrapping,
  });
};
