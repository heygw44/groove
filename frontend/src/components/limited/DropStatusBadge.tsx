import { Badge, type BadgeVariant } from '@/components/common/Badge';
import type { LimitedDropStatus } from '@/types/limitedDrop';

const STATUS_LABEL: Record<LimitedDropStatus, string> = {
  SCHEDULED: '예정',
  OPEN: '진행중',
  SOLD_OUT: '매진',
  CLOSED: '마감',
};

const STATUS_VARIANT: Record<LimitedDropStatus, BadgeVariant> = {
  SCHEDULED: 'neutral',
  OPEN: 'success',
  SOLD_OUT: 'danger',
  CLOSED: 'neutral',
};

interface DropStatusBadgeProps {
  status: LimitedDropStatus;
  className?: string;
}

export function DropStatusBadge({ status, className }: DropStatusBadgeProps) {
  return (
    <Badge variant={STATUS_VARIANT[status]} className={className}>
      {STATUS_LABEL[status]}
    </Badge>
  );
}
