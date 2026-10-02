import { formatRelativeFromNow } from '@/utils/formatDate';

interface AsOfBadgeProps {
  aggregatedAt: string | null;
}

/** 사전 집계 데이터의 마지막 갱신 시각(범위 내 가장 최근)과 집계 주기를 알리는 배지. 집계 행이 없어 aggregatedAt 이 null 이면 아무것도 렌더하지 않는다. */
export function AsOfBadge({ aggregatedAt }: AsOfBadgeProps) {
  if (aggregatedAt === null) {
    return null;
  }

  return (
    <span className="flex flex-col items-end gap-0.5">
      <span className="rounded-full bg-surface-muted px-2 py-0.5 text-xs text-content-muted">
        최근 갱신 {formatRelativeFromNow(aggregatedAt)}
      </span>
      <span className="text-[11px] text-content-muted">
        오늘은 15분마다, 최근 7일은 매일 새벽 다시 집계합니다.
      </span>
    </span>
  );
}
