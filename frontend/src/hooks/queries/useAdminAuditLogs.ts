import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { getAdminAuditLogs } from '@/api/admin';
import { adminAuditLogKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { AdminAuditLogListParams } from '@/types/adminAuditLog';

export const useAdminAuditLogs = (params: AdminAuditLogListParams) => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: adminAuditLogKeys.list(params),
    queryFn: () => getAdminAuditLogs(params),
    enabled: Boolean(accessToken) && !isBootstrapping,
    placeholderData: keepPreviousData,
  });
};
