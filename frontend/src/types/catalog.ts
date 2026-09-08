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
  /** 비로그인이면 null. */
  watched?: boolean;
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
  /** 비로그인이면 null. */
  watched?: boolean;
}

export interface CatalogLookupParams {
  barcode?: string;
  catalogNo?: string;
  query?: string;
  page?: number;
}

export interface CatalogLookupItem {
  discogsReleaseId: number;
  title?: string;
  artist?: string;
  year?: number;
  country?: string;
  catalogNo?: string;
  label?: string;
  /** 표시 전용이다. 저장하거나 업로드하지 않는다(Discogs 약관상 Restricted Data). */
  thumbUrl?: string;
  alreadyImported: boolean;
}

export interface CatalogReleaseDetail {
  discogsReleaseId: number;
  discogsMasterId?: number;
  title?: string;
  artistName?: string;
  labelName?: string;
  country?: string;
  pressingYear?: number;
  catalogNo?: string;
  barcode?: string;
  editionType: EditionType;
  genreNames: string[];
  imageUrl?: string;
  description?: string;
}

export interface CatalogImportRequest {
  discogsReleaseId: number;
  defaultPrice: number;
}

export interface CatalogImportResponse {
  productId: number;
  albumId: number;
}

/** Spring Batch BatchStatus 를 그대로 노출한다. 문서에 적힌 5개 외 값도 올 수 있다. */
export type CatalogImportJobStatus =
  | 'STARTING'
  | 'STARTED'
  | 'STOPPING'
  | 'STOPPED'
  | 'COMPLETED'
  | 'FAILED'
  | 'ABANDONED'
  | 'UNKNOWN';

export interface CatalogImportJobRequest {
  discogsMasterId: number;
  defaultPrice: number;
}

export interface CatalogImportJobStartResponse {
  jobExecutionId: number;
}

export interface CatalogImportJob {
  jobExecutionId: number;
  discogsMasterId?: number;
  status: CatalogImportJobStatus;
  readCount: number;
  writeCount: number;
  skipCount: number;
  filterCount: number;
  startedAt?: string;
  endedAt?: string;
  exitMessage?: string;
}

export interface CatalogImportJobListParams {
  page?: number;
  size?: number;
}
