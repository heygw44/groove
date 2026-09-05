import { Badge, type BadgeVariant } from '@/components/common/Badge';
import { MEMBER_STATUS_LABELS } from '@/constants/adminAudit';
import type { MemberStatus } from '@/types/member';

interface MemberStatusBadgeProps {
  status: MemberStatus;
}

const MEMBER_STATUS_BADGE: Record<MemberStatus, BadgeVariant> = {
  ACTIVE: 'success',
  SUSPENDED: 'danger',
  WITHDRAWN: 'neutral',
};

export function MemberStatusBadge({ status }: MemberStatusBadgeProps) {
  return <Badge variant={MEMBER_STATUS_BADGE[status]}>{MEMBER_STATUS_LABELS[status]}</Badge>;
}
