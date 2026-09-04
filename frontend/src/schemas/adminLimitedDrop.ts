import { z } from 'zod';

import type {
  AdminLimitedDropCreateRequest,
  AdminLimitedDropSummary,
  AdminLimitedDropUpdateRequest,
} from '@/types/limitedDrop';
import { getServerNow } from '@/utils/serverTime';

/*
 * 폼 값은 전부 문자열이다(adminCoupon.ts 와 같은 이유 - zod transform 을 쓰면 폼용/페이로드용
 * 두 제네릭을 오가게 되어 useForm<T> 하나로 못 쓴다). openAt 미래 검증은 호출 시점의
 * 서버 시각이 필요해 팩토리로 분리한다 - 테스트에서 고정 시각을 주입할 수 있어야 한다.
 * 수정은 SCHEDULED 드롭만 가능해 등록과 동일한 검증(openAt > now)을 그대로 적용한다.
 */
export const createAdminLimitedDropFormSchema = (now: Date = getServerNow()) =>
  z
    .object({
      productId: z.string().min(1, '상품을 선택해주세요.'),
      totalQuantity: z.string().regex(/^\d{1,9}$/, '1 이상의 정수로 입력해주세요.'),
      perMemberLimit: z.string().regex(/^\d{1,2}$/, '1~5 사이의 정수로 입력해주세요.'),
      openAt: z.string().min(1, '오픈 시각을 입력해주세요.'),
      closeAt: z.string().min(1, '마감 시각을 입력해주세요.'),
    })
    .superRefine((values, ctx) => {
      if (Number(values.totalQuantity) < 1) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['totalQuantity'],
          message: '1 이상의 정수로 입력해주세요.',
        });
      }

      const perMemberLimit = Number(values.perMemberLimit);
      if (perMemberLimit < 1 || perMemberLimit > 5) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['perMemberLimit'],
          message: '1~5 사이의 정수로 입력해주세요.',
        });
      }

      if (values.openAt && new Date(values.openAt) <= now) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['openAt'],
          message: '오픈 시각은 현재 시각 이후여야 합니다.',
        });
      }

      if (values.openAt && values.closeAt && new Date(values.closeAt) <= new Date(values.openAt)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['closeAt'],
          message: '마감 시각은 오픈 시각 이후여야 합니다.',
        });
      }
    });

export type AdminLimitedDropFormValues = z.infer<ReturnType<typeof createAdminLimitedDropFormSchema>>;

export const EMPTY_ADMIN_LIMITED_DROP_FORM_VALUES: AdminLimitedDropFormValues = {
  productId: '',
  totalQuantity: '',
  perMemberLimit: '1',
  openAt: '',
  closeAt: '',
};

export const toAdminLimitedDropFormValues = (
  drop: AdminLimitedDropSummary,
): AdminLimitedDropFormValues => ({
  productId: String(drop.productId),
  totalQuantity: String(drop.totalQuantity),
  perMemberLimit: String(drop.perMemberLimit),
  // datetime-local 입력값 형식(YYYY-MM-DDTHH:mm)에 맞춰 초 단위를 자른다.
  openAt: drop.openAt.slice(0, 16),
  closeAt: drop.closeAt.slice(0, 16),
});

/** 서버 LocalDateTime 은 초 단위까지 요구하므로, datetime-local 이 준 16자에 :00 을 붙인다. */
const toLocalDateTime = (value: string): string => (value.length === 16 ? `${value}:00` : value);

export const toAdminLimitedDropCreatePayload = (
  values: AdminLimitedDropFormValues,
): AdminLimitedDropCreateRequest => ({
  productId: Number(values.productId),
  totalQuantity: Number(values.totalQuantity),
  perMemberLimit: Number(values.perMemberLimit),
  openAt: toLocalDateTime(values.openAt),
  closeAt: toLocalDateTime(values.closeAt),
});

/**
 * 수정 요청은 키 생략 = 기존 유지이므로, 원본과 달라진 필드만 담는다.
 * productId 는 수정 시 상품 셀렉트가 잠겨 있어 비교 대상에서 뺀다.
 * 바뀐 필드가 하나도 없으면 undefined 를 반환해 호출부가 요청 자체를 건너뛸 수 있게 한다.
 */
export const toAdminLimitedDropUpdatePayload = (
  drop: AdminLimitedDropSummary,
  values: AdminLimitedDropFormValues,
): AdminLimitedDropUpdateRequest | undefined => {
  const payload: AdminLimitedDropUpdateRequest = {};

  const totalQuantity = Number(values.totalQuantity);
  if (totalQuantity !== drop.totalQuantity) {
    payload.totalQuantity = totalQuantity;
  }

  const perMemberLimit = Number(values.perMemberLimit);
  if (perMemberLimit !== drop.perMemberLimit) {
    payload.perMemberLimit = perMemberLimit;
  }

  const openAt = toLocalDateTime(values.openAt);
  if (openAt.slice(0, 16) !== drop.openAt.slice(0, 16)) {
    payload.openAt = openAt;
  }

  const closeAt = toLocalDateTime(values.closeAt);
  if (closeAt.slice(0, 16) !== drop.closeAt.slice(0, 16)) {
    payload.closeAt = closeAt;
  }

  return Object.keys(payload).length === 0 ? undefined : payload;
};
