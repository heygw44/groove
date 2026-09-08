import type { ReconcileMetric, ReconcileSeverity } from '@/types/adminStats';

export const RECONCILE_METRIC_LABELS: Record<ReconcileMetric, string> = {
  DAILY_ORDER_COUNT: '일별 주문 건수',
  DAILY_SALES_AMOUNT: '일별 매출액',
  DAILY_CANCEL_COUNT: '일별 취소 건수',
  DAILY_CANCEL_AMOUNT: '일별 취소액',
  PRODUCT_SOLD_QUANTITY: '상품별 판매 수량',
  PRODUCT_SALES_AMOUNT: '상품별 매출액',
  PRODUCT_ORDER_COUNT: '상품별 주문 건수',
};

export const RECONCILE_SEVERITY_LABELS: Record<ReconcileSeverity, string> = {
  WARN: '경고',
  CRITICAL: '심각',
};

/** 금액 지표는 formatPrice, 건수 지표는 "N건" 으로 다르게 표시한다. */
export const RECONCILE_AMOUNT_METRICS = new Set<ReconcileMetric>([
  'DAILY_SALES_AMOUNT',
  'DAILY_CANCEL_AMOUNT',
  'PRODUCT_SALES_AMOUNT',
]);
