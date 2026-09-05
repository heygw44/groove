import { describe, expect, it } from 'vitest';

import {
  parseAdminMemberFilters,
  serializeAdminMemberFilters,
  toAdminMemberListParams,
  type AdminMemberFilters,
} from '@/utils/adminMemberFilters';

const filters = (overrides: Partial<AdminMemberFilters> = {}): AdminMemberFilters => ({
  keyword: '',
  page: 0,
  ...overrides,
});

describe('parseAdminMemberFilters()', () => {
  it('빈 쿼리스트링이면 기본값을 쓴다', () => {
    // given
    const searchParams = new URLSearchParams();

    // when
    const result = parseAdminMemberFilters(searchParams);

    // then
    expect(result).toEqual({
      keyword: '',
      status: undefined,
      role: undefined,
      page: 0,
    });
  });

  it('유효한 status 값은 그대로 쓴다', () => {
    // given
    const searchParams = new URLSearchParams('status=SUSPENDED');

    // when & then
    expect(parseAdminMemberFilters(searchParams).status).toBe('SUSPENDED');
  });

  it('알 수 없는 status 값은 무시한다', () => {
    // given
    const searchParams = new URLSearchParams('status=UNKNOWN');

    // when & then
    expect(parseAdminMemberFilters(searchParams).status).toBeUndefined();
  });

  it('유효한 role 값은 그대로 쓴다', () => {
    // given
    const searchParams = new URLSearchParams('role=ADMIN');

    // when & then
    expect(parseAdminMemberFilters(searchParams).role).toBe('ADMIN');
  });

  it('알 수 없는 role 값은 무시한다', () => {
    // given
    const searchParams = new URLSearchParams('role=UNKNOWN');

    // when & then
    expect(parseAdminMemberFilters(searchParams).role).toBeUndefined();
  });

  it('keyword 앞뒤 공백을 제거한다', () => {
    // given
    const searchParams = new URLSearchParams({ keyword: '  groover  ' });

    // when & then
    expect(parseAdminMemberFilters(searchParams).keyword).toBe('groover');
  });

  it('keyword 는 100자를 넘지 않는다', () => {
    // given
    const searchParams = new URLSearchParams({ keyword: 'a'.repeat(120) });

    // when & then
    expect(parseAdminMemberFilters(searchParams).keyword).toHaveLength(100);
  });

  it.each(['-1', '1.5', 'abc', ''])('자연수가 아닌 page(%s) 는 0 으로 되돌린다', (value) => {
    // given
    const searchParams = new URLSearchParams(`page=${value}`);

    // when & then
    expect(parseAdminMemberFilters(searchParams).page).toBe(0);
  });
});

describe('serializeAdminMemberFilters()', () => {
  it('기본값이면 빈 쿼리스트링이 된다', () => {
    // given
    const value = filters();

    // when
    const params = serializeAdminMemberFilters(value);

    // then
    expect(params.toString()).toBe('');
  });

  it('설정된 필터만 쿼리스트링으로 옮긴다', () => {
    // given
    const value = filters({
      keyword: 'groover@groove.com',
      status: 'SUSPENDED',
      role: 'USER',
      page: 2,
    });

    // when
    const params = serializeAdminMemberFilters(value);

    // then
    expect(params.toString()).toBe(
      'keyword=groover%40groove.com&status=SUSPENDED&role=USER&page=2',
    );
  });

  it('직렬화한 값을 다시 파싱하면 원래 필터로 돌아온다', () => {
    // given
    const value = filters({ status: 'ACTIVE', keyword: 'groover', page: 4 });

    // when
    const result = parseAdminMemberFilters(serializeAdminMemberFilters(value));

    // then
    expect(result).toEqual(value);
  });
});

describe('toAdminMemberListParams()', () => {
  it('페이지 크기를 20으로 채운다', () => {
    // given
    const value = filters({ status: 'ACTIVE', page: 1 });

    // when
    const params = toAdminMemberListParams(value);

    // then
    expect(params).toEqual({
      keyword: undefined,
      status: 'ACTIVE',
      role: undefined,
      page: 1,
      size: 20,
    });
  });

  it('빈 keyword 는 undefined 로 바꾼다', () => {
    // given
    const value = filters({ keyword: '' });

    // when
    const params = toAdminMemberListParams(value);

    // then
    expect(params.keyword).toBeUndefined();
  });
});
