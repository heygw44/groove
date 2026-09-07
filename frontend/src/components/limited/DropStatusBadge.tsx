import { Badge, type BadgeVariant } from '@/components/common/Badge';
import type { LimitedDropStatus } from '@/types/limitedDrop';
import type { DropPhase } from '@/utils/limitedDrop';

const PHASE_LABEL: Record<DropPhase, string> = {
  SCHEDULED: '예정',
  OPENING: '오픈 중',
  OPEN: '진행중',
  SOLD_OUT: '매진',
  CLOSED: '마감',
};

const PHASE_VARIANT: Record<DropPhase, BadgeVariant> = {
  SCHEDULED: 'neutral',
  OPENING: 'success',
  OPEN: 'success',
  SOLD_OUT: 'danger',
  CLOSED: 'neutral',
};

type DropStatusBadgeProps = { className?: string } & (
  { status: LimitedDropStatus; phase?: never } | { phase: DropPhase; status?: never }
);

/** OPENING(스케줄 시각은 지났지만 상태 갱신 전) 을 표시하려면 status 대신 phase 를 넘긴다. */
export function DropStatusBadge({ status, phase, className }: DropStatusBadgeProps) {
  const resolvedPhase = phase ?? (status as DropPhase);
  return (
    <Badge variant={PHASE_VARIANT[resolvedPhase]} className={className}>
      {PHASE_LABEL[resolvedPhase]}
    </Badge>
  );
}
