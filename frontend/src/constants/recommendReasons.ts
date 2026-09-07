import type { RecommendReason } from '@/types/recommend';

export const RECOMMEND_REASON_LABELS: Record<RecommendReason, string> = {
  TASTE_GENRE: '취향 장르',
  TASTE_ARTIST: '취향 아티스트',
  TASTE_DECADE: '취향 연대',
  SAME_ARTIST: '같은 아티스트',
  SAME_GENRE: '같은 장르',
  SAME_LABEL: '같은 레이블',
  SAME_DECADE: '같은 연대',
  BOUGHT_TOGETHER: '함께 구매',
  RECENTLY_VIEWED_SIMILAR: '최근 본 상품과 비슷',
  POPULAR: '인기 상품',
};

export const MAX_REASON_BADGES = 2;

export const HOME_RECOMMEND_SIZE = 8;

export const RELATED_PRODUCT_SIZE = 4;

/** 닉네임을 앞에 붙여 `{닉네임}님을 위한 추천` 형태로 쓴다. */
export const HOME_RECOMMEND_SECTION_TITLE_SUFFIX = '님을 위한 추천';

export const RELATED_PRODUCTS_SECTION_TITLE = '함께 보면 좋은 앨범';
