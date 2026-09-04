import { useState } from 'react';

import { Badge } from '@/components/common/Badge';
import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { Modal } from '@/components/common/Modal';
import type { AvailableCoupon } from '@/types/coupon';
import { formatCouponDiscount } from '@/utils/coupon';
import { formatDate } from '@/utils/formatDate';
import { formatPrice } from '@/utils/formatPrice';

interface CouponSelectModalProps {
  open: boolean;
  onClose: () => void;
  coupons: AvailableCoupon[];
  selectedId?: number;
  onApply: (coupon: AvailableCoupon | null) => void;
}

export function CouponSelectModal({
  open,
  onClose,
  coupons,
  selectedId,
  onApply,
}: CouponSelectModalProps) {
  const [pendingId, setPendingId] = useState<number | undefined>(selectedId);

  // 모달을 다시 열 때마다 현재 적용된 쿠폰으로 임시 선택을 되돌린다(렌더 중 상태 조정).
  const [prevOpen, setPrevOpen] = useState(open);
  if (open !== prevOpen) {
    setPrevOpen(open);
    if (open) {
      setPendingId(selectedId);
    }
  }

  const maxDiscount = coupons.reduce((max, coupon) => Math.max(max, coupon.expectedDiscount), 0);

  const handleApply = () => {
    const selected = coupons.find((coupon) => coupon.memberCouponId === pendingId) ?? null;
    onApply(selected);
    onClose();
  };

  if (coupons.length === 0) {
    return (
      <Modal open={open} onClose={onClose} title="쿠폰 선택">
        <EmptyState
          title="적용 가능한 쿠폰이 없습니다."
          description="최소 주문 금액을 확인해주세요."
          action={<Button onClick={onClose}>닫기</Button>}
        />
      </Modal>
    );
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="쿠폰 선택"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            취소
          </Button>
          <Button onClick={handleApply}>적용</Button>
        </>
      }
    >
      <fieldset className="flex flex-col gap-2">
        <legend className="sr-only">적용할 쿠폰</legend>

        <label
          className={`flex cursor-pointer items-center gap-3 rounded-md border px-4 py-3 ${
            pendingId === undefined ? 'border-accent bg-accent-soft' : 'border-line'
          }`}
        >
          <input
            type="radio"
            name="coupon"
            className="sr-only"
            checked={pendingId === undefined}
            onChange={() => setPendingId(undefined)}
          />
          <span className="text-sm font-medium text-content">적용 안 함</span>
        </label>

        {coupons.map((coupon) => (
          <label
            key={coupon.memberCouponId}
            className={`flex cursor-pointer items-start justify-between gap-3 rounded-md border px-4 py-3 ${
              pendingId === coupon.memberCouponId ? 'border-accent bg-accent-soft' : 'border-line'
            }`}
          >
            <input
              type="radio"
              name="coupon"
              className="sr-only"
              checked={pendingId === coupon.memberCouponId}
              onChange={() => setPendingId(coupon.memberCouponId)}
            />
            <div className="flex flex-col gap-1">
              <div className="flex items-center gap-1.5">
                <span className="text-sm font-bold text-content">{coupon.couponName}</span>
                {coupon.expectedDiscount === maxDiscount && (
                  <Badge variant="accent">최대 할인</Badge>
                )}
              </div>
              <p className="text-xs text-content-muted">{formatCouponDiscount(coupon)}</p>
              {coupon.minOrderAmount > 0 && (
                <p className="text-xs text-content-muted">
                  최소 주문 금액 {formatPrice(coupon.minOrderAmount)}
                </p>
              )}
              <p className="text-xs text-content-subtle">만료 {formatDate(coupon.expiresAt)}</p>
            </div>
            <span className="shrink-0 text-sm font-bold text-content">
              -{formatPrice(coupon.expectedDiscount)}
            </span>
          </label>
        ))}
      </fieldset>
    </Modal>
  );
}
