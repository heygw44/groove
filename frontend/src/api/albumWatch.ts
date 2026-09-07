import { client, unwrap } from '@/api/client';
import type { AlbumWatch, AlbumWatchListResponse } from '@/types/albumWatch';
import type { ApiResponse } from '@/types/api';

export const getAlbumWatches = () =>
  unwrap(client.get<ApiResponse<AlbumWatchListResponse>>('/members/me/album-watches'));

export const addAlbumWatch = (albumId: number) =>
  unwrap(client.post<ApiResponse<AlbumWatch>>(`/members/me/album-watches/${albumId}`));

export const removeAlbumWatch = async (albumId: number) => {
  await client.delete<ApiResponse<void>>(`/members/me/album-watches/${albumId}`);
};
