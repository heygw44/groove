import { useState } from 'react';

import { ReconcileLogTable } from '@/components/admin/dashboard/ReconcileLogTable';
import { Pagination } from '@/components/common/Pagination';
import { QueryErrorState } from '@/components/common/QueryErrorState';
import { TableSkeleton } from '@/components/common/TableSkeleton';
import { useAdminReconcileLogs } from '@/hooks/queries/useAdminStats';

const PAGE_SIZE = 20;

type RepairedFilter = 'unresolved' | 'all';

export function ReconcileLogSection() {
  const [filter, setFilter] = useState<RepairedFilter>('unresolved');
  const [page, setPage] = useState(0);

  const { data, isPending, isError, error, isPlaceholderData, refetch } = useAdminReconcileLogs({
    repaired: filter === 'unresolved' ? false : undefined,
    page,
    size: PAGE_SIZE,
  });

  const changeFilter = (next: RepairedFilter) => {
    setFilter(next);
    setPage(0);
  };

  return (
    <section id="reconcile-logs" className="flex scroll-mt-6 flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="text-sm font-bold text-content">집계 대사 로그</h3>
        <div className="flex gap-1 rounded-md bg-surface-muted p-0.5 text-sm">
          <button
            type="button"
            onClick={() => changeFilter('unresolved')}
            className={`rounded px-3 py-1 ${
              filter === 'unresolved' ? 'bg-surface font-medium text-content shadow-sm' : 'text-content-muted'
            }`}
          >
            미해결만
          </button>
          <button
            type="button"
            onClick={() => changeFilter('all')}
            className={`rounded px-3 py-1 ${
              filter === 'all' ? 'bg-surface font-medium text-content shadow-sm' : 'text-content-muted'
            }`}
          >
            전체
          </button>
        </div>
      </div>

      {isPending && <TableSkeleton columns={7} />}

      {!isPending && isError && (
        <QueryErrorState error={error} onRetry={refetch} title="대사 로그를 불러오지 못했습니다" />
      )}

      {!isPending && !isError && data && (
        <div className={isPlaceholderData ? 'opacity-60' : ''}>
          <ReconcileLogTable logs={data.content} />

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
