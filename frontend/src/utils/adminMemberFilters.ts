import type { AdminMemberListParams } from '@/types/adminMember';
import type { MemberRole, MemberStatus } from '@/types/member';

export interface AdminMemberFilters {
  keyword: string;
  status?: MemberStatus;
  role?: MemberRole;
  page: number;
}

const DEFAULT_PAGE = 0;
const ADMIN_MEMBER_PAGE_SIZE = 20;
const KEYWORD_MAX_LENGTH = 100;

const MEMBER_STATUSES = new Set<string>(['ACTIVE', 'SUSPENDED', 'WITHDRAWN']);
const MEMBER_ROLES = new Set<string>(['USER', 'ADMIN']);

const isMemberStatus = (value: string): value is MemberStatus => MEMBER_STATUSES.has(value);
const isMemberRole = (value: string): value is MemberRole => MEMBER_ROLES.has(value);

const parseStatus = (value: string | null): MemberStatus | undefined =>
  value !== null && isMemberStatus(value) ? value : undefined;

const parseRole = (value: string | null): MemberRole | undefined =>
  value !== null && isMemberRole(value) ? value : undefined;

const parseKeyword = (value: string | null): string =>
  (value ?? '').trim().slice(0, KEYWORD_MAX_LENGTH);

/** 자연수(0 포함) 문자열만 통과시킨다 - 음수·NaN·소수 등은 기본값으로 무시. */
const parsePage = (value: string | null): number => {
  if (value === null || !/^\d+$/.test(value)) {
    return DEFAULT_PAGE;
  }
  return Number(value);
};

export const parseAdminMemberFilters = (searchParams: URLSearchParams): AdminMemberFilters => ({
  keyword: parseKeyword(searchParams.get('keyword')),
  status: parseStatus(searchParams.get('status')),
  role: parseRole(searchParams.get('role')),
  page: parsePage(searchParams.get('page')),
});

/** 기본값·빈 값은 URL 을 지저분하게 만들 뿐이라 생략한다. */
export const serializeAdminMemberFilters = (filters: AdminMemberFilters): URLSearchParams => {
  const params = new URLSearchParams();

  if (filters.keyword !== '') {
    params.set('keyword', filters.keyword);
  }
  if (filters.status !== undefined) {
    params.set('status', filters.status);
  }
  if (filters.role !== undefined) {
    params.set('role', filters.role);
  }
  if (filters.page !== DEFAULT_PAGE) {
    params.set('page', String(filters.page));
  }

  return params;
};

export const toAdminMemberListParams = (filters: AdminMemberFilters): AdminMemberListParams => ({
  keyword: filters.keyword === '' ? undefined : filters.keyword,
  status: filters.status,
  role: filters.role,
  page: filters.page,
  size: ADMIN_MEMBER_PAGE_SIZE,
});
