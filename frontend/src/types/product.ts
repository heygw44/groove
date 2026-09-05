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
  nameEn: string;
}

export interface ProductImage {
  url: string;
  sortOrder: number;
}

export interface ProductSummary {
  id: number;
  title: string;
  artistName: string;
  price: number;
  status: ProductStatus;
  thumbnailUrl?: string;
  averageRating?: number;
  wishlisted?: boolean;
}

export interface ProductDetail {
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
  description?: string;
  averageRating?: number;
  reviewCount?: number;
  wishlisted?: boolean;
  limitedDrop?: ProductLimitedDropSummary;
}

export interface ProductListParams {
  keyword?: string;
  artistId?: number;
  genreIds?: number[];
  labelId?: number;
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
>;

export interface AdminProductListParams {
  status?: ProductStatus;
  page?: number;
  size?: number;
  sort?: string;
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
}

export type AdminProductUpdateRequest = Partial<
  Omit<AdminProductCreateRequest, 'initialStock' | 'labelId'>
> & {
  labelId?: number | null;
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
