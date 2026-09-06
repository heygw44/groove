import { describe, expect, it } from 'vitest';

import {
  EMPTY_PRODUCT_FORM_VALUES,
  productCreateSchema,
  productFormSchema,
  toCreatePayload,
  toFormValues,
  toFormValuesFromRelease,
  toUpdatePayload,
  type ProductFormValues,
} from '@/schemas/product';
import type { CatalogReleaseDetail } from '@/types/catalog';
import type { Label, ProductFormSource } from '@/types/product';

const formValues = (overrides: Partial<ProductFormValues> = {}): ProductFormValues => ({
  ...EMPTY_PRODUCT_FORM_VALUES,
  title: 'Kind of Blue',
  artistId: '7',
  price: '42000',
  initialStock: '10',
  albumMode: 'existing',
  albumId: '5',
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
  album: { id: 9, title: 'Kind of Blue', originalReleaseYear: 1959 },
  country: 'US',
  pressingYear: 1959,
  catalogNo: 'CL 1355',
  barcode: '074643866926',
  editionType: 'ORIGINAL',
  ...overrides,
});

const releaseDetail = (overrides: Partial<CatalogReleaseDetail> = {}): CatalogReleaseDetail => ({
  discogsReleaseId: 123,
  title: 'Kind of Blue',
  artistName: 'Miles Davis',
  labelName: 'Columbia',
  country: 'US',
  pressingYear: 1959,
  catalogNo: 'CL 1355',
  barcode: '074643866926',
  editionType: 'ORIGINAL',
  genreNames: ['Jazz'],
  imageUrl: 'https://discogs.example/thumb.jpg',
  description: '설명',
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

  it('앨범 이동은 지원하지 않으므로 앨범 선택 필드를 비워 둔다', () => {
    // given & when
    const values = toFormValues(source());

    // then
    expect(values.albumMode).toBe('existing');
    expect(values.albumId).toBe('');
  });

  it('프레싱 필드를 그대로 옮긴다', () => {
    // given & when
    const values = toFormValues(source());

    // then
    expect(values.country).toBe('US');
    expect(values.pressingYear).toBe('1959');
    expect(values.catalogNo).toBe('CL 1355');
    expect(values.barcode).toBe('074643866926');
    expect(values.editionType).toBe('ORIGINAL');
  });
});

