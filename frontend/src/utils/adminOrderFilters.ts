import type { AdminOrderListParams, OrderStatus } from '@/types/order';
import { isOrderStatus } from '@/utils/orderStatus';

export interface AdminOrderFilters {
  status?: OrderStatus;
  keyword: string;
  from?: string;
  to?: string;
  page: number;
}

const DEFAULT_PAGE = 0;
const ADMIN_ORDER_PAGE_SIZE = 20;
const KEYWORD_MAX_LENGTH = 100;
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

const parseStatus = (value: string | null): OrderStatus | undefined =>
  value !== null && isOrderStatus(value) ? value : undefined;

const parseKeyword = (value: string | null): string =>
  (value ?? '').trim().slice(0, KEYWORD_MAX_LENGTH);

/*
 * 형식(yyyy-MM-dd)과 실존 날짜 여부를 모두 확인한다. Date 는 2026-02-30 같은
 * 값을 3월로 롤오버해버리므로, 파싱한 날짜를 다시 같은 형식으로 찍어 원래
 * 문자열과 비교해야 존재하지 않는 날짜를 걸러낼 수 있다.
 */
const isValidDate = (value: string): boolean => {
  if (!DATE_PATTERN.test(value)) {
    return false;
  }
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) {
    return false;
  }
  const [year, month, day] = value.split('-').map(Number);
  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day;
};

const parseDate = (value: string | null): string | undefined =>
  value !== null && isValidDate(value) ? value : undefined;

/** 자연수(0 포함) 문자열만 통과시킨다 - 음수·NaN·소수 등은 기본값으로 무시. */
const parsePage = (value: string | null): number => {
  if (value === null || !/^\d+$/.test(value)) {
    return DEFAULT_PAGE;
  }
  return Number(value);
};

export const parseAdminOrderFilters = (searchParams: URLSearchParams): AdminOrderFilters => {
  const from = parseDate(searchParams.get('from'));
  const to = parseDate(searchParams.get('to'));
  const isInvalidRange = from !== undefined && to !== undefined && from > to;

  return {
    status: parseStatus(searchParams.get('status')),
    keyword: parseKeyword(searchParams.get('keyword')),
    from: isInvalidRange ? undefined : from,
    to: isInvalidRange ? undefined : to,
    page: parsePage(searchParams.get('page')),
  };
};

/** 기본값·빈 값은 URL 을 지저분하게 만들 뿐이라 생략한다. */
export const serializeAdminOrderFilters = (filters: AdminOrderFilters): URLSearchParams => {
  const params = new URLSearchParams();

  if (filters.status !== undefined) {
    params.set('status', filters.status);
  }
  if (filters.keyword !== '') {
    params.set('keyword', filters.keyword);
  }
  if (filters.from !== undefined) {
    params.set('from', filters.from);
  }
  if (filters.to !== undefined) {
    params.set('to', filters.to);
  }
  if (filters.page !== DEFAULT_PAGE) {
    params.set('page', String(filters.page));
  }

  return params;
};

export const toAdminOrderListParams = (filters: AdminOrderFilters): AdminOrderListParams => ({
  status: filters.status,
  keyword: filters.keyword === '' ? undefined : filters.keyword,
  from: filters.from,
  to: filters.to,
  page: filters.page,
  size: ADMIN_ORDER_PAGE_SIZE,
});
