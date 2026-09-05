import type { LimitedDropStatus } from '@/types/limitedDrop';

export interface AdminStatsSummary {
  todaySalesAmount: number;
  todayOrderCount: number;
  todayNewMemberCount: number;
  pendingOrderCount: number;
}

export interface DailySales {
  date: string;
  orderCount: number;
  salesAmount: number;
  cancelAmount: number;
}

export interface PopularProduct {
  productId: number;
  productTitle: string;
  artistName: string;
  soldQuantity: number;
  salesAmount: number;
  orderCount: number;
}

export interface LimitedDropStats {
  dropId: number;
  productTitle: string;
  status: LimitedDropStatus;
  totalQuantity: number;
  soldQuantity: number;
  /** 이미 백분율(0~100, 소수 첫째 자리)로 내려온다. */
  sellRate: number;
  openAt: string;
  closeAt: string;
  soldOutAt?: string;
  soldOutSeconds?: number;
}

export interface StatsPeriodParams {
  from?: string;
  to?: string;
}

export type PopularProductSort = 'quantity' | 'sales';

export interface PopularProductParams extends StatsPeriodParams {
  limit?: number;
  sort?: PopularProductSort;
}