describe('productCreateSchema (앨범 선택)', () => {
  it('기존 앨범인데 albumId 가 비면 검증에 실패한다', () => {
    // given
    const values = formValues({ albumMode: 'existing', albumId: '' });

    // when & then
    expect(productCreateSchema.safeParse(values).success).toBe(false);
  });

  it('새 앨범인데 제목이 비면 검증에 실패한다', () => {
    // given
    const values = formValues({ albumMode: 'new', albumId: '', newAlbumTitle: '  ' });

    // when & then
    expect(productCreateSchema.safeParse(values).success).toBe(false);
  });

  it('선택한 쪽 값이 채워져 있으면 통과한다', () => {
    // given
    const existing = formValues({ albumMode: 'existing', albumId: '5' });
    const created = formValues({ albumMode: 'new', albumId: '', newAlbumTitle: '새 앨범' });

    // when & then
    expect(productCreateSchema.safeParse(existing).success).toBe(true);
    expect(productCreateSchema.safeParse(created).success).toBe(true);
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

  it('기존 앨범을 선택하면 albumId 만 싣고 newAlbum 은 비운다', () => {
    // given
    const values = formValues({ albumMode: 'existing', albumId: '5', newAlbumTitle: '무시될 값' });

    // when
    const payload = toCreatePayload(values);

    // then
    expect(payload.albumId).toBe(5);
    expect(payload.newAlbum).toBeUndefined();
  });

  it('새 앨범을 선택하면 newAlbum 만 싣고 albumId 는 비운다', () => {
    // given
    const values = formValues({
      albumMode: 'new',
      albumId: '',
      newAlbumTitle: '새 앨범',
      newAlbumYear: '2020',
    });

    // when
    const payload = toCreatePayload(values);

    // then
    expect(payload.albumId).toBeUndefined();
    expect(payload.newAlbum).toEqual({ title: '새 앨범', originalReleaseYear: 2020 });
  });

  it('프레싱 필드가 비어 있으면 키를 undefined 로 남긴다', () => {
    // given
    const values = formValues({ country: '', pressingYear: '', catalogNo: '', barcode: '', editionType: '' });

    // when
    const payload = toCreatePayload(values);

    // then
    expect(payload.country).toBeUndefined();
    expect(payload.pressingYear).toBeUndefined();
    expect(payload.catalogNo).toBeUndefined();
    expect(payload.barcode).toBeUndefined();
    expect(payload.editionType).toBeUndefined();
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

  it('프레싱 필드가 비면 해제 신호로 null 을 보낸다', () => {
    // given
    const values = formValues({ country: '', pressingYear: '', catalogNo: '', barcode: '' });

    // when
    const payload = toUpdatePayload(values);

    // then
    expect(payload.country).toBeNull();
    expect(payload.pressingYear).toBeNull();
    expect(payload.catalogNo).toBeNull();
    expect(payload.barcode).toBeNull();
  });

  it('editionType 은 해제 개념이 없어 비어 있으면 키를 undefined 로 남긴다', () => {
    // given
    const values = formValues({ editionType: '' });

    // when & then
    expect(toUpdatePayload(values).editionType).toBeUndefined();
  });

  it('앨범 관련 필드는 수정 요청에 실리지 않는다', () => {
    // given
    const values = formValues({ albumMode: 'new', newAlbumTitle: '무시될 값' });

    // when
    const payload = toUpdatePayload(values);

    // then
    expect(payload).not.toHaveProperty('albumId');
    expect(payload).not.toHaveProperty('newAlbum');
  });
});

describe('toFormValuesFromRelease()', () => {
  const labels: Label[] = [
    { id: 3, name: 'Columbia', country: 'US' },
    { id: 4, name: 'Blue Note', country: 'US' },
  ];

  it('이름이 일치하는 레이블이 있으면 선택하고 없으면 비운다', () => {
    // given
    const matched = releaseDetail({ labelName: 'Columbia' });
    const unmatched = releaseDetail({ labelName: '존재하지 않는 레이블' });

    // when & then
    expect(toFormValuesFromRelease(matched, labels).labelId).toBe('3');
    expect(toFormValuesFromRelease(unmatched, labels).labelId).toBe('');
  });

  it('프레싱·설명 필드를 그대로 옮긴다', () => {
    // given & when
    const values = toFormValuesFromRelease(releaseDetail(), labels);

    // then
    expect(values).toMatchObject({
      title: 'Kind of Blue',
      country: 'US',
      pressingYear: '1959',
      catalogNo: 'CL 1355',
      barcode: '074643866926',
      editionType: 'ORIGINAL',
      description: '설명',
    });
  });

  it('앨범은 새 앨범 쪽을 기본 선택하고 제목을 채운다', () => {
    // given & when
    const values = toFormValuesFromRelease(releaseDetail(), labels);

    // then
    expect(values.albumMode).toBe('new');
    expect(values.newAlbumTitle).toBe('Kind of Blue');
    expect(values.newAlbumYear).toBe('1959');
  });

  it('값이 없는 필드는 빈 문자열로 채운다', () => {
    // given
    const detail = releaseDetail({
      title: undefined,
      labelName: undefined,
      country: undefined,
      pressingYear: undefined,
      catalogNo: undefined,
      barcode: undefined,
      description: undefined,
    });

    // when
    const values = toFormValuesFromRelease(detail, labels);

    // then
    expect(values).toMatchObject({
      title: '',
      labelId: '',
      country: '',
      pressingYear: '',
      catalogNo: '',
      barcode: '',
      description: '',
      newAlbumTitle: '',
      newAlbumYear: '',
    });
  });
});
