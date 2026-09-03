import { describe, expect, it } from 'vitest';

import { PRODUCT_PAGE_SIZE } from '@/constants/product';
import {
  parseProductFilters,
  serializeProductFilters,
  toProductListParams,
  type ProductListFilters,
} from '@/utils/productFilters';

const filters = (overrides: Partial<ProductListFilters> = {}): ProductListFilters => ({
  sort: 'latest',
  page: 0,
  ...overrides,
});

describe('parseProductFilters()', () => {
  it('빈 쿼리스트링이면 기본 정렬과 첫 페이지를 쓴다', () => {
    // given
    const searchParams = new URLSearchParams();

    // when
    const result = parseProductFilters(searchParams);

    // then
    expect(result).toEqual({
      keyword: undefined,
      artistId: undefined,
      genreIds: undefined,
      labelId: undefined,
      minPrice: undefined,
      maxPrice: undefined,
      sort: 'latest',
      page: 0,
    });
  });

  it('공백뿐인 keyword 는 없는 것으로 본다', () => {
    // given
    const searchParams = new URLSearchParams('keyword=%20%20');

    // when & then
    expect(parseProductFilters(searchParams).keyword).toBeUndefined();
  });

  it('keyword 앞뒤 공백은 잘라낸다', () => {
    // given
    const searchParams = new URLSearchParams('keyword=%20blue%20');

    // when & then
    expect(parseProductFilters(searchParams).keyword).toBe('blue');
  });

  it.each(['-1', '1.5', 'abc', ''])('자연수가 아닌 artistId(%s) 는 무시한다', (value) => {
    // given
    const searchParams = new URLSearchParams(`artistId=${value}`);

    // when & then
    expect(parseProductFilters(searchParams).artistId).toBeUndefined();
  });

  it('잘못된 page 는 무시하고 0 을 쓴다', () => {
    // given
    const searchParams = new URLSearchParams('page=-3');

    // when & then
    expect(parseProductFilters(searchParams).page).toBe(0);
  });

  it('genreIds 는 자연수만 남기고 중복을 제거해 오름차순으로 정렬한다', () => {
    // given
    const searchParams = new URLSearchParams('genreIds=3&genreIds=1&genreIds=3&genreIds=x');

    // when & then
    expect(parseProductFilters(searchParams).genreIds).toEqual([1, 3]);
  });

  it('유효한 genreIds 가 하나도 없으면 undefined 가 된다', () => {
    // given
    const searchParams = new URLSearchParams('genreIds=x&genreIds=-1');

    // when & then
    expect(parseProductFilters(searchParams).genreIds).toBeUndefined();
  });

  it('알 수 없는 sort 값이면 latest 로 되돌린다', () => {
    // given
    const searchParams = new URLSearchParams('sort=cheapest');

    // when & then
    expect(parseProductFilters(searchParams).sort).toBe('latest');
  });

  it('허용된 sort 값은 그대로 쓴다', () => {
    // given
    const searchParams = new URLSearchParams('sort=priceDesc');

    // when & then
    expect(parseProductFilters(searchParams).sort).toBe('priceDesc');
  });
});

describe('serializeProductFilters()', () => {
  it('기본값과 빈 값은 URL 에 넣지 않는다', () => {
    // given
    const value = filters();

    // when
    const params = serializeProductFilters(value);

    // then
    expect(params.toString()).toBe('');
  });

  it('설정된 필터만 쿼리스트링으로 옮긴다', () => {
    // given
    const value = filters({
      keyword: 'blue',
      artistId: 7,
      genreIds: [1, 2],
      labelId: 3,
      minPrice: 10000,
      maxPrice: 50000,
      sort: 'priceAsc',
      page: 2,
    });

    // when
    const params = serializeProductFilters(value);

    // then
    expect(params.toString()).toBe(
      'keyword=blue&artistId=7&genreIds=1&genreIds=2&labelId=3&minPrice=10000&maxPrice=50000&sort=priceAsc&page=2',
    );
  });

  it('빈 genreIds 배열은 생략한다', () => {
    // given
    const value = filters({ genreIds: [] });

    // when & then
    expect(serializeProductFilters(value).getAll('genreIds')).toEqual([]);
  });

  it('minPrice 가 0 이어도 값이 있으면 넣는다', () => {
    // given
    const value = filters({ minPrice: 0 });

    // when & then
    expect(serializeProductFilters(value).get('minPrice')).toBe('0');
  });

  it('직렬화한 값을 다시 파싱하면 원래 필터로 돌아온다', () => {
    // given
    const value = filters({ keyword: 'blue', genreIds: [1, 2], sort: 'rating', page: 3 });

    // when
    const result = parseProductFilters(serializeProductFilters(value));

    // then
    expect(result).toMatchObject(value);
  });
});

describe('toProductListParams()', () => {
  it('URL 에 없는 size 를 기본 페이지 크기로 채운다', () => {
    // given
    const value = filters({ keyword: 'blue' });

    // when
    const params = toProductListParams(value);

    // then
    expect(params.size).toBe(PRODUCT_PAGE_SIZE);
    expect(params.keyword).toBe('blue');
  });
});
