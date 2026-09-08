import { Badge } from '@/components/common/Badge';
import { Skeleton } from '@/components/common/Skeleton';
import type { MemberCoupon } from '@/types/coupon';
import {
  formatCouponDiscount,
  formatDDay,
  getDaysUntil,
  getMemberCouponStatus,
} from '@/utils/coupon';
import { formatServerDate } from '@/utils/formatDate';
import { formatPrice } from '@/utils/formatPrice';

interface CouponCardProps {
  coupon: MemberCoupon;
}

const D_DAY_URGENT_THRESHOLD = 7;

function CouponStatusBadge({ coupon }: { coupon: MemberCoupon }) {
  const status = getMemberCouponStatus(coupon);

  if (status === 'used') {
    return <Badge variant="neutral">사용 완료</Badge>;
  }
  if (status === 'expired') {
    return <Badge variant="neutral">만료</Badge>;
  }

  const days = getDaysUntil(coupon.expiresAt);
  return (
    <Badge variant={days <= D_DAY_URGENT_THRESHOLD ? 'danger' : 'neutral'}>
      {formatDDay(days)}
    </Badge>
  );
}

export function CouponCard({ coupon }: CouponCardProps) {
  const status = getMemberCouponStatus(coupon);
  const isInactive = status !== 'usable';

  return (
    <article
      className={`rounded-lg border border-line bg-surface p-4 ${isInactive ? 'opacity-60' : ''}`}
    >
      <div className="flex items-start justify-between gap-2">
        <strong className="text-sm font-bold">{coupon.couponName}</strong>
        <CouponStatusBadge coupon={coupon} />
      </div>

      <p className="mt-2 text-xl font-bold">{formatCouponDiscount(coupon)}</p>

      <p className="mt-1 text-xs text-content-muted">
        {coupon.minOrderAmount > 0
          ? `최소 주문 금액 ${formatPrice(coupon.minOrderAmount)}`
          : '최소 주문 금액 없음'}
      </p>

      <p className="mt-2 font-mono text-xs text-content-subtle">{coupon.couponCode}</p>

      <p className="mt-1 text-xs text-content-muted">
        {status === 'used' && coupon.usedAt
          ? `사용일 ${formatServerDate(coupon.usedAt)}`
          : `만료일 ${formatServerDate(coupon.expiresAt)}`}
      </p>
    </article>
  );
}

export function CouponCardSkeleton() {
  return (
    <div className="rounded-lg border border-line bg-surface p-4">
      <div className="flex items-start justify-between gap-2">
        <Skeleton className="h-4 w-2/5" />
        <Skeleton className="h-5 w-14" />
      </div>
      <Skeleton className="mt-2 h-7 w-1/3" />
      <Skeleton className="mt-1.5 h-3 w-3/5" />
      <Skeleton className="mt-2 h-3 w-2/5" />
      <Skeleton className="mt-1.5 h-3 w-1/3" />
    </div>
  );
}
