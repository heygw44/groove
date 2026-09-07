import { z } from 'zod';

import type { CatalogReleaseDetail, EditionType } from '@/types/catalog';
import type {
  AdminProductCreateRequest,
  AdminProductUpdateRequest,
  Label,
  ProductFormSource,
} from '@/types/product';

const YEAR_REGEX = /^\d{4}$/;

/*
 * 폼 값은 전부 문자열이다(셀렉트·인풋의 자연스러운 타입). 페이로드 변환은
 * toCreatePayload/toUpdatePayload 가 맡는다 - zod transform 을 쓰면 스키마가
 * 폼용/페이로드용 두 제네릭을 오가게 되어 useForm<T> 하나로 못 쓴다.
 */
export const productFormSchema = z.object({
  title: z
    .string()
    .trim()
    .min(1, '제목을 입력해주세요.')
    .max(200, '제목은 200자 이하로 입력해주세요.'),
  artistId: z.string().min(1, '아티스트를 선택해주세요.'),
  labelId: z.string(),
  genreIds: z.array(z.number().int()),
  releaseDate: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/, '발매일 형식이 올바르지 않습니다.')
    .or(z.literal('')),
  pressingInfo: z.string().trim().max(100, '사양은 100자 이하로 입력해주세요.'),
  colorVariant: z.string().trim().max(50, '컬러반은 50자 이하로 입력해주세요.'),
  price: z
    .string()
    .min(1, '가격을 입력해주세요.')
    .regex(/^\d{1,8}$/, '0 이상의 숫자로 입력해주세요.'),
  description: z.string().trim(),
  imageUrls: z.array(z.string().max(500)).max(10, '이미지는 10장까지 등록할 수 있습니다.'),
  initialStock: z.string(),
  // 등록 폼에서만 쓴다(수정은 앨범 이동을 지원하지 않는다). 필수 여부는 productCreateSchema 가 검증한다.
  albumMode: z.enum(['existing', 'new']),
  albumId: z.string(),
  newAlbumTitle: z.string().trim().max(200, '앨범 제목은 200자 이하로 입력해주세요.'),
  newAlbumYear: z
    .string()
    .regex(YEAR_REGEX, '발매 연도는 4자리 숫자로 입력해주세요.')
    .or(z.literal('')),
  country: z.string(),
  pressingYear: z
    .string()
    .regex(YEAR_REGEX, '제작 연도는 4자리 숫자로 입력해주세요.')
    .or(z.literal('')),
  catalogNo: z.string().trim().max(50, '카탈로그 번호는 50자 이하로 입력해주세요.'),
  barcode: z.string().trim().max(20, '바코드는 20자 이하로 입력해주세요.'),
  editionType: z.string(),
});

export const productCreateSchema = productFormSchema
  .extend({
    initialStock: z
      .string()
      .min(1, '초기 재고를 입력해주세요.')
      .regex(/^\d+$/, '0 이상의 숫자로 입력해주세요.'),
  })
  .superRefine((values, ctx) => {
    if (values.albumMode === 'existing' && !values.albumId) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['albumId'],
        message: '기존 앨범을 선택해주세요.',
      });
    }
    if (values.albumMode === 'new' && !values.newAlbumTitle.trim()) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['newAlbumTitle'],
        message: '새 앨범 제목을 입력해주세요.',
      });
    }
  });

export type ProductFormValues = z.infer<typeof productFormSchema>;

export const EMPTY_PRODUCT_FORM_VALUES: ProductFormValues = {
  title: '',
  artistId: '',
  labelId: '',
  genreIds: [],
  releaseDate: '',
  pressingInfo: '',
  colorVariant: '',
  price: '',
  description: '',
  imageUrls: [],
  initialStock: '',
  albumMode: 'existing',
  albumId: '',
  newAlbumTitle: '',
  newAlbumYear: '',
  country: '',
  pressingYear: '',
  catalogNo: '',
  barcode: '',
  editionType: '',
};

export const toFormValues = (product: ProductFormSource): ProductFormValues => ({
  title: product.title,
  artistId: String(product.artist.id),
  labelId: product.label ? String(product.label.id) : '',
  genreIds: product.genres.map((genre) => genre.id),
  releaseDate: product.releaseDate ?? '',
  pressingInfo: product.pressingInfo ?? '',
  colorVariant: product.colorVariant ?? '',
  // 원 단위 정수만 허용하므로, 이전에 저장된 소수 가격이 폼 검증에 걸려 수정을 막지 않도록 반올림해 넣는다.
  price: String(Math.round(product.price)),
  description: product.description ?? '',
  imageUrls: [...product.images]
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((image) => image.url),
  initialStock: '',
  // 수정 폼은 앨범 이동을 지원하지 않으므로 값을 쓰지 않는다.
  albumMode: 'existing',
  albumId: '',
  newAlbumTitle: '',
  newAlbumYear: '',
  country: product.country ?? '',
  pressingYear: product.pressingYear !== undefined ? String(product.pressingYear) : '',
  catalogNo: product.catalogNo ?? '',
  barcode: product.barcode ?? '',
  editionType: product.editionType,
});

