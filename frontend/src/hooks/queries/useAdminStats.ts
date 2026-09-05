import { keepPreviousData, useQuery } from '@tanstack/react-query';

import {
  getAdminDailySales,
  getAdminLimitedDropStats,
  getAdminPopularProducts,
  getAdminStatsSummary,
} from '@/api/admin';
import { adminStatsKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { PopularProductParams, StatsPeriodParams } from '@/types/adminStats';

const useAdminAuthGate = () => {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isBootstrapping = useAuthStore((s) => s.isBootstrapping);
  return Boolean(accessToken) && !isBootstrapping;
};

export const useAdminStatsSummary = () => {
  const enabled = useAdminAuthGate();

  return useQuery({
    queryKey: adminStatsKeys.summary,
    queryFn: () => getAdminStatsSummary(),
    enabled,
  });
};

export const useAdminDailySales = (params: StatsPeriodParams) => {
  const enabled = useAdminAuthGate();

  return useQuery({
    queryKey: adminStatsKeys.dailySales(params),
    queryFn: () => getAdminDailySales(params),
    enabled,
    placeholderData: keepPreviousData,
  });
};

export const useAdminPopularProducts = (params: PopularProductParams) => {
  const enabled = useAdminAuthGate();

  return useQuery({
    queryKey: adminStatsKeys.popularProducts(params),
    queryFn: () => getAdminPopularProducts(params),
    enabled,
    placeholderData: keepPreviousData,
  });
};

export const useAdminLimitedDropStats = () => {
  const enabled = useAdminAuthGate();

  return useQuery({
    queryKey: adminStatsKeys.limitedDrops,
    queryFn: () => getAdminLimitedDropStats(),
    enabled,
  });
};
