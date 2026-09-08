import { useSearchParams } from 'react-router-dom';

import { EmptyState } from '@/components/common/EmptyState';
import { QueryErrorState } from '@/components/common/QueryErrorState';
import { CouponCard, CouponCardSkeleton } from '@/components/coupon/CouponCard';
import { CouponIssueForm } from '@/components/coupon/CouponIssueForm';
import { CouponStatusTabs } from '@/components/coupon/CouponStatusTabs';
import { SectionCard } from '@/components/mypage/SectionCard';
import { useMyCoupons } from '@/hooks/queries/useMyCoupons';
import type { MemberCouponStatus } from '@/types/coupon';
import { parseMemberCouponStatus, serializeMemberCouponStatus } from '@/utils/coupon';

const EMPTY_MESSAGE: Record<MemberCouponStatus, string> = {
  usable: '사용 가능한 쿠폰이 없습니다.',
  used: '사용한 쿠폰이 없습니다.',
  expired: '만료된 쿠폰이 없습니다.',
};

const SKELETON_COUNT = 4;

export default function CouponBoxPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const status = parseMemberCouponStatus(searchParams);

  const { data, isPending, isError, error, refetch } = useMyCoupons(status);

  const updateStatus = (nextStatus: MemberCouponStatus) => {
    setSearchParams(serializeMemberCouponStatus(nextStatus));
  };

  return (
    <div>
      <h2 className="text-xl font-bold">쿠폰함</h2>

      <div className="mt-5">
        <SectionCard title="쿠폰 등록" description="쿠폰 코드를 입력하면 발급받을 수 있습니다.">
          <CouponIssueForm />
        </SectionCard>
      </div>

      <div className="mt-5">
        <CouponStatusTabs value={status} onChange={updateStatus} />
      </div>

      <div className="mt-3">
        {isPending && (
          <div className="grid gap-3 sm:grid-cols-2">
            {Array.from({ length: SKELETON_COUNT }, (_, index) => (
              <CouponCardSkeleton key={index} />
            ))}
          </div>
        )}

        {!isPending && isError && (
          <QueryErrorState error={error} onRetry={refetch} title="쿠폰을 불러오지 못했습니다." />
        )}

        {!isPending && !isError && data && data.length === 0 && (
          <EmptyState title={EMPTY_MESSAGE[status]} />
        )}

        {!isPending && !isError && data && data.length > 0 && (
          <div className="grid gap-3 sm:grid-cols-2">
            {data.map((coupon) => (
              <CouponCard key={coupon.memberCouponId} coupon={coupon} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
