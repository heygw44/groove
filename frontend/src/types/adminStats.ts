import type { LimitedDropStatus } from '@/types/limitedDrop';

export interface AdminStatsSummary {
  todaySalesAmount: number;
  todayCancelAmount: number;
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

export interface LimitedDropAttempts {
  attemptCount: number;
  successCount: number;
  soldOutCount: number;
  alreadyPurchasedCount: number;
  notOpenCount: number;
  closedCount: number;
  /** N:1 의 N (배수). sellRate 와 달리 퍼센트가 아니다. */
  competitionRate: number;
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
  /** 기능 이전 마감 드롭·시도 없는 드롭·Redis 조회 실패 시 생략된다. */
  attempts?: LimitedDropAttempts;
}

export interface DailySalesStats {
  items: DailySales[];
  aggregatedAt: string | null;
}

export interface PopularProductStats {
  items: PopularProduct[];
  aggregatedAt: string | null;
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

export type ReconcileMetric =
  | 'DAILY_ORDER_COUNT'
  | 'DAILY_SALES_AMOUNT'
  | 'DAILY_CANCEL_COUNT'
  | 'DAILY_CANCEL_AMOUNT'
  | 'PRODUCT_SOLD_QUANTITY'
  | 'PRODUCT_SALES_AMOUNT'
  | 'PRODUCT_ORDER_COUNT';

export type ReconcileSeverity = 'WARN' | 'CRITICAL';

export interface ReconcileLog {
  id: number;
  saleDate: string;
  metric: ReconcileMetric;
  severity: ReconcileSeverity;
  /** 원본을 다시 계산한 값. */
  expectedValue: number;
  /** 집계 테이블에 있던(어긋난) 값. */
  actualValue: number;
  repaired: boolean;
  createdAt: string;
}

export interface ReconcileLogListParams {
  repaired?: boolean;
  page: number;
  size?: number;
}

export interface LimitedDropStatsListParams {
  page: number;
  size?: number;
}
