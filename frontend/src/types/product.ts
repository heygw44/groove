import type { EditionType, PressingSummary, ProductAlbumSummary } from '@/types/catalog';
import type { LimitedDropStatus } from '@/types/limitedDrop';

export type ProductStatus = 'ON_SALE' | 'SOLD_OUT' | 'HIDDEN';

export type ProductSort = 'latest' | 'priceAsc' | 'priceDesc' | 'rating' | 'popular';

export interface ProductLimitedDropSummary {
  id: number;
  status: LimitedDropStatus;
  openAt: string;
  closeAt: string;
  remainingQuantity: number;
  perMemberLimit: number;
}

export interface Genre {
  id: number;
  name: string;
}

export interface Label {
  id: number;
  name: string;
  country: string;
}

export interface Artist {
  id: number;
  name: string;
  nameEn?: string;
}

export interface ProductImage {
  url: string;
  sortOrder: number;
}

export interface ProductSummary {
  id: number;
  title: string;
  artistName: string;
  labelName?: string;
  price: number;
  colorVariant?: string;
  pressingInfo?: string;
  status: ProductStatus;
  thumbnailUrl?: string;
  averageRating?: number;
  reviewCount?: number;
  wishlisted?: boolean;
  country?: string;
  pressingYear?: number;
  editionType: EditionType;
}

export interface ProductDetail {
  id: number;
  title: string;
  album: ProductAlbumSummary;
  artist: Artist;
  label?: Label;
  genres: Genre[];
  images: ProductImage[];
  price: number;
  status: ProductStatus;
  stockQuantity: number;
  releaseDate?: string;
  pressingInfo?: string;
  colorVariant?: string;
  pressing: PressingSummary;
  description?: string;
  averageRating?: number;
  reviewCount?: number;
  wishlisted?: boolean;
  /** 위시에 없으면 이 값도 없다. */
  alertEnabled?: boolean;
  limitedDrop?: ProductLimitedDropSummary;
}

export interface ProductListParams {
  keyword?: string;
  artistId?: number;
  genreIds?: number[];
  labelId?: number;
  albumId?: number;
  country?: string;
  pressingYearFrom?: number;
  pressingYearTo?: number;
  editionType?: EditionType;
  minPrice?: number;
  maxPrice?: number;
  sort?: ProductSort;
  page?: number;
  size?: number;
}

export interface AdminProductSummary {
  id: number;
  title: string;
  artistName: string;
  price: number;
  status: ProductStatus;
  thumbnailUrl?: string;
  stockQuantity?: number;
  createdAt: string;
}

/** 공개 상세와 달리 pressingCount 는 내려오지 않는다. */
export interface AdminProductAlbum {
  id: number;
  title: string;
  originalReleaseYear?: number;
}

export interface AdminAlbumSummary {
  id: number;
  title: string;
  artistName: string;
  originalReleaseYear?: number;
}

export interface AdminAlbumListParams {
  keyword?: string;
  page?: number;
  size?: number;
}

export interface AdminProductResponse {
  id: number;
  title: string;
  artist: Artist;
  label?: Label;
  genres: Genre[];
  images: ProductImage[];
  price: number;
  status: ProductStatus;
  stockQuantity: number;
  releaseDate?: string;
  pressingInfo?: string;
  colorVariant?: string;
  album: AdminProductAlbum;
  country?: string;
  pressingYear?: number;
  catalogNo?: string;
  barcode?: string;
  editionType: EditionType;
  description?: string;
  averageRating?: number;
  reviewCount?: number;
  createdAt: string;
  updatedAt: string;
}

/** ProductForm 이 폼 초기값을 채울 때 필요한 필드만 뽑은 형태. ProductDetail/AdminProductResponse 모두 만족한다. */
export type ProductFormSource = Pick<
  AdminProductResponse,
  | 'id'
  | 'title'
  | 'artist'
  | 'label'
  | 'genres'
  | 'releaseDate'
  | 'pressingInfo'
  | 'colorVariant'
  | 'price'
  | 'description'
  | 'images'
  | 'album'
  | 'country'
  | 'pressingYear'
  | 'catalogNo'
  | 'barcode'
  | 'editionType'
>;

export interface AdminProductListParams {
  status?: ProductStatus;
  page?: number;
  size?: number;
  sort?: string;
}

export interface AdminNewAlbumRequest {
  title: string;
  originalReleaseYear?: number;
}

export interface AdminProductCreateRequest {
  title: string;
  artistId: number;
  labelId?: number;
  genreIds?: number[];
  releaseDate?: string;
  pressingInfo?: string;
  colorVariant?: string;
  price: number;
  description?: string;
  imageUrls?: string[];
  initialStock: number;
  /** albumId 와 newAlbum 중 정확히 하나만 보낸다. 둘 다 없거나 둘 다 있으면 서버가 400 을 준다. */
  albumId?: number;
  newAlbum?: AdminNewAlbumRequest;
  country?: string;
  pressingYear?: number;
  catalogNo?: string;
  barcode?: string;
  editionType?: EditionType;
}

/** 앨범 이동은 지원하지 않는다(서버 요청 DTO 에 albumId 가 없다). null 은 값 해제다. */
export type AdminProductUpdateRequest = Partial<
  Omit<
    AdminProductCreateRequest,
    'initialStock' | 'labelId' | 'albumId' | 'newAlbum' | 'country' | 'pressingYear' | 'catalogNo' | 'barcode'
  >
> & {
  labelId?: number | null;
  country?: string | null;
  pressingYear?: number | null;
  catalogNo?: string | null;
  barcode?: string | null;
};

export type StockChangeType = 'IN' | 'OUT' | 'ADJUST';

export interface StockAdjustRequest {
  changeType: StockChangeType;
  quantity: number;
  reason?: string;
}

export interface StockAdjustResponse {
  productId: number;
  quantity: number;
  productStatus: ProductStatus;
}
