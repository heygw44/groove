import { describe, expect, it } from 'vitest';

import {
  parseAdminAuditLogFilters,
  serializeAdminAuditLogFilters,
  toAdminAuditLogListParams,
  type AdminAuditLogFilters,
} from '@/utils/adminAuditLogFilters';

const filters = (overrides: Partial<AdminAuditLogFilters> = {}): AdminAuditLogFilters => ({
  page: 0,
  ...overrides,
});

describe('parseAdminAuditLogFilters()', () => {
  it('빈 쿼리스트링이면 기본값을 쓴다', () => {
    // given
    const searchParams = new URLSearchParams();

    // when
    const result = parseAdminAuditLogFilters(searchParams);

    // then
    expect(result).toEqual({
      action: undefined,
      targetType: undefined,
      adminId: undefined,
      from: undefined,
      to: undefined,
      page: 0,
    });
  });

  it('유효한 action 값은 그대로 쓴다', () => {
    // given
    const searchParams = new URLSearchParams('action=MEMBER_STATUS_CHANGE');

    // when & then
    expect(parseAdminAuditLogFilters(searchParams).action).toBe('MEMBER_STATUS_CHANGE');
  });

  it('알 수 없는 action 값은 무시한다', () => {
    // given
    const searchParams = new URLSearchParams('action=UNKNOWN');

    // when & then
    expect(parseAdminAuditLogFilters(searchParams).action).toBeUndefined();
  });

  it('유효한 targetType 값은 그대로 쓴다', () => {
    // given
    const searchParams = new URLSearchParams('targetType=MEMBER');

    // when & then
    expect(parseAdminAuditLogFilters(searchParams).targetType).toBe('MEMBER');
  });

  it('알 수 없는 targetType 값은 무시한다', () => {
    // given
    const searchParams = new URLSearchParams('targetType=UNKNOWN');

    // when & then
    expect(parseAdminAuditLogFilters(searchParams).targetType).toBeUndefined();
  });

  it('양의 정수 adminId 는 그대로 쓴다', () => {
    // given
    const searchParams = new URLSearchParams('adminId=3');

    // when & then
    expect(parseAdminAuditLogFilters(searchParams).adminId).toBe(3);
  });

  it.each(['0', '-1', '1.5', 'abc'])('올바르지 않은 adminId(%s) 는 무시한다', (value) => {
    // given
    const searchParams = new URLSearchParams(`adminId=${value}`);

    // when & then
    expect(parseAdminAuditLogFilters(searchParams).adminId).toBeUndefined();
  });

  it('형식이 올바른 from/to 는 그대로 쓴다', () => {
    // given
    const searchParams = new URLSearchParams({ from: '2026-01-01', to: '2026-01-31' });

    // when
    const result = parseAdminAuditLogFilters(searchParams);

    // then
    expect(result.from).toBe('2026-01-01');
    expect(result.to).toBe('2026-01-31');
  });

  it.each(['2026-13-01', '2026/01/01', 'not-a-date', '2026-02-30'])(
    '형식이 잘못되거나 존재하지 않는 날짜(%s)는 무시한다',
    (value) => {
      // given
      const searchParams = new URLSearchParams({ from: value });

      // when & then
      expect(parseAdminAuditLogFilters(searchParams).from).toBeUndefined();
    },
  );

  it('from 이 to 보다 늦으면 둘 다 버린다', () => {
    // given
    const searchParams = new URLSearchParams({ from: '2026-02-01', to: '2026-01-01' });

    // when
    const result = parseAdminAuditLogFilters(searchParams);

    // then
    expect(result.from).toBeUndefined();
    expect(result.to).toBeUndefined();
  });

  it.each(['-1', '1.5', 'abc', ''])('자연수가 아닌 page(%s) 는 0 으로 되돌린다', (value) => {
    // given
    const searchParams = new URLSearchParams(`page=${value}`);

    // when & then
    expect(parseAdminAuditLogFilters(searchParams).page).toBe(0);
  });
});

describe('serializeAdminAuditLogFilters()', () => {
  it('기본값이면 빈 쿼리스트링이 된다', () => {
    // given
    const value = filters();

    // when
    const params = serializeAdminAuditLogFilters(value);

    // then
    expect(params.toString()).toBe('');
  });

  it('설정된 필터만 쿼리스트링으로 옮긴다', () => {
    // given
    const value = filters({
      action: 'MEMBER_STATUS_CHANGE',
      targetType: 'MEMBER',
      adminId: 3,
      from: '2026-01-01',
      to: '2026-01-31',
      page: 2,
    });

    // when
    const params = serializeAdminAuditLogFilters(value);

    // then
    expect(params.toString()).toBe(
      'action=MEMBER_STATUS_CHANGE&targetType=MEMBER&adminId=3&from=2026-01-01&to=2026-01-31&page=2',
    );
  });

  it('직렬화한 값을 다시 파싱하면 원래 필터로 돌아온다', () => {
    // given
    const value = filters({ action: 'PAYMENT_CANCEL', adminId: 5, page: 4 });

    // when
    const result = parseAdminAuditLogFilters(serializeAdminAuditLogFilters(value));

    // then
    expect(result).toEqual(value);
  });
});

describe('toAdminAuditLogListParams()', () => {
  it('페이지 크기를 20으로 채운다', () => {
    // given
    const value = filters({ action: 'STOCK_ADJUST', page: 1 });

    // when
    const params = toAdminAuditLogListParams(value);

    // then
    expect(params).toEqual({
      action: 'STOCK_ADJUST',
      targetType: undefined,
      adminId: undefined,
      from: undefined,
      to: undefined,
      page: 1,
      size: 20,
    });
  });
});