export const toCreatePayload = (values: ProductFormValues): AdminProductCreateRequest => ({
  title: values.title,
  artistId: Number(values.artistId),
  labelId: values.labelId ? Number(values.labelId) : undefined,
  genreIds: values.genreIds,
  releaseDate: values.releaseDate || undefined,
  pressingInfo: values.pressingInfo || undefined,
  colorVariant: values.colorVariant || undefined,
  price: Number(values.price),
  description: values.description || undefined,
  imageUrls: values.imageUrls,
  initialStock: Number(values.initialStock),
  // albumId/newAlbum 은 정확히 하나만 실어야 서버가 받아준다(둘 다 있거나 없으면 400).
  albumId: values.albumMode === 'existing' && values.albumId ? Number(values.albumId) : undefined,
  newAlbum:
    values.albumMode === 'new' && values.newAlbumTitle.trim()
      ? {
          title: values.newAlbumTitle.trim(),
          originalReleaseYear: values.newAlbumYear ? Number(values.newAlbumYear) : undefined,
        }
      : undefined,
  country: values.country || undefined,
  pressingYear: values.pressingYear ? Number(values.pressingYear) : undefined,
  catalogNo: values.catalogNo || undefined,
  barcode: values.barcode || undefined,
  editionType: values.editionType ? (values.editionType as EditionType) : undefined,
});

/* genreIds/imageUrls 는 서버가 null=유지, []=전부 제거로 구분하므로 항상 배열을 전송한다. */
export const toUpdatePayload = (values: ProductFormValues): AdminProductUpdateRequest => ({
  title: values.title,
  artistId: Number(values.artistId),
  // 서버는 null=해제, 키 생략=유지로 구분하므로 값이 없으면 항상 null 을 보낸다.
  labelId: values.labelId ? Number(values.labelId) : null,
  genreIds: values.genreIds,
  releaseDate: values.releaseDate || undefined,
  pressingInfo: values.pressingInfo || undefined,
  colorVariant: values.colorVariant || undefined,
  price: Number(values.price),
  description: values.description || undefined,
  imageUrls: values.imageUrls,
  country: values.country || null,
  pressingYear: values.pressingYear ? Number(values.pressingYear) : null,
  catalogNo: values.catalogNo || null,
  barcode: values.barcode || null,
  editionType: values.editionType ? (values.editionType as EditionType) : undefined,
});

/**
 * Discogs 릴리즈 상세를 등록 폼 프리필 값으로 옮긴다. 가격·재고·이미지는 채우지 않는다
 * (Discogs 는 가격을 주지 않고, 이미지는 저장·업로드가 금지된 Restricted Data 다).
 * artistName 은 id 가 없어 폼 필드가 아니다 - 호출부가 ArtistSearchSelect 검색창의
 * 초기 키워드로 넘겨 관리자가 직접 확정하게 한다.
 */
export function toFormValuesFromRelease(
  detail: CatalogReleaseDetail,
  labels: Label[],
): Pick<
  ProductFormValues,
  | 'title'
  | 'labelId'
  | 'country'
  | 'pressingYear'
  | 'catalogNo'
  | 'barcode'
  | 'editionType'
  | 'description'
  | 'albumMode'
  | 'newAlbumTitle'
  | 'newAlbumYear'
> {
  const matchedLabel = detail.labelName
    ? labels.find((label) => label.name === detail.labelName)
    : undefined;

  return {
    title: detail.title ?? '',
    labelId: matchedLabel ? String(matchedLabel.id) : '',
    country: detail.country ?? '',
    pressingYear: detail.pressingYear !== undefined ? String(detail.pressingYear) : '',
    catalogNo: detail.catalogNo ?? '',
    barcode: detail.barcode ?? '',
    editionType: detail.editionType,
    description: detail.description ?? '',
    // 관리자가 기존 앨범 검색으로 바꿀 수 있으니 기본은 새 앨범 쪽에 제목만 채운다.
    albumMode: 'new',
    newAlbumTitle: detail.title ?? '',
    newAlbumYear: detail.pressingYear !== undefined ? String(detail.pressingYear) : '',
  };
}

export const stockAdjustSchema = z.object({
  changeType: z.enum(['IN', 'OUT', 'ADJUST']),
  quantity: z
    .string()
    .min(1, '수량을 입력해주세요.')
    .regex(/^[1-9]\d*$/, '1 이상의 숫자로 입력해주세요.'),
  reason: z.string().trim().max(200, '사유는 200자 이하로 입력해주세요.'),
});

export type StockAdjustFormValues = z.infer<typeof stockAdjustSchema>;
