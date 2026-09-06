import { client, unwrap } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type { AlbumDetail } from '@/types/catalog';

export const getAlbum = (id: number) =>
  unwrap(client.get<ApiResponse<AlbumDetail>>(`/albums/${id}`));
