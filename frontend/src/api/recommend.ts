import { client, unwrap } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type { ProductSummary } from '@/types/product';
import type {
  HomeRecommendResponse,
  RecommendItem,
  TasteProfile,
  TasteProfileUpdateRequest,
} from '@/types/recommend';

export const getTasteProfile = () =>
  unwrap(client.get<ApiResponse<TasteProfile>>('/members/me/taste-profile'));

export const updateTasteProfile = (payload: TasteProfileUpdateRequest) =>
  unwrap(client.put<ApiResponse<TasteProfile>>('/members/me/taste-profile', payload));

export const getHomeRecommendations = (size?: number) =>
  unwrap(
    client.get<ApiResponse<HomeRecommendResponse>>('/recommend/home', {
      params: size ? { size } : undefined,
    }),
  );

export const getRelatedProducts = (productId: number, size?: number) =>
  unwrap(
    client.get<ApiResponse<RecommendItem[]>>(`/products/${productId}/related`, {
      params: size ? { size } : undefined,
    }),
  );

export const getRecentViews = () =>
  unwrap(client.get<ApiResponse<ProductSummary[]>>('/members/me/recent-views'));
