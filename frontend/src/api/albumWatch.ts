import { client, unwrap } from '@/api/client';
import type { AlbumWatch, AlbumWatchListParams } from '@/types/albumWatch';
import type { ApiResponse, PageResponse } from '@/types/api';

export const getAlbumWatches = (params: AlbumWatchListParams) =>
  unwrap(
    client.get<ApiResponse<PageResponse<AlbumWatch>>>('/members/me/album-watches', { params }),
  );

export const addAlbumWatch = (albumId: number) =>
  unwrap(client.post<ApiResponse<AlbumWatch>>(`/members/me/album-watches/${albumId}`));

export const removeAlbumWatch = async (albumId: number) => {
  await client.delete<ApiResponse<void>>(`/members/me/album-watches/${albumId}`);
};
