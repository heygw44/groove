import { describe, expect, it } from 'vitest';

import {
  EMPTY_PRODUCT_FORM_VALUES,
  productCreateSchema,
  productFormSchema,
  toCreatePayload,
  toFormValues,
  toUpdatePayload,
  type ProductFormValues,
} from '@/schemas/product';
import type { ProductFormSource } from '@/types/product';

const formValues = (overrides: Partial<ProductFormValues> = {}): ProductFormValues => ({
  ...EMPTY_PRODUCT_FORM_VALUES,
  title: 'Kind of Blue',
  artistId: '7',
  price: '42000',
  initialStock: '10',
  ...overrides,
});

const source = (overrides: Partial<ProductFormSource> = {}): ProductFormSource => ({
  id: 1,
  title: 'Kind of Blue',
  artist: { id: 7, name: '마일스 데이비스', nameEn: 'Miles Davis' },
  label: { id: 3, name: 'Columbia', country: 'US' },
  genres: [{ id: 2, name: 'Jazz' }],
  releaseDate: '1959-08-17',
  pressingInfo: '180g',
  colorVariant: 'Black',
  price: 42000,
  description: '설명',
  images: [
    { url: 'https://cdn.groove.local/b.jpg', sortOrder: 1 },
    { url: 'https://cdn.groove.local/a.jpg', sortOrder: 0 },
  ],
  ...overrides,
});

describe('productFormSchema', () => {
  it('제목이 비어 있으면 검증에 실패한다', () => {
    // given
    const values = formValues({ title: '   ' });

    // when & then
    expect(productFormSchema.safeParse(values).success).toBe(false);
  });

  it('발매일은 빈 문자열이거나 yyyy-MM-dd 형식이어야 한다', () => {
    // given & when & then
    expect(productFormSchema.safeParse(formValues({ releaseDate: '' })).success).toBe(true);
    expect(productFormSchema.safeParse(formValues({ releaseDate: '1959-08-17' })).success).toBe(
      true,
    );
    expect(productFormSchema.safeParse(formValues({ releaseDate: '1959/08/17' })).success).toBe(
      false,
    );
  });

  it.each(['45000.5', '-1', '1e5', ''])('정수가 아닌 가격(%s) 은 거부한다', (price) => {
    // given
    const values = formValues({ price });

    // when & then
    expect(productFormSchema.safeParse(values).success).toBe(false);
  });

  it('등록 스키마는 초기 재고를 요구한다', () => {
    // given
    const values = formValues({ initialStock: '' });

    // when & then
    expect(productFormSchema.safeParse(values).success).toBe(true);
    expect(productCreateSchema.safeParse(values).success).toBe(false);
  });
});

describe('toFormValues()', () => {
  it('이미지를 sortOrder 순으로 정렬해 URL 만 남긴다', () => {
    // given
    const product = source();

    // when
    const values = toFormValues(product);

    // then
    expect(values.imageUrls).toEqual([
      'https://cdn.groove.local/a.jpg',
      'https://cdn.groove.local/b.jpg',
    ]);
  });

  it('레이블이 없으면 빈 문자열로 채운다', () => {
    // given
    const product = source({ label: undefined });

    // when & then
    expect(toFormValues(product).labelId).toBe('');
  });

  it('저장된 소수 가격은 반올림해서 폼에 넣는다', () => {
    // given
    const product = source({ price: 45000.5 });

    // when & then
    expect(toFormValues(product).price).toBe('45001');
  });

  it('초기 재고는 수정 폼에서 쓰지 않으므로 비워 둔다', () => {
    // given & when & then
    expect(toFormValues(source()).initialStock).toBe('');
  });
});

describe('toCreatePayload()', () => {
  it('선택 입력이 비면 키를 undefined 로 남긴다', () => {
    // given
    const values = formValues({ labelId: '', releaseDate: '', description: '' });

    // when
    const payload = toCreatePayload(values);

    // then
    expect(payload.labelId).toBeUndefined();
    expect(payload.releaseDate).toBeUndefined();
    expect(payload.description).toBeUndefined();
  });

  it('문자열 폼 값을 숫자로 변환한다', () => {
    // given
    const values = formValues({ labelId: '3', initialStock: '10' });

    // when
    const payload = toCreatePayload(values);

    // then
    expect(payload).toMatchObject({ artistId: 7, labelId: 3, price: 42000, initialStock: 10 });
  });
});

describe('toUpdatePayload()', () => {
  it('레이블이 비면 해제 신호로 null 을 보낸다', () => {
    // given
    const values = formValues({ labelId: '' });

    // when & then
    expect(toUpdatePayload(values).labelId).toBeNull();
  });

  it('genreIds 와 imageUrls 는 비어 있어도 배열로 보낸다', () => {
    // given
    const values = formValues({ genreIds: [], imageUrls: [] });

    // when
    const payload = toUpdatePayload(values);

    // then
    expect(payload.genreIds).toEqual([]);
    expect(payload.imageUrls).toEqual([]);
  });
});
