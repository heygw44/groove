import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';

import { DailySalesChart } from '@/components/admin/dashboard/DailySalesChart';
import { LimitedDropStatsTable } from '@/components/admin/dashboard/LimitedDropStatsTable';
import { PopularProductTable } from '@/components/admin/dashboard/PopularProductTable';
import { StatCard } from '@/components/admin/dashboard/StatCard';
import { StatsPeriodSelector } from '@/components/admin/dashboard/StatsPeriodSelector';
import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { Spinner } from '@/components/common/Spinner';
import { adminStatsKeys } from '@/hooks/queries/queryKeys';
import {
  useAdminDailySales,
  useAdminLimitedDropStats,
  useAdminPopularProducts,
  useAdminStatsSummary,
} from '@/hooks/queries/useAdminStats';
import type { PopularProductSort } from '@/types/adminStats';
import {
  parseStatsPeriod,
  resolvePresetPeriod,
  serializeStatsPeriod,
} from '@/utils/adminStatsFilters';
import { formatPrice } from '@/utils/formatPrice';
import { getServerNow } from '@/utils/serverTime';

const POPULAR_PRODUCT_LIMIT = 10;

export default function AdminDashboardPage() {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [sort, setSort] = useState<PopularProductSort>('quantity');

  const parsedPeriod = parseStatsPeriod(searchParams);
  const period =
    parsedPeriod.from !== undefined && parsedPeriod.to !== undefined
      ? { from: parsedPeriod.from, to: parsedPeriod.to }
      : resolvePresetPeriod('30d', getServerNow());

  const handlePeriodChange = (next: { from: string; to: string }) => {
    setSearchParams(serializeStatsPeriod(next));
  };

  const handleRefresh = () => {
    void queryClient.invalidateQueries({ queryKey: adminStatsKeys.all });
  };

  const summaryQuery = useAdminStatsSummary();
  const dailySalesQuery = useAdminDailySales(period);
  const popularProductsQuery = useAdminPopularProducts({
    ...period,
    limit: POPULAR_PRODUCT_LIMIT,
    sort,
  });
  const limitedDropsQuery = useAdminLimitedDropStats();

  return (
    <div className="flex flex-col gap-8">
      <div className="flex items-center justify-between gap-4">
        <h2 className="text-[17px] font-bold tracking-tight">대시보드</h2>
        <Button variant="secondary" size="sm" onClick={handleRefresh}>
          새로고침
        </Button>
      </div>

      <section>
        {summaryQuery.isPending && (
          <div className="flex min-h-24 items-center justify-center">
            <Spinner />
          </div>
        )}

        {!summaryQuery.isPending && summaryQuery.isError && (
          <EmptyState
            title="요약 정보를 불러오지 못했습니다."
            description="잠시 후 다시 시도해주세요."
            action={
              <Button variant="secondary" onClick={() => summaryQuery.refetch()}>
                다시 시도
              </Button>
            }
          />
        )}

        {!summaryQuery.isPending && !summaryQuery.isError && summaryQuery.data && (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard label="오늘 매출" value={formatPrice(summaryQuery.data.todaySalesAmount)} />
            <StatCard label="오늘 주문" value={`${summaryQuery.data.todayOrderCount}건`} />
            <StatCard label="오늘 신규 회원" value={`${summaryQuery.data.todayNewMemberCount}명`} />
            <StatCard
              label="결제 대기"
              value={`${summaryQuery.data.pendingOrderCount}건`}
              to="/admin/orders?status=PENDING"
            />
          </div>
        )}
      </section>

      <section className="flex flex-col gap-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h3 className="text-sm font-bold text-content">일별 매출</h3>
          <StatsPeriodSelector value={period} onChange={handlePeriodChange} />
        </div>

        {dailySalesQuery.isPending && (
          <div className="flex min-h-48 items-center justify-center">
            <Spinner />
          </div>
        )}

        {!dailySalesQuery.isPending && dailySalesQuery.isError && (
          <EmptyState
            title="매출 데이터를 불러오지 못했습니다."
            description="잠시 후 다시 시도해주세요."
            action={
              <Button variant="secondary" onClick={() => dailySalesQuery.refetch()}>
                다시 시도
              </Button>
            }
          />
        )}

        {!dailySalesQuery.isPending && !dailySalesQuery.isError && dailySalesQuery.data && (
          <div className={dailySalesQuery.isPlaceholderData ? 'opacity-60' : ''}>
            <DailySalesChart data={dailySalesQuery.data} />
          </div>
        )}
      </section>

      <section className="flex flex-col gap-4">
        <h3 className="text-sm font-bold text-content">인기 상품</h3>

        {popularProductsQuery.isPending && (
          <div className="flex min-h-48 items-center justify-center">
            <Spinner />
          </div>
        )}

        {!popularProductsQuery.isPending && popularProductsQuery.isError && (
          <EmptyState
            title="인기 상품을 불러오지 못했습니다."
            description="잠시 후 다시 시도해주세요."
            action={
              <Button variant="secondary" onClick={() => popularProductsQuery.refetch()}>
                다시 시도
              </Button>
            }
          />
        )}

        {!popularProductsQuery.isPending &&
          !popularProductsQuery.isError &&
          popularProductsQuery.data && (
            <div className={popularProductsQuery.isPlaceholderData ? 'opacity-60' : ''}>
              <PopularProductTable
                items={popularProductsQuery.data}
                sort={sort}
                onSortChange={setSort}
              />
            </div>
          )}
      </section>

      <section className="flex flex-col gap-4">
        <h3 className="text-sm font-bold text-content">한정반 현황</h3>

        {limitedDropsQuery.isPending && (
          <div className="flex min-h-48 items-center justify-center">
            <Spinner />
          </div>
        )}

        {!limitedDropsQuery.isPending && limitedDropsQuery.isError && (
          <EmptyState
            title="한정반 현황을 불러오지 못했습니다."
            description="잠시 후 다시 시도해주세요."
            action={
              <Button variant="secondary" onClick={() => limitedDropsQuery.refetch()}>
                다시 시도
              </Button>
            }
          />
        )}

        {!limitedDropsQuery.isPending && !limitedDropsQuery.isError && limitedDropsQuery.data && (
          <LimitedDropStatsTable items={limitedDropsQuery.data} />
        )}
      </section>
    </div>
  );
}
