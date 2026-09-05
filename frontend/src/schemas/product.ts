import { z } from 'zod';

import type {
  AdminProductCreateRequest,
  AdminProductUpdateRequest,
  ProductFormSource,
} from '@/types/product';

/*
 * 폼 값은 전부 문자열이다(셀렉트·인풋의 자연스러운 타입). 페이로드 변환은
 * toCreatePayload/toUpdatePayload 가 맡는다 - zod transform 을 쓰면 스키마가
 * 폼용/페이로드용 두 제네릭을 오가게 되어 useForm<T> 하나로 못 쓴다.
 */
export const productFormSchema = z.object({
  title: z.string().trim().min(1, '제목을 입력해주세요.').max(200, '제목은 200자 이하여야 합니다.'),
  artistId: z.string().min(1, '아티스트를 선택해주세요.'),
  labelId: z.string(),
  genreIds: z.array(z.number().int()),
  releaseDate: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/, '발매일 형식이 올바르지 않습니다.')
    .or(z.literal('')),
  pressingInfo: z.string().trim().max(100, '프레싱 정보는 100자 이하여야 합니다.'),
  colorVariant: z.string().trim().max(50, '컬러반 정보는 50자 이하여야 합니다.'),
  price: z
    .string()
    .min(1, '가격을 입력해주세요.')
    .regex(/^\d{1,8}$/, '0 이상의 정수로 입력해주세요.'),
  description: z.string().trim(),
  imageUrls: z.array(z.string().max(500)).max(10, '이미지는 10장까지 등록할 수 있습니다.'),
  initialStock: z.string(),
});

export const productCreateSchema = productFormSchema.extend({
  initialStock: z
    .string()
    .min(1, '초기 재고를 입력해주세요.')
    .regex(/^\d+$/, '0 이상의 정수로 입력해주세요.'),
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
  imageUrls: [...product.images].sort((a, b) => a.sortOrder - b.sortOrder).map((image) => image.url),
  initialStock: '',
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
});

/* genreIds/imageUrls 는 서버가 null=유지, []=전부 제거로 구분하므로 항상 배열을 전송한다. */
export const toUpdatePayload = (values: ProductFormValues): AdminProductUpdateRequest => ({
  title: values.title,
  artistId: Number(values.artistId),
  // 서버는 null=레이블 해제, 키 생략=유지로 구분하므로 값이 없으면 항상 null 을 보낸다.
  labelId: values.labelId ? Number(values.labelId) : null,
  genreIds: values.genreIds,
  releaseDate: values.releaseDate || undefined,
  pressingInfo: values.pressingInfo || undefined,
  colorVariant: values.colorVariant || undefined,
  price: Number(values.price),
  description: values.description || undefined,
  imageUrls: values.imageUrls,
});

export const stockAdjustSchema = z.object({
  changeType: z.enum(['IN', 'OUT', 'ADJUST']),
  quantity: z
    .string()
    .min(1, '수량을 입력해주세요.')
    .regex(/^[1-9]\d*$/, '1 이상의 정수로 입력해주세요.'),
  reason: z.string().trim().max(200, '사유는 200자 이하여야 합니다.'),
});

export type StockAdjustFormValues = z.infer<typeof stockAdjustSchema>;
