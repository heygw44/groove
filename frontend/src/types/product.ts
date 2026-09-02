export type ProductStatus = 'ON_SALE' | 'SOLD_OUT' | 'HIDDEN';

export type ProductSort = 'latest' | 'priceAsc' | 'priceDesc' | 'rating' | 'popular';

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
}

export interface ProductListParams {
  keyword?: string;
  artistId?: number;
  genreId?: number;
  labelId?: number;
  minPrice?: number;
  maxPrice?: number;
  sort?: ProductSort;
  page?: number;
  size?: number;
}
