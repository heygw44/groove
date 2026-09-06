import { Badge, type BadgeVariant } from '@/components/common/Badge';
import type { CatalogImportJobStatus } from '@/types/catalog';

interface CatalogImportJobStatusBadgeProps {
  status: CatalogImportJobStatus;
}

const KNOWN_STATUS_LABEL: Partial<Record<CatalogImportJobStatus, string>> = {
  STARTING: '시작 중',
  STARTED: '진행 중',
  COMPLETED: '완료',
  FAILED: '실패',
  STOPPED: '중지됨',
};

const KNOWN_STATUS_VARIANT: Partial<Record<CatalogImportJobStatus, BadgeVariant>> = {
  STARTING: 'accent',
  STARTED: 'accent',
  COMPLETED: 'success',
  FAILED: 'danger',
  STOPPED: 'neutral',
};

// 문서에 적힌 5개(STARTING/STARTED/COMPLETED/FAILED/STOPPED) 외의 BatchStatus 값은
// 맵에 없으면 중립 뱃지에 원문을 그대로 보여주는 fallback 으로 처리한다.
export function CatalogImportJobStatusBadge({ status }: CatalogImportJobStatusBadgeProps) {
  const label = KNOWN_STATUS_LABEL[status] ?? status;
  const variant = KNOWN_STATUS_VARIANT[status] ?? 'neutral';
  return <Badge variant={variant}>{label}</Badge>;
}
