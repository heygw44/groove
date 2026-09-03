import type { ProductSort } from '@/types/product';

interface ProductSortOption {
  value: ProductSort;
  label: string;
}

export const PRODUCT_SORT_OPTIONS: ProductSortOption[] = [
  { value: 'latest', label: '최신순' },
  { value: 'priceAsc', label: '가격 낮은순' },
  { value: 'priceDesc', label: '가격 높은순' },
  { value: 'rating', label: '평점순' },
  { value: 'popular', label: '인기순' },
];

/** 2·3·4열 그리드 공배수라 마지막 행이 어중간하게 남지 않는다. */
export const PRODUCT_PAGE_SIZE = 24;
