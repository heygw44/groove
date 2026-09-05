import { useSearchParams } from 'react-router-dom';

import { AdminAuditLogFilterBar } from '@/components/admin/audit/AdminAuditLogFilterBar';
import { AdminAuditLogTable } from '@/components/admin/audit/AdminAuditLogTable';
import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { Pagination } from '@/components/common/Pagination';
import { Spinner } from '@/components/common/Spinner';
import { useAdminAuditLogs } from '@/hooks/queries/useAdminAuditLogs';
import {
  parseAdminAuditLogFilters,
  serializeAdminAuditLogFilters,
  toAdminAuditLogListParams,
  type AdminAuditLogFilters,
} from '@/utils/adminAuditLogFilters';

export default function AdminAuditLogsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = parseAdminAuditLogFilters(searchParams);

  const { data, isPending, isError, isPlaceholderData, refetch } = useAdminAuditLogs(
    toAdminAuditLogListParams(filters),
  );

  const updateFilters = (
    patch: Partial<AdminAuditLogFilters>,
    options?: { replace?: boolean },
  ) => {
    setSearchParams(serializeAdminAuditLogFilters({ ...filters, ...patch, page: 0 }), options);
  };

  const updatePage = (page: number) => {
    setSearchParams(serializeAdminAuditLogFilters({ ...filters, page }));
  };

  return (
    <div>
      <div className="mb-4 flex items-end justify-between gap-6">
        <div>
          <h2 className="text-[17px] font-bold tracking-tight">감사 로그</h2>
          <p className="mt-1.5 text-sm text-content-muted">
            {isPending ? '불러오는 중…' : `총 ${data?.totalElements ?? 0}건`}
          </p>
        </div>
      </div>

      <div className="mb-4">
        <AdminAuditLogFilterBar filters={filters} onChange={updateFilters} />
      </div>

      {isPending && (
        <div className="flex min-h-48 items-center justify-center">
          <Spinner />
        </div>
      )}

      {!isPending && isError && (
        <EmptyState
          title="감사 로그를 불러오지 못했습니다."
          description="잠시 후 다시 시도해주세요."
          action={
            <Button variant="secondary" onClick={() => refetch()}>
              다시 시도
            </Button>
          }
        />
      )}

      {!isPending && !isError && data && data.content.length === 0 && (
        <EmptyState title="조건에 맞는 감사 로그가 없습니다." />
      )}

      {!isPending && !isError && data && data.content.length > 0 && (
        <div className={isPlaceholderData ? 'opacity-60' : ''}>
          <AdminAuditLogTable logs={data.content} />

          <div className="mt-6">
            <Pagination page={filters.page} totalPages={data.totalPages} onChange={updatePage} />
          </div>
        </div>
      )}
    </div>
  );
}
