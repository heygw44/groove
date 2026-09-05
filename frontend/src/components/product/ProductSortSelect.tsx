import { Select } from '@/components/common/Select';
import { PRODUCT_SORT_OPTIONS } from '@/constants/product';
import type { ProductSort } from '@/types/product';

interface ProductSortSelectProps {
  value: ProductSort;
  onChange: (sort: ProductSort) => void;
}

export function ProductSortSelect({ value, onChange }: ProductSortSelectProps) {
  return (
    <Select
      aria-label="정렬"
      value={value}
      onChange={(event) => onChange(event.target.value as ProductSort)}
      className="w-auto"
    >
      {PRODUCT_SORT_OPTIONS.map((option) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </Select>
  );
}
