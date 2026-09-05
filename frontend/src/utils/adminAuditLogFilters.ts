import type {
  AdminAuditAction,
  AdminAuditLogListParams,
  AdminAuditTargetType,
} from '@/types/adminAuditLog';

export interface AdminAuditLogFilters {
  action?: AdminAuditAction;
  targetType?: AdminAuditTargetType;
  adminId?: number;
  from?: string;
  to?: string;
  page: number;
}

const DEFAULT_PAGE = 0;
const ADMIN_AUDIT_LOG_PAGE_SIZE = 20;
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

const AUDIT_ACTIONS = new Set<string>([
  'PRODUCT_CREATE',
  'PRODUCT_UPDATE',
  'PRODUCT_HIDE',
  'PRODUCT_RESTORE',
  'ORDER_STATUS_CHANGE',
  'COUPON_CREATE',
  'COUPON_UPDATE',
  'COUPON_DISABLE',
  'LIMITED_DROP_CREATE',
  'LIMITED_DROP_UPDATE',
  'LIMITED_DROP_OPEN',
  'LIMITED_DROP_CLOSE',
  'MEMBER_STATUS_CHANGE',
  'PAYMENT_CANCEL',
  'STOCK_ADJUST',
]);

const AUDIT_TARGET_TYPES = new Set<string>([
  'PRODUCT',
  'ORDER',
  'COUPON',
  'LIMITED_DROP',
  'MEMBER',
  'PAYMENT',
]);

const isAuditAction = (value: string): value is AdminAuditAction => AUDIT_ACTIONS.has(value);
const isAuditTargetType = (value: string): value is AdminAuditTargetType =>
  AUDIT_TARGET_TYPES.has(value);

const parseAction = (value: string | null): AdminAuditAction | undefined =>
  value !== null && isAuditAction(value) ? value : undefined;

const parseTargetType = (value: string | null): AdminAuditTargetType | undefined =>
  value !== null && isAuditTargetType(value) ? value : undefined;

/** 자연수(1 이상) 문자열만 통과시킨다 - 관리자 id 는 0 이하일 수 없다. */
const parseAdminId = (value: string | null): number | undefined => {
  if (value === null || !/^\d+$/.test(value) || Number(value) < 1) {
    return undefined;
  }
  return Number(value);
};

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

export const parseAdminAuditLogFilters = (
  searchParams: URLSearchParams,
): AdminAuditLogFilters => {
  const from = parseDate(searchParams.get('from'));
  const to = parseDate(searchParams.get('to'));
  const isInvalidRange = from !== undefined && to !== undefined && from > to;

  return {
    action: parseAction(searchParams.get('action')),
    targetType: parseTargetType(searchParams.get('targetType')),
    adminId: parseAdminId(searchParams.get('adminId')),
    from: isInvalidRange ? undefined : from,
    to: isInvalidRange ? undefined : to,
    page: parsePage(searchParams.get('page')),
  };
};

/** 기본값·빈 값은 URL 을 지저분하게 만들 뿐이라 생략한다. */
export const serializeAdminAuditLogFilters = (filters: AdminAuditLogFilters): URLSearchParams => {
  const params = new URLSearchParams();

  if (filters.action !== undefined) {
    params.set('action', filters.action);
  }
  if (filters.targetType !== undefined) {
    params.set('targetType', filters.targetType);
  }
  if (filters.adminId !== undefined) {
    params.set('adminId', String(filters.adminId));
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

export const toAdminAuditLogListParams = (
  filters: AdminAuditLogFilters,
): AdminAuditLogListParams => ({
  action: filters.action,
  targetType: filters.targetType,
  adminId: filters.adminId,
  from: filters.from,
  to: filters.to,
  page: filters.page,
  size: ADMIN_AUDIT_LOG_PAGE_SIZE,
});
