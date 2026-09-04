import { useSearchParams } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { Spinner } from '@/components/common/Spinner';
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

export default function CouponBoxPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const status = parseMemberCouponStatus(searchParams);

  const { data, isPending, isError, refetch } = useMyCoupons(status);

  const updateStatus = (nextStatus: MemberCouponStatus) => {
    setSearchParams(serializeMemberCouponStatus(nextStatus));
  };

  return (
    <div>
      <h1 className="text-xl font-bold">쿠폰함</h1>

      <div className="mt-5">
        <SectionCard title="쿠폰 등록" description="쿠폰 코드를 입력해 발급받으세요.">
          <CouponIssueForm />
        </SectionCard>
      </div>

      <div className="mt-5">
        <CouponStatusTabs value={status} onChange={updateStatus} />
      </div>

      <div className="mt-3">
        {isPending && (
          <div className="flex min-h-48 items-center justify-center">
            <Spinner />
          </div>
        )}

        {!isPending && isError && (
          <EmptyState
            title="쿠폰을 불러오지 못했습니다."
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
