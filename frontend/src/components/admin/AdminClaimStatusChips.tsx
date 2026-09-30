import type {
  AdminOrderClaimStatusCounts,
  OrderClaimStatus,
  OrderClaimType,
} from '@/types/adminOrder';
import {
  CLAIM_STATUSES_BY_TYPE,
  getClaimStatusLabel,
  pickClaimCount,
} from '@/utils/adminOrderActions';

export type ClaimStatusFilter = OrderClaimStatus | 'ALL';

interface AdminClaimStatusChipsProps {
  type: OrderClaimType;
  value: ClaimStatusFilter;
  /** 건수 로딩 중이거나 실패하면 없다. 라벨만 보인다. */
  counts?: AdminOrderClaimStatusCounts;
  onChange: (value: ClaimStatusFilter) => void;
}

export function AdminClaimStatusChips({
  type,
  value,
  counts,
  onChange,
}: AdminClaimStatusChipsProps) {
  const chips = [
    ...CLAIM_STATUSES_BY_TYPE[type].map((status) => ({
      key: status,
      label: getClaimStatusLabel(type, status),
      count: counts && pickClaimCount(counts, status),
    })),
    { key: 'ALL' as const, label: '전체', count: counts?.total },
  ];

  return (
    <div
      role="group"
      aria-label="클레임 상태"
      className="-mx-1 mb-4 flex gap-1 overflow-x-auto p-1"
    >
      {chips.map((chip) => {
        const isSelected = chip.key === value;
        return (
          <button
            key={chip.key}
            type="button"
            aria-pressed={isSelected}
            onClick={() => onChange(chip.key)}
            className={`h-9 shrink-0 rounded-full border px-4 text-sm whitespace-nowrap ${
              isSelected
                ? 'border-content bg-content text-surface'
                : 'border-line-strong bg-surface text-content-muted hover:bg-surface-muted hover:text-content'
            }`}
          >
            {chip.count === undefined ? chip.label : `${chip.label} ${chip.count}`}
          </button>
        );
      })}
    </div>
  );
}
