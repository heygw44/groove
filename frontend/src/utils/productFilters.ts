import { PRODUCT_PAGE_SIZE, PRODUCT_SORT_OPTIONS } from '@/constants/product';
import type { ProductListParams, ProductSort } from '@/types/product';

export interface ProductListFilters {
  keyword?: string;
  artistId?: number;
  genreIds?: number[];
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

/** 자연수 문자열만 통과시켜 숫자로 바꾸고, 중복 제거 후 오름차순 정렬한다(안정적인 쿼리 키를 위해). */
const parseIdList = (values: string[]): number[] | undefined => {
  const ids = values.filter((value) => /^\d+$/.test(value)).map(Number);
  if (ids.length === 0) {
    return undefined;
  }
  return Array.from(new Set(ids)).sort((a, b) => a - b);
};

export const parseProductFilters = (searchParams: URLSearchParams): ProductListFilters => ({
  keyword: searchParams.get('keyword')?.trim() || undefined,
  artistId: parseNonNegativeInt(searchParams.get('artistId')),
  genreIds: parseIdList(searchParams.getAll('genreIds')),
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
  if (filters.genreIds && filters.genreIds.length > 0) {
    filters.genreIds.forEach((id) => params.append('genreIds', String(id)));
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
  genreIds: filters.genreIds,
  labelId: filters.labelId,
  minPrice: filters.minPrice,
  maxPrice: filters.maxPrice,
  sort: filters.sort,
  page: filters.page,
  size: PRODUCT_PAGE_SIZE,
});

/**
 * 사용자가 직접 건 필터의 개수. 정렬과 페이지는 결과를 좁히지 않으므로 세지 않고,
 * 가격은 최소·최대를 합쳐 하나로 본다(칩·초기화 버튼이 이 기준을 공유한다).
 */
export const countActiveFilters = (filters: ProductListFilters): number => {
  let count = 0;

  if (filters.keyword) {
    count += 1;
  }
  if (filters.artistId !== undefined) {
    count += 1;
  }
  if (filters.labelId !== undefined) {
    count += 1;
  }
  if (filters.minPrice !== undefined || filters.maxPrice !== undefined) {
    count += 1;
  }
  count += filters.genreIds?.length ?? 0;

  return count;
};

export const hasActiveFilters = (filters: ProductListFilters): boolean =>
  countActiveFilters(filters) > 0;
