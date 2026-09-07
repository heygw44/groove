import { useSearchParams } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { Skeleton } from '@/components/common/Skeleton';
import { CouponCard } from '@/components/coupon/CouponCard';
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

function CouponCardSkeleton() {
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

export default function CouponBoxPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const status = parseMemberCouponStatus(searchParams);

  const { data, isPending, isError, refetch } = useMyCoupons(status);

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
          <EmptyState
            title="쿠폰을 불러오지 못했습니다"
            description="잠시 후 다시 시도해주세요."
            action={
              <Button variant="secondary" onClick={() => refetch()}>
                다시 시도
              </Button>
            }
          />
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
