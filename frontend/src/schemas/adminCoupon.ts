import { z } from 'zod';

import type {
  AdminCouponCreateRequest,
  AdminCouponDisplayStatus,
  AdminCouponSummary,
  AdminCouponUpdateRequest,
} from '@/types/coupon';
import { getServerNow } from '@/utils/serverTime';

const CODE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // 0/O, 1/I 처럼 헷갈리는 글자는 뺐다.

/*
 * 폼 값은 전부 문자열이다(product.ts 와 같은 이유 - zod transform 을 쓰면 폼용/페이로드용
 * 두 제네릭을 오가게 되어 useForm<T> 하나로 못 쓴다). expiresAt 미래 검증은 호출 시점의
 * 서버 시각이 필요해 팩토리로 분리한다 - 테스트에서 고정 시각을 주입할 수 있어야 한다.
 */
export const createAdminCouponFormSchema = (now: Date = getServerNow()) =>
  z
    .object({
      code: z.string().regex(/^[A-Z0-9]{6,20}$/, '코드는 영문 대문자/숫자 6~20자여야 합니다.'),
      name: z
        .string()
        .trim()
        .min(1, '이름을 입력해주세요.')
        .max(50, '이름은 50자 이하여야 합니다.'),
      discountType: z.enum(['FIXED', 'RATE']),
      discountValue: z.string().regex(/^\d{1,8}$/, '0 이상의 정수로 입력해주세요.'),
      minOrderAmount: z.string().regex(/^\d{0,8}$/, '0 이상의 정수로 입력해주세요.'),
      maxDiscountAmount: z.string().regex(/^\d{0,8}$/, '0 이상의 정수로 입력해주세요.'),
      totalQuantity: z.string().regex(/^\d{0,9}$/, '1 이상의 정수로 입력해주세요.'),
      expiresAt: z.string().min(1, '만료일을 입력해주세요.'),
      status: z.enum(['ACTIVE', 'DISABLED']),
    })
    .superRefine((values, ctx) => {
      const discountValue = Number(values.discountValue);
      if (discountValue <= 0) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['discountValue'],
          message: '할인 값은 1 이상이어야 합니다.',
        });
      } else if (values.discountType === 'RATE' && (discountValue < 1 || discountValue > 100)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['discountValue'],
          message: '정률 할인은 1~100 사이여야 합니다.',
        });
      }

      if (values.maxDiscountAmount !== '' && Number(values.maxDiscountAmount) <= 0) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['maxDiscountAmount'],
          message: '최대 할인 금액은 1 이상이어야 합니다.',
        });
      }

      if (values.totalQuantity !== '' && Number(values.totalQuantity) < 1) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['totalQuantity'],
          message: '1 이상의 정수로 입력해주세요.',
        });
      }

      if (values.expiresAt && new Date(values.expiresAt) <= now) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['expiresAt'],
          message: '만료일은 현재 시각 이후여야 합니다.',
        });
      }
    });

export type AdminCouponFormValues = z.infer<ReturnType<typeof createAdminCouponFormSchema>>;

export const EMPTY_ADMIN_COUPON_FORM_VALUES: AdminCouponFormValues = {
  code: '',
  name: '',
  discountType: 'FIXED',
  discountValue: '',
  minOrderAmount: '',
  maxDiscountAmount: '',
  totalQuantity: '',
  expiresAt: '',
  status: 'ACTIVE',
};

/** 저장된 값이 소수여도(서버는 소수 2자리까지 허용) 정수만 받는 폼 검증에 걸리지 않도록 반올림해 넣는다. */
const roundToString = (value: number): string => String(Math.round(value));

export const toFormValues = (coupon: AdminCouponSummary): AdminCouponFormValues => ({
  code: coupon.code,
  name: coupon.name,
  discountType: coupon.discountType,
  discountValue: roundToString(coupon.discountValue),
  minOrderAmount: roundToString(coupon.minOrderAmount),
  maxDiscountAmount:
    coupon.maxDiscountAmount !== undefined ? roundToString(coupon.maxDiscountAmount) : '',
  totalQuantity: coupon.totalQuantity !== undefined ? String(coupon.totalQuantity) : '',
  // datetime-local 입력값 형식(YYYY-MM-DDTHH:mm)에 맞춰 초 단위를 자른다.
  expiresAt: coupon.expiresAt.slice(0, 16),
  status: coupon.status,
});

