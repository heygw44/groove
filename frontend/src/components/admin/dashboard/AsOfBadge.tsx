import { formatRelativeFromNow } from '@/utils/formatDate';

interface AsOfBadgeProps {
  aggregatedAt: string | null;
}

/** 사전 집계 데이터의 지연을 알리는 배지. 집계 행이 없어 aggregatedAt 이 null 이면 아무것도 렌더하지 않는다. */
export function AsOfBadge({ aggregatedAt }: AsOfBadgeProps) {
  if (aggregatedAt === null) {
    return null;
  }

  return (
    <span className="rounded-full bg-surface-muted px-2 py-0.5 text-xs text-content-muted">
      {formatRelativeFromNow(aggregatedAt)} 기준
    </span>
  );
}
