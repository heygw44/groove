import { describe, expect, it } from 'vitest';

import {
  parseAdminOrderFilters,
  serializeAdminOrderFilters,
  toAdminOrderListParams,
  type AdminOrderFilters,
} from '@/utils/adminOrderFilters';

const filters = (overrides: Partial<AdminOrderFilters> = {}): AdminOrderFilters => ({
  keyword: '',
  page: 0,
  ...overrides,
});

describe('parseAdminOrderFilters()', () => {
  it('빈 쿼리스트링이면 기본값을 쓴다', () => {
    // given
    const searchParams = new URLSearchParams();

    // when
    const result = parseAdminOrderFilters(searchParams);

    // then
    expect(result).toEqual({
      status: undefined,
      keyword: '',
      from: undefined,
      to: undefined,
      page: 0,
    });
  });

  it('유효한 status 값은 그대로 쓴다', () => {
    // given
    const searchParams = new URLSearchParams('status=PAID');

    // when & then
    expect(parseAdminOrderFilters(searchParams).status).toBe('PAID');
  });

  it('알 수 없는 status 값은 무시한다', () => {
    // given
    const searchParams = new URLSearchParams('status=UNKNOWN');

    // when & then
    expect(parseAdminOrderFilters(searchParams).status).toBeUndefined();
  });

  it('keyword 앞뒤 공백을 제거한다', () => {
    // given
    const searchParams = new URLSearchParams({ keyword: '  order-1  ' });

    // when & then
    expect(parseAdminOrderFilters(searchParams).keyword).toBe('order-1');
  });

  it('keyword 는 100자를 넘지 않는다', () => {
    // given
    const searchParams = new URLSearchParams({ keyword: 'a'.repeat(120) });

    // when & then
    expect(parseAdminOrderFilters(searchParams).keyword).toHaveLength(100);
  });

  it('형식이 올바른 from/to 는 그대로 쓴다', () => {
    // given
    const searchParams = new URLSearchParams({ from: '2026-01-01', to: '2026-01-31' });

    // when
    const result = parseAdminOrderFilters(searchParams);

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
      expect(parseAdminOrderFilters(searchParams).from).toBeUndefined();
    },
  );

  it('from 이 to 보다 늦으면 둘 다 버린다', () => {
    // given
    const searchParams = new URLSearchParams({ from: '2026-02-01', to: '2026-01-01' });

    // when
    const result = parseAdminOrderFilters(searchParams);

    // then
    expect(result.from).toBeUndefined();
    expect(result.to).toBeUndefined();
  });

  it.each(['-1', '1.5', 'abc', ''])('자연수가 아닌 page(%s) 는 0 으로 되돌린다', (value) => {
    // given
    const searchParams = new URLSearchParams(`page=${value}`);

    // when & then
    expect(parseAdminOrderFilters(searchParams).page).toBe(0);
  });
});

describe('serializeAdminOrderFilters()', () => {
  it('기본값이면 빈 쿼리스트링이 된다', () => {
    // given
    const value = filters();

    // when
    const params = serializeAdminOrderFilters(value);

    // then
    expect(params.toString()).toBe('');
  });

  it('설정된 필터만 쿼리스트링으로 옮긴다', () => {
    // given
    const value = filters({
      status: 'CANCELED',
      keyword: 'user@test.com',
      from: '2026-01-01',
      to: '2026-01-31',
      page: 2,
    });

    // when
    const params = serializeAdminOrderFilters(value);

    // then
    expect(params.toString()).toBe(
      'status=CANCELED&keyword=user%40test.com&from=2026-01-01&to=2026-01-31&page=2',
    );
  });

  it('직렬화한 값을 다시 파싱하면 원래 필터로 돌아온다', () => {
    // given
    const value = filters({ status: 'SHIPPED', keyword: 'ORD-1', page: 4 });

    // when
    const result = parseAdminOrderFilters(serializeAdminOrderFilters(value));

    // then
    expect(result).toEqual(value);
  });
});

describe('toAdminOrderListParams()', () => {
  it('페이지 크기를 20으로 채운다', () => {
    // given
    const value = filters({ status: 'PAID', page: 1 });

    // when
    const params = toAdminOrderListParams(value);

    // then
    expect(params).toEqual({
      status: 'PAID',
      keyword: undefined,
      from: undefined,
      to: undefined,
      page: 1,
      size: 20,
    });
  });

  it('빈 keyword 는 undefined 로 바꾼다', () => {
    // given
    const value = filters({ keyword: '' });

    // when
    const params = toAdminOrderListParams(value);

    // then
    expect(params.keyword).toBeUndefined();
  });
});
