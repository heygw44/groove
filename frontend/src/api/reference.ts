import { client, unwrap } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type { Artist, Genre, Label } from '@/types/product';

export const getGenres = () => unwrap(client.get<ApiResponse<Genre[]>>('/genres'));

export const getLabels = () => unwrap(client.get<ApiResponse<Label[]>>('/labels'));

/** keyword 가 비어 있으면 서버가 앞 20건을 내려준다. */
export const searchArtists = (keyword?: string) =>
  unwrap(client.get<ApiResponse<Artist[]>>('/artists', { params: { keyword } }));

export const getArtist = (id: number) => unwrap(client.get<ApiResponse<Artist>>(`/artists/${id}`));
