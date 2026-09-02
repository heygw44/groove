import { PRODUCT_PAGE_SIZE, PRODUCT_SORT_OPTIONS } from '@/constants/product';
import type { ProductListParams, ProductSort } from '@/types/product';

export interface ProductListFilters {
  keyword?: string;
  artistId?: number;
  genreId?: number;
  labelId?: number;
  minPrice?: number;
  maxPrice?: number;
  sort: ProductSort;
  page: number;
}

const SORT_VALUES = PRODUCT_SORT_OPTIONS.map((option) => option.value);

const DEFAULT_SORT: ProductSort = 'latest';
const DEFAULT_PAGE = 0;

/** 자연수(0 포함) 문자열만 통과시킨다 - 음수·NaN·소수 등은 undefined 로 무시. */
const parseNonNegativeInt = (value: string | null): number | undefined => {
  if (value === null || !/^\d+$/.test(value)) {
    return undefined;
  }
  return Number(value);
};

const parseSort = (value: string | null): ProductSort =>
  SORT_VALUES.includes(value as ProductSort) ? (value as ProductSort) : DEFAULT_SORT;

export const parseProductFilters = (searchParams: URLSearchParams): ProductListFilters => ({
  keyword: searchParams.get('keyword')?.trim() || undefined,
  artistId: parseNonNegativeInt(searchParams.get('artistId')),
  genreId: parseNonNegativeInt(searchParams.get('genreId')),
  labelId: parseNonNegativeInt(searchParams.get('labelId')),
  minPrice: parseNonNegativeInt(searchParams.get('minPrice')),
  maxPrice: parseNonNegativeInt(searchParams.get('maxPrice')),
  sort: parseSort(searchParams.get('sort')),
  page: parseNonNegativeInt(searchParams.get('page')) ?? DEFAULT_PAGE,
});

/** 기본값·빈 값은 URL 을 지저분하게 만들 뿐이라 생략한다. size 는 URL 에 안 넣는다. */
export const serializeProductFilters = (filters: ProductListFilters): URLSearchParams => {
  const params = new URLSearchParams();

  if (filters.keyword) {
    params.set('keyword', filters.keyword);
  }
  if (filters.artistId !== undefined) {
    params.set('artistId', String(filters.artistId));
  }
  if (filters.genreId !== undefined) {
    params.set('genreId', String(filters.genreId));
  }
  if (filters.labelId !== undefined) {
    params.set('labelId', String(filters.labelId));
  }
  if (filters.minPrice !== undefined) {
    params.set('minPrice', String(filters.minPrice));
  }
  if (filters.maxPrice !== undefined) {
    params.set('maxPrice', String(filters.maxPrice));
  }
  if (filters.sort !== DEFAULT_SORT) {
    params.set('sort', filters.sort);
  }
  if (filters.page !== DEFAULT_PAGE) {
    params.set('page', String(filters.page));
  }

  return params;
};

export const toProductListParams = (filters: ProductListFilters): ProductListParams => ({
  keyword: filters.keyword,
  artistId: filters.artistId,
  genreId: filters.genreId,
  labelId: filters.labelId,
  minPrice: filters.minPrice,
  maxPrice: filters.maxPrice,
  sort: filters.sort,
  page: filters.page,
  size: PRODUCT_PAGE_SIZE,
});
