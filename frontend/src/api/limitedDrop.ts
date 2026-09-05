import { client, unwrap } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type {
  LimitedDropDetail,
  LimitedDropListResponse,
  LimitedDropStatus,
  LimitedPurchaseRequest,
  LimitedPurchaseResponse,
} from '@/types/limitedDrop';

export const getLimitedDrops = (status?: LimitedDropStatus) =>
  unwrap(
    client.get<ApiResponse<LimitedDropListResponse>>('/limited-drops', {
      params: status ? { status } : undefined,
    }),
  );

export const getLimitedDrop = (id: number) =>
  unwrap(client.get<ApiResponse<LimitedDropDetail>>(`/limited-drops/${id}`));

export const purchaseLimitedDrop = (id: number, payload: LimitedPurchaseRequest) =>
  unwrap(
    client.post<ApiResponse<LimitedPurchaseResponse>>(`/limited-drops/${id}/purchase`, payload),
  );
