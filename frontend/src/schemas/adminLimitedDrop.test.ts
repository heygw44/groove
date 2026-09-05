import { describe, expect, it } from 'vitest';

import {
  createAdminLimitedDropFormSchema,
  EMPTY_ADMIN_LIMITED_DROP_FORM_VALUES,
  toAdminLimitedDropCreatePayload,
  toAdminLimitedDropFormValues,
  toAdminLimitedDropUpdatePayload,
  type AdminLimitedDropFormValues,
} from '@/schemas/adminLimitedDrop';
import type { AdminLimitedDropSummary } from '@/types/limitedDrop';

const formValues = (
  overrides: Partial<AdminLimitedDropFormValues> = {},
): AdminLimitedDropFormValues => ({
  ...EMPTY_ADMIN_LIMITED_DROP_FORM_VALUES,
  productId: '1',
  totalQuantity: '100',
  perMemberLimit: '2',
  openAt: '2026-09-10T10:00',
  closeAt: '2026-09-11T10:00',
  ...overrides,
});

const drop = (overrides: Partial<AdminLimitedDropSummary> = {}): AdminLimitedDropSummary => ({
  id: 1,
  productId: 1,
  productTitle: 'Kind of Blue',
  totalQuantity: 100,
  soldCount: 0,
  perMemberLimit: 2,
  openAt: '2026-09-10T10:00:00',
  closeAt: '2026-09-11T10:00:00',
  status: 'SCHEDULED',
  createdAt: '2026-09-01T00:00:00',
  ...overrides,
});

describe('createAdminLimitedDropFormSchema()', () => {
  const now = new Date('2026-09-04T00:00:00');
  const schema = createAdminLimitedDropFormSchema(now);

  it('총 수량이 0이면 실패한다', () => {
    // given
    const values = formValues({ totalQuantity: '0' });

    // when & then
    expect(schema.safeParse(values).success).toBe(false);
  });

  it('1인 한도가 0이면 실패한다', () => {
    // given
    const values = formValues({ perMemberLimit: '0' });

    // when & then
    expect(schema.safeParse(values).success).toBe(false);
  });

  it('1인 한도가 6이면 실패한다', () => {
    // given
    const values = formValues({ perMemberLimit: '6' });

    // when & then
    expect(schema.safeParse(values).success).toBe(false);
  });

  it('1인 한도가 5이면 통과한다', () => {
    // given
    const values = formValues({ perMemberLimit: '5' });

    // when & then
    expect(schema.safeParse(values).success).toBe(true);
  });

  it('오픈 시각이 현재 시각 이전이면 실패한다', () => {
    // given
    const values = formValues({ openAt: '2020-01-01T00:00' });

    // when & then
    expect(schema.safeParse(values).success).toBe(false);
  });

  it('마감 시각이 오픈 시각보다 이르면 실패한다', () => {
    // given
    const values = formValues({ openAt: '2026-09-11T10:00', closeAt: '2026-09-10T10:00' });

    // when
    const result = schema.safeParse(values);

    // then
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues[0].path).toEqual(['closeAt']);
    }
  });

  it('모든 값이 유효하면 통과한다', () => {
    // given
    const values = formValues();

    // when & then
    expect(schema.safeParse(values).success).toBe(true);
  });
});

describe('toAdminLimitedDropFormValues()', () => {
  it('시각은 초 단위를 잘라 datetime-local 형식으로 만든다', () => {
    // given
    const original = drop({ openAt: '2026-09-10T10:00:00', closeAt: '2026-09-11T10:00:00' });

    // when
    const values = toAdminLimitedDropFormValues(original);

    // then
    expect(values.openAt).toBe('2026-09-10T10:00');
    expect(values.closeAt).toBe('2026-09-11T10:00');
  });

  it('productId 를 문자열로 변환한다', () => {
    // given
    const original = drop({ productId: 42 });

    // when & then
    expect(toAdminLimitedDropFormValues(original).productId).toBe('42');
  });
});

describe('toAdminLimitedDropCreatePayload()', () => {
  it('문자열 값을 숫자·LocalDateTime 문자열로 변환한다', () => {
    // given
    const values = formValues();

    // when
    const payload = toAdminLimitedDropCreatePayload(values);

    // then
    expect(payload).toEqual({
      productId: 1,
      totalQuantity: 100,
      perMemberLimit: 2,
      openAt: '2026-09-10T10:00:00',
      closeAt: '2026-09-11T10:00:00',
    });
  });
});

describe('toAdminLimitedDropUpdatePayload()', () => {
  it('바뀐 값이 없으면 undefined를 반환한다', () => {
    // given
    const original = drop();
    const values = toAdminLimitedDropFormValues(original);

    // when & then
    expect(toAdminLimitedDropUpdatePayload(original, values)).toBeUndefined();
  });

  it('총 수량만 바꾸면 totalQuantity만 담는다', () => {
    // given
    const original = drop();
    const values = { ...toAdminLimitedDropFormValues(original), totalQuantity: '200' };

    // when & then
    expect(toAdminLimitedDropUpdatePayload(original, values)).toEqual({ totalQuantity: 200 });
  });

  it('마감 시각만 바꾸면 closeAt만 담는다', () => {
    // given
    const original = drop();
    const values = { ...toAdminLimitedDropFormValues(original), closeAt: '2026-09-15T10:00' };

    // when & then
    expect(toAdminLimitedDropUpdatePayload(original, values)).toEqual({
      closeAt: '2026-09-15T10:00:00',
    });
  });
});
