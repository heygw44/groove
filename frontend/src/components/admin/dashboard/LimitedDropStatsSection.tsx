import { useState } from 'react';

import { LimitedDropStatsTable } from '@/components/admin/dashboard/LimitedDropStatsTable';
import { Pagination } from '@/components/common/Pagination';
import { QueryErrorState } from '@/components/common/QueryErrorState';
import { TableSkeleton } from '@/components/common/TableSkeleton';
import { useAdminLimitedDropStats } from '@/hooks/queries/useAdminStats';

const PAGE_SIZE = 20;

export function LimitedDropStatsSection() {
  const [page, setPage] = useState(0);

  const { data, isPending, isError, error, isPlaceholderData, refetch } = useAdminLimitedDropStats({
    page,
    size: PAGE_SIZE,
  });

  return (
    <section className="flex flex-col gap-4">
      <h3 className="text-sm font-bold text-content">한정반 현황</h3>

      {isPending && <TableSkeleton columns={8} />}

      {!isPending && isError && (
        <QueryErrorState error={error} onRetry={refetch} title="한정반 현황을 불러오지 못했습니다" />
      )}

      {!isPending && !isError && data && (
        <div className={isPlaceholderData ? 'opacity-60' : ''}>
          <LimitedDropStatsTable items={data.content} />

          {data.totalPages > 1 && (
            <div className="mt-6">
              <Pagination page={page} totalPages={data.totalPages} onChange={setPage} />
            </div>
          )}
        </div>
      )}
    </section>
  );
}
