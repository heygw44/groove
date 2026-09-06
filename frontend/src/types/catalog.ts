import type { ProductSummary } from '@/types/product';

export type EditionType = 'STANDARD' | 'ORIGINAL' | 'REISSUE' | 'REMASTER' | 'LIMITED' | 'PROMO';

/** 상품 상세의 프레싱 스펙. 값이 없는 필드는 non_null 정책상 키 자체가 빠진다. */
export interface PressingSummary {
  country?: string;
  pressingYear?: number;
  catalogNo?: string;
  barcode?: string;
  editionType: EditionType;
  discogsReleaseId?: number;
}

export interface ProductAlbumSummary {
  id: number;
  title: string;
  originalReleaseYear?: number;
  /** HIDDEN 을 제외한 같은 앨범의 프레싱 수(자기 자신 포함). */
  pressingCount: number;
}

export interface AlbumArtistSummary {
  id: number;
  name: string;
}

export interface AlbumDetail {
  id: number;
  title: string;
  artist: AlbumArtistSummary;
  originalReleaseYear?: number;
  description?: string;
  pressings: ProductSummary[];
}
