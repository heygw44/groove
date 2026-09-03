import { useEffect, useState } from 'react';

import { Button } from '@/components/common/Button';
import { Input } from '@/components/common/Input';
import { Select } from '@/components/common/Select';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import type { OrderStatus } from '@/types/order';
import type { AdminOrderFilters } from '@/utils/adminOrderFilters';
import { ORDER_STATUSES, ORDER_STATUS_LABEL } from '@/utils/orderStatus';

interface AdminOrderFilterBarProps {
  filters: AdminOrderFilters;
  onChange: (next: Partial<AdminOrderFilters>, options?: { replace?: boolean }) => void;
}

const hasActiveFilter = (filters: AdminOrderFilters): boolean =>
  filters.status !== undefined ||
  filters.keyword !== '' ||
  filters.from !== undefined ||
  filters.to !== undefined;

export function AdminOrderFilterBar({ filters, onChange }: AdminOrderFilterBarProps) {
  const [keyword, setKeyword] = useState(filters.keyword);
  const debouncedKeyword = useDebouncedValue(keyword, 300);

  /*
   * URL 이 외부(뒤로가기, 초기화 등)에서 바뀌면 입력창도 따라가야 한다. 렌더 중
   * 이전 값과 비교해 setState 하는 건 effect 없이도 같은 프레임에 반영되고
   * react-hooks/set-state-in-effect 경고도 피한다 (ProductFilterPanel 과 동일 패턴).
   */
  const [prevUrlKeyword, setPrevUrlKeyword] = useState(filters.keyword);
  if (filters.keyword !== prevUrlKeyword) {
    setPrevUrlKeyword(filters.keyword);
    setKeyword(filters.keyword);
  }

  /* 디바운스 반영만 replace - 타이핑 한 글자마다 히스토리 스택이 쌓이면 뒤로가기가 무의미해진다. */
  useEffect(() => {
    const trimmed = debouncedKeyword.trim();
    if (trimmed !== filters.keyword) {
      onChange({ keyword: trimmed }, { replace: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedKeyword]);

  const handleReset = () => {
    setKeyword('');
    onChange({ status: undefined, keyword: '', from: undefined, to: undefined });
  };

  return (
    <div className="flex flex-wrap items-center gap-2">
      <Select
        aria-label="상태 필터"
        value={filters.status ?? ''}
        onChange={(event) =>
          onChange({ status: (event.target.value || undefined) as OrderStatus | undefined })
        }
        className="w-32"
      >
        <option value="">전체</option>
        {ORDER_STATUSES.map((status) => (
          <option key={status} value={status}>
            {ORDER_STATUS_LABEL[status]}
          </option>
        ))}
      </Select>

      <Input
        type="date"
        aria-label="시작일"
        value={filters.from ?? ''}
        onChange={(event) => onChange({ from: event.target.value || undefined })}
        className="w-36"
      />
      <span aria-hidden className="text-content-subtle">
        ~
      </span>
      <Input
        type="date"
        aria-label="종료일"
        value={filters.to ?? ''}
        onChange={(event) => onChange({ to: event.target.value || undefined })}
        className="w-36"
      />

      <Input
        placeholder="주문번호 또는 회원 이메일"
        value={keyword}
        onChange={(event) => setKeyword(event.target.value)}
        className="w-56"
      />

      {hasActiveFilter(filters) && (
        <Button variant="ghost" size="sm" onClick={handleReset}>
          초기화
        </Button>
      )}
    </div>
  );
}
