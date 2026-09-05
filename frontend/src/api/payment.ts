import { client, unwrap } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type { PaymentConfirmRequest, PaymentConfirmResponse } from '@/types/payment';

export const confirmPayment = (payload: PaymentConfirmRequest) =>
  unwrap(client.post<ApiResponse<PaymentConfirmResponse>>('/payments/confirm', payload));
