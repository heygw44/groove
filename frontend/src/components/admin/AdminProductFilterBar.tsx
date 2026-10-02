import { useEffect, useState } from 'react';

import { Button } from '@/components/common/Button';
import { Input } from '@/components/common/Input';
import { Select } from '@/components/common/Select';
import { KEYWORD_INPUT_MAX_LENGTH } from '@/constants/search';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import type { ProductStatus } from '@/types/product';

export interface AdminProductFilters {
  keyword: string;
  status?: ProductStatus;
}

interface AdminProductFilterBarProps {
  filters: AdminProductFilters;
  onChange: (next: Partial<AdminProductFilters>, options?: { replace?: boolean }) => void;
}

const STATUS_OPTIONS: { value: ProductStatus; label: string }[] = [
  { value: 'ON_SALE', label: '판매중' },
  { value: 'SOLD_OUT', label: '품절' },
  { value: 'HIDDEN', label: '숨김' },
];

const STATUS_VALUES = new Set<string>(STATUS_OPTIONS.map((option) => option.value));

const isProductStatus = (value: string): value is ProductStatus => STATUS_VALUES.has(value);

const hasActiveFilter = (filters: AdminProductFilters): boolean =>
  filters.keyword !== '' || filters.status !== undefined;

export function AdminProductFilterBar({ filters, onChange }: AdminProductFilterBarProps) {
  const [keyword, setKeyword] = useState(filters.keyword);
  const debouncedKeyword = useDebouncedValue(keyword, 300);

  // URL 이 외부(뒤로가기, 초기화 등)에서 바뀌면 입력창도 따라간다. 렌더 중 비교로 effect 없이 반영한다.
  const [prevUrlKeyword, setPrevUrlKeyword] = useState(filters.keyword);
  if (filters.keyword !== prevUrlKeyword) {
    setPrevUrlKeyword(filters.keyword);
    setKeyword(filters.keyword);
  }

  // 타이핑 한 글자마다 히스토리가 쌓이지 않도록 디바운스 반영만 replace 로 한다.
  useEffect(() => {
    const trimmed = debouncedKeyword.trim();
    if (trimmed !== filters.keyword) {
      onChange({ keyword: trimmed }, { replace: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedKeyword]);

  const handleReset = () => {
    setKeyword('');
    onChange({ keyword: '', status: undefined });
  };

  return (
    <div className="flex flex-wrap items-center gap-2">
      <Select
        aria-label="상태 필터"
        value={filters.status ?? ''}
        onChange={(event) => {
          const value = event.target.value;
          onChange({ status: isProductStatus(value) ? value : undefined });
        }}
        className="w-32"
      >
        <option value="">전체</option>
        {STATUS_OPTIONS.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </Select>

      <Input
        placeholder="제목 또는 아티스트"
        value={keyword}
        maxLength={KEYWORD_INPUT_MAX_LENGTH}
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
