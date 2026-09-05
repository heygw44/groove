import { Badge } from '@/components/common/Badge';
import type { AdminCouponDisplayStatus } from '@/types/coupon';

const STATUS_LABEL: Record<AdminCouponDisplayStatus, { label: string; variant: 'success' | 'danger' | 'neutral' }> = {
  ACTIVE: { label: '활성', variant: 'success' },
  EXPIRED: { label: '만료', variant: 'neutral' },
  DISABLED: { label: '비활성', variant: 'danger' },
};

export function AdminCouponStatusBadge({ status }: { status: AdminCouponDisplayStatus }) {
  const { label, variant } = STATUS_LABEL[status];
  return <Badge variant={variant}>{label}</Badge>;
}