/** 서버 LocalDateTime 은 초 단위까지 요구하므로, datetime-local 이 준 16자에 :00 을 붙인다. */
const toLocalDateTime = (value: string): string => (value.length === 16 ? `${value}:00` : value);

export const toCreatePayload = (values: AdminCouponFormValues): AdminCouponCreateRequest => {
  const payload: AdminCouponCreateRequest = {
    code: values.code,
    name: values.name,
    discountType: values.discountType,
    discountValue: Number(values.discountValue),
    minOrderAmount: values.minOrderAmount !== '' ? Number(values.minOrderAmount) : 0,
    expiresAt: toLocalDateTime(values.expiresAt),
  };

  // maxDiscountAmount 는 정률에서만 의미가 있다. 정액으로 등록하면 서버가 null 로 저장한다.
  if (values.discountType === 'RATE' && values.maxDiscountAmount !== '') {
    payload.maxDiscountAmount = Number(values.maxDiscountAmount);
  }
  if (values.totalQuantity !== '') {
    payload.totalQuantity = Number(values.totalQuantity);
  }

  return payload;
};

/**
 * 수정 요청은 키 생략 = 기존 유지이므로, 원본과 달라진 필드만 담는다.
 * 발급이 시작된(issuedCount > 0) 쿠폰은 할인 4필드 비교 자체를 건너뛰어 절대 포함하지 않는다.
 */
export const toUpdatePayload = (
  values: AdminCouponFormValues,
  original: AdminCouponSummary,
): AdminCouponUpdateRequest => {
  const payload: AdminCouponUpdateRequest = {};
  const discountLocked = original.issuedCount > 0;

  const name = values.name.trim();
  if (name !== original.name) {
    payload.name = name;
  }

  if (!discountLocked) {
    const discountValue = Number(values.discountValue);
    const minOrderAmount = values.minOrderAmount !== '' ? Number(values.minOrderAmount) : 0;
    const maxDiscountAmount =
      values.maxDiscountAmount !== '' ? Number(values.maxDiscountAmount) : undefined;

    if (values.discountType !== original.discountType) {
      payload.discountType = values.discountType;
    }
    if (discountValue !== original.discountValue) {
      payload.discountValue = discountValue;
    }
    if (minOrderAmount !== original.minOrderAmount) {
      payload.minOrderAmount = minOrderAmount;
    }
    if (maxDiscountAmount !== original.maxDiscountAmount) {
      payload.maxDiscountAmount = maxDiscountAmount;
    }
  }

  const nextExpiresAt = toLocalDateTime(values.expiresAt);
  if (nextExpiresAt.slice(0, 16) !== original.expiresAt.slice(0, 16)) {
    payload.expiresAt = nextExpiresAt;
  }

  // 빈 값(무제한 요청)은 원본에 수량이 있어도 보내지 않는다 - 한 번 정한 수량은 무제한으로 되돌릴 수 없다.
  if (values.totalQuantity !== '') {
    const totalQuantity = Number(values.totalQuantity);
    if (totalQuantity !== original.totalQuantity) {
      payload.totalQuantity = totalQuantity;
    }
  }

  if (values.status !== original.status) {
    payload.status = values.status;
  }

  return payload;
};

export const generateCouponCode = (length = 10): string => {
  const randomValues = crypto.getRandomValues(new Uint32Array(length));
  return Array.from(randomValues, (value) => CODE_CHARS[value % CODE_CHARS.length]).join('');
};

export const getAdminCouponDisplayStatus = (
  coupon: Pick<AdminCouponSummary, 'status' | 'expiresAt'>,
  now: Date = getServerNow(),
): AdminCouponDisplayStatus => {
  if (coupon.status === 'DISABLED') {
    return 'DISABLED';
  }
  return new Date(coupon.expiresAt) <= now ? 'EXPIRED' : 'ACTIVE';
};
