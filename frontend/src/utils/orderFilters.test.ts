import { describe, expect, it } from 'vitest';

import {
  parseOrderListFilters,
  serializeOrderListFilters,
  toOrderListParams,
  type OrderListFilters,
} from '@/utils/orderFilters';

const filters = (overrides: Partial<OrderListFilters> = {}): OrderListFilters => ({
  page: 0,
  ...overrides,
});

describe('parseOrderListFilters()', () => {
  it('빈 쿼리스트링이면 상태 없이 첫 페이지를 쓴다', () => {
    // given
    const searchParams = new URLSearchParams();

    // when
    const result = parseOrderListFilters(searchParams);

    // then
    expect(result).toEqual({ status: undefined, page: 0 });
  });

  it('유효한 status 값은 그대로 쓴다', () => {
    // given
    const searchParams = new URLSearchParams('status=PAID');

    // when & then
    expect(parseOrderListFilters(searchParams).status).toBe('PAID');
  });

  it('알 수 없는 status 값은 무시한다', () => {
    // given
    const searchParams = new URLSearchParams('status=UNKNOWN');

    // when & then
    expect(parseOrderListFilters(searchParams).status).toBeUndefined();
  });

  it.each(['-1', '1.5', 'abc', ''])('자연수가 아닌 page(%s) 는 0 으로 되돌린다', (value) => {
    // given
    const searchParams = new URLSearchParams(`page=${value}`);

    // when & then
    expect(parseOrderListFilters(searchParams).page).toBe(0);
  });

  it('자연수 page 값은 그대로 쓴다', () => {
    // given
    const searchParams = new URLSearchParams('page=3');

    // when & then
    expect(parseOrderListFilters(searchParams).page).toBe(3);
  });
});

describe('serializeOrderListFilters()', () => {
  it('기본값이면 빈 쿼리스트링이 된다', () => {
    // given
    const value = filters();

    // when
    const params = serializeOrderListFilters(value);

    // then
    expect(params.toString()).toBe('');
  });

  it('설정된 필터만 쿼리스트링으로 옮긴다', () => {
    // given
    const value = filters({ status: 'CANCELED', page: 2 });

    // when
    const params = serializeOrderListFilters(value);

    // then
    expect(params.toString()).toBe('status=CANCELED&page=2');
  });

  it('직렬화한 값을 다시 파싱하면 원래 필터로 돌아온다', () => {
    // given
    const value = filters({ status: 'SHIPPED', page: 4 });

    // when
    const result = parseOrderListFilters(serializeOrderListFilters(value));

    // then
    expect(result).toEqual(value);
  });
});

describe('toOrderListParams()', () => {
  it('페이지 크기를 10 으로 채운다', () => {
    // given
    const value = filters({ status: 'PAID', page: 1 });

    // when
    const params = toOrderListParams(value);

    // then
    expect(params).toEqual({ status: 'PAID', page: 1, size: 10 });
  });
});
