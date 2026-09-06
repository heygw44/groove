import type { Artist, Genre, ProductSummary } from '@/types/product';

export type Decade = 'D1960' | 'D1970' | 'D1980' | 'D1990' | 'D2000' | 'D2010' | 'D2020';

export type RecommendReason =
  | 'TASTE_GENRE'
  | 'TASTE_ARTIST'
  | 'TASTE_DECADE'
  | 'SAME_ARTIST'
  | 'SAME_GENRE'
  | 'SAME_LABEL'
  | 'SAME_DECADE'
  | 'BOUGHT_TOGETHER'
  | 'RECENTLY_VIEWED_SIMILAR';

export interface TasteProfile {
  genres: Genre[];
  artists: Artist[];
  decades: Decade[];
  updatedAt: string;
}

export interface TasteProfileUpdateRequest {
  genreIds: number[];
  artistIds: number[];
  decades: Decade[];
}

export interface RecommendItem {
  product: ProductSummary;
  reasons: RecommendReason[];
}

export interface HomeRecommendResponse {
  profileRequired: boolean;
  items: RecommendItem[];
}

export interface TasteMatch {
  matched: boolean;
  reasons: RecommendReason[];
}
