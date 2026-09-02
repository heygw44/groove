import { useQuery } from '@tanstack/react-query';

import { getAddresses } from '@/api/address';
import { addressKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';

export const useAddresses = () => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);

  return useQuery({
    queryKey: addressKeys.all,
    queryFn: getAddresses,
    enabled: Boolean(accessToken) && !isBootstrapping,
  });
};
