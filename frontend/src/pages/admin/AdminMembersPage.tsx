import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';

import { AdminMemberDetailDrawer } from '@/components/admin/member/AdminMemberDetailDrawer';
import { AdminMemberFilterBar } from '@/components/admin/member/AdminMemberFilterBar';
import { AdminMemberTable } from '@/components/admin/member/AdminMemberTable';
import { EmptyState } from '@/components/common/EmptyState';
import { Pagination } from '@/components/common/Pagination';
import { QueryErrorState } from '@/components/common/QueryErrorState';
import { TableSkeleton } from '@/components/common/TableSkeleton';
import { useAdminMembers } from '@/hooks/queries/useAdminMembers';
import type { AdminMemberSummary } from '@/types/adminMember';
import {
  parseAdminMemberFilters,
  serializeAdminMemberFilters,
  toAdminMemberListParams,
  type AdminMemberFilters,
} from '@/utils/adminMemberFilters';

export default function AdminMembersPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = parseAdminMemberFilters(searchParams);

  const [selectedMemberId, setSelectedMemberId] = useState<number>();

  const { data, isPending, isError, error, isPlaceholderData, refetch } = useAdminMembers(
    toAdminMemberListParams(filters),
  );

  const updateFilters = (patch: Partial<AdminMemberFilters>, options?: { replace?: boolean }) => {
    setSearchParams(serializeAdminMemberFilters({ ...filters, ...patch, page: 0 }), options);
  };

  const updatePage = (page: number) => {
    setSearchParams(serializeAdminMemberFilters({ ...filters, page }));
  };

  const handleSelect = (member: AdminMemberSummary) => setSelectedMemberId(member.id);

  return (
    <div>
      <div className="mb-4 flex items-end justify-between gap-6">
        <div>
          <h2 className="text-[17px] font-bold tracking-tight">회원 관리</h2>
          <p className="mt-1.5 text-sm text-content-muted">
            {isPending ? '불러오는 중…' : `총 ${data?.totalElements ?? 0}명`}
          </p>
        </div>
      </div>

      <div className="mb-4">
        <AdminMemberFilterBar filters={filters} onChange={updateFilters} />
      </div>

      {isPending && <TableSkeleton columns={7} />}

      {!isPending && isError && (
        <QueryErrorState error={error} onRetry={refetch} title="회원을 불러오지 못했습니다" />
      )}

      {!isPending && !isError && data && data.content.length === 0 && (
        <EmptyState title="조건에 맞는 회원이 없습니다" />
      )}

      {!isPending && !isError && data && data.content.length > 0 && (
        <div className={isPlaceholderData ? 'opacity-60' : ''}>
          <AdminMemberTable members={data.content} onSelect={handleSelect} />

          <div className="mt-6">
            <Pagination page={filters.page} totalPages={data.totalPages} onChange={updatePage} />
          </div>
        </div>
      )}

      <AdminMemberDetailDrawer
        memberId={selectedMemberId}
        onClose={() => setSelectedMemberId(undefined)}
      />
    </div>
  );
}
