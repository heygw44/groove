import type { MemberCouponStatus } from '@/types/coupon';

interface CouponStatusTabsProps {
  value: MemberCouponStatus;
  onChange: (status: MemberCouponStatus) => void;
}

const TAB_STATUSES: MemberCouponStatus[] = ['usable', 'used', 'expired'];

const TAB_LABEL: Record<MemberCouponStatus, string> = {
  usable: '사용 가능',
  used: '사용 완료',
  expired: '만료',
};

export function CouponStatusTabs({ value, onChange }: CouponStatusTabsProps) {
  return (
    <div role="tablist" aria-label="쿠폰 상태" className="flex gap-1 overflow-x-auto pb-1">
      {TAB_STATUSES.map((status) => {
        const isSelected = status === value;
        return (
          <button
            key={status}
            type="button"
            role="tab"
            aria-selected={isSelected}
            onClick={() => onChange(status)}
            className={`h-9 shrink-0 rounded-full px-4 text-sm whitespace-nowrap ${
              isSelected
                ? 'bg-content text-surface'
                : 'text-content-muted hover:bg-surface-muted hover:text-content'
            }`}
          >
            {TAB_LABEL[status]}
          </button>
        );
      })}
    </div>
  );
}
