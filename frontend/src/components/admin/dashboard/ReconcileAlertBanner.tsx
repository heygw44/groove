import { Link } from 'react-router-dom';

import { useAdminReconcileLogs } from '@/hooks/queries/useAdminStats';

/**
 * 자동 복구되지 않은 대사 불일치를 알린다. 목록 전체가 아니라 totalElements 만 필요해
 * size=1 로 가볍게 조회한다. 정상(0건)일 때 자리를 차지하면 신호가 죽으므로 렌더하지
 * 않고, 로딩·에러 중에도 또 다른 경고를 띄우지 않도록 조용히 넘어간다.
 */
export function ReconcileAlertBanner() {
  const { data, isPending, isError } = useAdminReconcileLogs({ repaired: false, page: 0, size: 1 });

  if (isPending || isError || !data || data.totalElements === 0) {
    return null;
  }

  return (
    <div className="flex items-center justify-between gap-4 rounded-lg border border-danger-line bg-danger-soft px-4 py-3">
      <p className="text-sm font-medium text-danger">
        집계 불일치 {data.totalElements}건이 자동 복구되지 않았습니다.
      </p>
      <Link
        to="#reconcile-logs"
        className="shrink-0 text-sm font-medium text-danger underline underline-offset-2 hover:text-danger/80"
      >
        확인하기
      </Link>
    </div>
  );
}
