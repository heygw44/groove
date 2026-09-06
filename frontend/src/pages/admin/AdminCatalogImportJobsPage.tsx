import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';

import { CatalogImportJobStartModal } from '@/components/admin/catalog/CatalogImportJobStartModal';
import { CatalogImportJobTable } from '@/components/admin/catalog/CatalogImportJobTable';
import { Button } from '@/components/common/Button';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { EmptyState } from '@/components/common/EmptyState';
import { Pagination } from '@/components/common/Pagination';
import { Spinner } from '@/components/common/Spinner';
import { useToast } from '@/components/common/toastContext';
import { useRestartCatalogImportJob } from '@/hooks/mutations/useAdminCatalogMutations';
import { useAdminCatalogImportJobs } from '@/hooks/queries/useAdminCatalogImportJobs';
import type { CatalogImportJob } from '@/types/catalog';
import { getErrorMessage } from '@/utils/apiError';

const PAGE_SIZE = 20;

const parsePage = (value: string | null): number => {
  if (value === null || !/^\d+$/.test(value)) {
    return 0;
  }
  return Number(value);
};

export default function AdminCatalogImportJobsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePage(searchParams.get('page'));

  const [starting, setStarting] = useState(false);
  const [restartTarget, setRestartTarget] = useState<CatalogImportJob | undefined>(undefined);

  const { showToast } = useToast();
  const { data, isPending, isError, isPlaceholderData, refetch } = useAdminCatalogImportJobs({
    page,
    size: PAGE_SIZE,
  });
  const restartMutation = useRestartCatalogImportJob();

  const updatePage = (nextPage: number) => {
    setSearchParams((prev) => {
      const params = new URLSearchParams(prev);
      if (nextPage > 0) {
        params.set('page', String(nextPage));
      } else {
        params.delete('page');
      }
      return params;
    });
  };

  const handleConfirmRestart = () => {
    if (!restartTarget) {
      return;
    }

    restartMutation.mutate(restartTarget.jobExecutionId, {
      onSuccess: () => {
        showToast('success', '잡을 재시작했습니다.');
        setRestartTarget(undefined);
      },
      onError: (error) => {
        setRestartTarget(undefined);
        showToast('error', getErrorMessage(error));
      },
    });
  };

  return (
    <div>
      <div className="mb-4 flex items-end justify-between gap-6">
        <div>
          <h2 className="text-[17px] font-bold tracking-tight">적재 잡 관리</h2>
          <p className="mt-1.5 text-sm text-content-muted">
            {isPending ? '불러오는 중…' : `총 ${data?.totalElements ?? 0}개`}
          </p>
        </div>
        <Button onClick={() => setStarting(true)}>잡 실행</Button>
      </div>

      {isPending && (
        <div className="flex min-h-48 items-center justify-center">
          <Spinner />
        </div>
      )}

      {!isPending && isError && (
        <EmptyState
          title="적재 잡 목록을 불러오지 못했습니다."
          description="잠시 후 다시 시도해주세요."
          action={
            <Button variant="secondary" onClick={() => refetch()}>
              다시 시도
            </Button>
          }
        />
      )}

      {!isPending && !isError && data && data.content.length === 0 && (
        <EmptyState title="실행된 적재 잡이 없습니다." />
      )}

      {!isPending && !isError && data && data.content.length > 0 && (
        <div className={isPlaceholderData ? 'opacity-60' : ''}>
          <CatalogImportJobTable items={data.content} onRestart={setRestartTarget} />

          <div className="mt-6">
            <Pagination page={page} totalPages={data.totalPages} onChange={updatePage} />
          </div>
        </div>
      )}

      <CatalogImportJobStartModal
        open={starting}
        onClose={() => setStarting(false)}
        onStarted={() => showToast('success', '잡을 실행했습니다.')}
        onError={(message) => showToast('error', message)}
      />

      <ConfirmDialog
        open={Boolean(restartTarget)}
        onClose={() => setRestartTarget(undefined)}
        onConfirm={handleConfirmRestart}
        title="적재 잡을 재시작하시겠습니까?"
        description="실패한 지점부터 이어서 실행됩니다."
        confirmLabel="재시작"
        variant="primary"
        pending={restartMutation.isPending}
      />
    </div>
  );
}
