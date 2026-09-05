import type { OrderListParams, OrderStatus } from '@/types/order';
import { isOrderStatus } from '@/utils/orderStatus';

export interface OrderListFilters {
  status?: OrderStatus;
  page: number;
}

const DEFAULT_PAGE = 0;
const ORDER_PAGE_SIZE = 10;

const parseStatus = (value: string | null): OrderStatus | undefined =>
  value !== null && isOrderStatus(value) ? value : undefined;

/** 자연수(0 포함) 문자열만 통과시킨다 - 음수·NaN·소수 등은 기본값으로 무시. */
const parsePage = (value: string | null): number => {
  if (value === null || !/^\d+$/.test(value)) {
    return DEFAULT_PAGE;
  }
  return Number(value);
};

export const parseOrderListFilters = (searchParams: URLSearchParams): OrderListFilters => ({
  status: parseStatus(searchParams.get('status')),
  page: parsePage(searchParams.get('page')),
});

/** 기본값·빈 값은 URL 을 지저분하게 만들 뿐이라 생략한다. */
export const serializeOrderListFilters = (filters: OrderListFilters): URLSearchParams => {
  const params = new URLSearchParams();

  if (filters.status !== undefined) {
    params.set('status', filters.status);
  }
  if (filters.page !== DEFAULT_PAGE) {
    params.set('page', String(filters.page));
  }

  return params;
};

export const toOrderListParams = (filters: OrderListFilters): OrderListParams => ({
  status: filters.status,
  page: filters.page,
  size: ORDER_PAGE_SIZE,
});
