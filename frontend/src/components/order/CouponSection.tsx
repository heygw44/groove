import { useEffect, useState } from 'react';

import { Button } from '@/components/common/Button';
import { Spinner } from '@/components/common/Spinner';
import { useToast } from '@/components/common/toastContext';
import { CouponSelectModal } from '@/components/order/CouponSelectModal';
import { useAvailableCoupons } from '@/hooks/queries/useAvailableCoupons';
import type { AvailableCoupon } from '@/types/coupon';
import { formatCouponDiscount } from '@/utils/coupon';
import { formatPrice } from '@/utils/formatPrice';

interface CouponSectionProps {
  orderAmount: number;
  selected: AvailableCoupon | null;
  onSelect: (coupon: AvailableCoupon | null) => void;
}

export function CouponSection({ orderAmount, selected, onSelect }: CouponSectionProps) {
  const { showToast } = useToast();
  const { data: coupons, isPending, isError, refetch } = useAvailableCoupons(orderAmount);
  const [isModalOpen, setIsModalOpen] = useState(false);

  // 재조회 결과에 선택된 쿠폰이 더 이상 없으면(만료·사용됨) 자동으로 해제한다.
  useEffect(() => {
    if (coupons === undefined || selected === null) {
      return;
    }
    const stillAvailable = coupons.some(
      (coupon) => coupon.memberCouponId === selected.memberCouponId,
    );
    if (!stillAvailable) {
      onSelect(null);
      showToast('info', '선택한 쿠폰을 더 이상 적용할 수 없어 해제했습니다.');
    }
  }, [coupons, selected, onSelect, showToast]);

  return (
    <div>
      <h2 className="mb-3 text-base font-bold">쿠폰</h2>
      <div className="rounded-lg border border-line bg-surface px-5 py-4">
        {isPending && <Spinner size="sm" />}

        {!isPending && isError && (
          <div className="flex items-center justify-between gap-4">
            <p className="text-sm text-content-muted">쿠폰 정보를 불러오지 못했습니다.</p>
            <Button variant="secondary" size="sm" onClick={() => refetch()}>
              다시 시도
            </Button>
          </div>
        )}

        {!isPending && !isError && selected && (
          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="text-sm font-bold text-content">{selected.couponName}</p>
              <p className="text-xs text-content-muted">{formatCouponDiscount(selected)}</p>
            </div>
            <div className="flex shrink-0 items-center gap-3">
              <span className="text-sm font-bold text-content">
                -{formatPrice(selected.expectedDiscount)}
              </span>
              <Button variant="secondary" size="sm" onClick={() => setIsModalOpen(true)}>
                변경
              </Button>
              <Button variant="ghost" size="sm" onClick={() => onSelect(null)}>
                해제
              </Button>
            </div>
          </div>
        )}

        {!isPending && !isError && !selected && (
          <div className="flex items-center gap-3">
            {coupons && coupons.length > 0 ? (
              <>
                <Button variant="secondary" onClick={() => setIsModalOpen(true)}>
                  쿠폰 선택
                </Button>
                <span className="text-sm text-content-muted">
                  사용 가능한 쿠폰 {coupons.length}장
                </span>
              </>
            ) : (
              <span className="text-sm text-content-muted">적용 가능한 쿠폰이 없습니다.</span>
            )}
          </div>
        )}
      </div>

      <CouponSelectModal
        open={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        coupons={coupons ?? []}
        selectedId={selected?.memberCouponId}
        onApply={onSelect}
      />
    </div>
  );
}
