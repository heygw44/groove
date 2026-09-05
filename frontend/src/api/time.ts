import { client, unwrap } from '@/api/client';
import type { ApiResponse } from '@/types/api';

export const getServerTime = () =>
  unwrap(client.get<ApiResponse<{ serverTime: string }>>('/time'));
