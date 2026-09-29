import type { OrderListParams, OrderStatusGroup } from '@/types/order';
import { isOrderStatusGroup } from '@/utils/orderStatus';

export interface OrderListFilters {
  statusGroup?: OrderStatusGroup;
  page: number;
}

const DEFAULT_PAGE = 0;
const ORDER_PAGE_SIZE = 10;

const parseStatusGroup = (value: string | null): OrderStatusGroup | undefined =>
  value !== null && isOrderStatusGroup(value) ? value : undefined;

/** 자연수(0 포함) 문자열만 통과시킨다 - 음수·NaN·소수 등은 기본값으로 무시. */
const parsePage = (value: string | null): number => {
  if (value === null || !/^\d+$/.test(value)) {
    return DEFAULT_PAGE;
  }
  return Number(value);
};

export const parseOrderListFilters = (searchParams: URLSearchParams): OrderListFilters => ({
  statusGroup: parseStatusGroup(searchParams.get('statusGroup')),
  page: parsePage(searchParams.get('page')),
});

/** 기본값·빈 값은 URL 을 지저분하게 만들 뿐이라 생략한다. */
export const serializeOrderListFilters = (filters: OrderListFilters): URLSearchParams => {
  const params = new URLSearchParams();

  if (filters.statusGroup !== undefined) {
    params.set('statusGroup', filters.statusGroup);
  }
  if (filters.page !== DEFAULT_PAGE) {
    params.set('page', String(filters.page));
  }

  return params;
};

export const toOrderListParams = (filters: OrderListFilters): OrderListParams => ({
  statusGroup: filters.statusGroup,
  page: filters.page,
  size: ORDER_PAGE_SIZE,
});
