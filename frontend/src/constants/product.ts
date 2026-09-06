import type { EditionType } from '@/types/catalog';
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

export const EDITION_TYPE_LABELS: Record<EditionType, string> = {
  STANDARD: '일반반',
  ORIGINAL: '오리지널',
  REISSUE: '재발매',
  REMASTER: '리마스터',
  LIMITED: '한정반',
  PROMO: '프로모',
};

/**
 * 국가 값은 Discogs 표기를 그대로 저장한다("Europe" 같은 비국가 값도 있어 ISO 코드로 담지 않는다).
 * 목록 필터는 프레싱이 많은 상위 국가만 고정 목록으로 노출한다.
 */
export const PRESSING_COUNTRIES = [
  'US',
  'UK',
  'Europe',
  'Japan',
  'Germany',
  'France',
  'Italy',
  'Netherlands',
  'Canada',
  'South Korea',
] as const;

export const PRESSING_COUNTRY_LABELS: Record<(typeof PRESSING_COUNTRIES)[number], string> = {
  US: '미국',
  UK: '영국',
  Europe: '유럽',
  Japan: '일본',
  Germany: '독일',
  France: '프랑스',
  Italy: '이탈리아',
  Netherlands: '네덜란드',
  Canada: '캐나다',
  'South Korea': '한국',
};

/** 고정 목록 밖의 국가(Discogs 표기)는 한글 라벨이 없으므로 원문 그대로 보여준다. */
export function getCountryLabel(country: string): string {
  return (PRESSING_COUNTRY_LABELS as Record<string, string>)[country] ?? country;
}
