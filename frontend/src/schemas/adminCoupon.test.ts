import { describe, expect, it } from 'vitest';

import {
  createAdminCouponFormSchema,
  EMPTY_ADMIN_COUPON_FORM_VALUES,
  generateCouponCode,
  getAdminCouponDisplayStatus,
  toCreatePayload,
  toFormValues,
  toUpdatePayload,
  type AdminCouponFormValues,
} from '@/schemas/adminCoupon';
import type { AdminCouponSummary } from '@/types/coupon';

const formValues = (overrides: Partial<AdminCouponFormValues> = {}): AdminCouponFormValues => ({
  ...EMPTY_ADMIN_COUPON_FORM_VALUES,
  code: 'ABCD1234',
  name: '테스트 쿠폰',
  discountValue: '5000',
  expiresAt: '2026-12-31T23:59',
  ...overrides,
});

const coupon = (overrides: Partial<AdminCouponSummary> = {}): AdminCouponSummary => ({
  id: 1,
  code: 'WELCOME10',
  name: '신규가입 쿠폰',
  discountType: 'FIXED',
  discountValue: 5000,
  minOrderAmount: 30000,
  issuedCount: 0,
  usedCount: 0,
  expiresAt: '2026-12-31T23:59:00',
  status: 'ACTIVE',
  createdAt: '2026-09-01T00:00:00',
  ...overrides,
});

describe('createAdminCouponFormSchema()', () => {
  const now = new Date('2026-09-04T00:00:00');
  const schema = createAdminCouponFormSchema(now);

  it('정률 할인 값이 100을 넘으면 실패한다', () => {
    // given
    const values = formValues({ discountType: 'RATE', discountValue: '101' });

    // when & then
    expect(schema.safeParse(values).success).toBe(false);
  });

  it('정률 할인 값이 100이면 통과한다', () => {
    // given
    const values = formValues({ discountType: 'RATE', discountValue: '100' });

    // when & then
    expect(schema.safeParse(values).success).toBe(true);
  });

  it('정액 할인 값이 0이면 실패한다', () => {
    // given
    const values = formValues({ discountType: 'FIXED', discountValue: '0' });

    // when & then
    expect(schema.safeParse(values).success).toBe(false);
  });

  it('최대 할인 금액이 0이면 실패한다', () => {
    // given
    const values = formValues({ maxDiscountAmount: '0' });

    // when & then
    expect(schema.safeParse(values).success).toBe(false);
  });

  it('총 수량이 0이면 실패한다', () => {
    // given
    const values = formValues({ totalQuantity: '0' });

    // when & then
    expect(schema.safeParse(values).success).toBe(false);
  });

  it('만료일이 현재 시각 이전이면 실패한다', () => {
    // given
    const values = formValues({ expiresAt: '2020-01-01T00:00' });

    // when & then
    expect(schema.safeParse(values).success).toBe(false);
  });

  it('만료일이 비어 있지 않고 미래이면 통과한다', () => {
    // given
    const values = formValues();

    // when & then
    expect(schema.safeParse(values).success).toBe(true);
  });
});

describe('toFormValues()', () => {
  it('소수 할인 값은 반올림해서 폼에 넣는다', () => {
    // given
    const original = coupon({ discountValue: 5000.5 });

    // when & then
    expect(toFormValues(original).discountValue).toBe('5001');
  });

  it('최대 할인 금액·총 수량이 없으면 빈 문자열로 채운다', () => {
    // given
    const original = coupon();

    // when
    const values = toFormValues(original);

    // then
    expect(values.maxDiscountAmount).toBe('');
    expect(values.totalQuantity).toBe('');
  });

  it('만료일은 초 단위를 잘라 datetime-local 형식으로 만든다', () => {
    // given
    const original = coupon({ expiresAt: '2026-12-31T23:59:59' });

    // when & then
    expect(toFormValues(original).expiresAt).toBe('2026-12-31T23:59');
  });
});

describe('toCreatePayload()', () => {
  it('총 수량을 비우면 키를 생략한다', () => {
    // given
    const values = formValues({ totalQuantity: '' });

    // when
    const payload = toCreatePayload(values);

    // then
    expect(payload).not.toHaveProperty('totalQuantity');
  });

  it('정액 할인이면 최대 할인 금액을 보내지 않는다', () => {
    // given
    const values = formValues({ discountType: 'FIXED', maxDiscountAmount: '5000' });

    // when
    const payload = toCreatePayload(values);

    // then
    expect(payload).not.toHaveProperty('maxDiscountAmount');
  });

  it('정률 할인이고 최대 할인 금액이 있으면 함께 보낸다', () => {
    // given
    const values = formValues({
      discountType: 'RATE',
      discountValue: '10',
      maxDiscountAmount: '5000',
    });

    // when
    const payload = toCreatePayload(values);

    // then
    expect(payload.maxDiscountAmount).toBe(5000);
  });

  it('만료일에 초 단위(:00)를 붙여 보낸다', () => {
    // given
    const values = formValues({ expiresAt: '2026-12-31T23:59' });

    // when
    const payload = toCreatePayload(values);

    // then
    expect(payload.expiresAt).toBe('2026-12-31T23:59:00');
  });

  it('최소 주문 금액을 비우면 0을 보낸다', () => {
    // given
    const values = formValues({ minOrderAmount: '' });

    // when
    const payload = toCreatePayload(values);

    // then
    expect(payload.minOrderAmount).toBe(0);
  });
});

describe('toUpdatePayload()', () => {
  it('바뀐 값이 없으면 빈 객체를 반환한다', () => {
    // given
    const original = coupon();
    const values = toFormValues(original);

    // when & then
    expect(toUpdatePayload(values, original)).toEqual({});
  });

  it('이름만 바꾸면 이름만 담는다', () => {
    // given
    const original = coupon();
    const values = { ...toFormValues(original), name: '새 이름' };

    // when & then
    expect(toUpdatePayload(values, original)).toEqual({ name: '새 이름' });
  });

  it('발급이 시작된 쿠폰은 할인 값을 바꿔도 포함하지 않는다', () => {
    // given
    const original = coupon({ issuedCount: 3 });
    const values = { ...toFormValues(original), discountValue: '9999' };

    // when
    const payload = toUpdatePayload(values, original);

    // then
    expect(payload).not.toHaveProperty('discountValue');
  });

  it('총 수량을 비우면 null을 보낸다', () => {
    // given
    const original = coupon({ totalQuantity: 100 });
    const values = { ...toFormValues(original), totalQuantity: '' };

    // when & then
    expect(toUpdatePayload(values, original)).toEqual({ totalQuantity: null });
  });

  it('원본도 무제한이면 총 수량을 비워도 포함하지 않는다', () => {
    // given
    const original = coupon({ totalQuantity: undefined });
    const values = { ...toFormValues(original), totalQuantity: '' };

    // when
    const payload = toUpdatePayload(values, original);

    // then
    expect(payload).not.toHaveProperty('totalQuantity');
  });

  it('최대 할인 금액을 비우면 null을 보낸다', () => {
    // given
    const original = coupon({ discountType: 'RATE', maxDiscountAmount: 5000 });
    const values = { ...toFormValues(original), maxDiscountAmount: '' };

    // when & then
    expect(toUpdatePayload(values, original)).toEqual({ maxDiscountAmount: null });
  });

  it('원본에 상한이 없으면 최대 할인 금액을 비워도 포함하지 않는다', () => {
    // given
    const original = coupon({ discountType: 'RATE', maxDiscountAmount: undefined });
    const values = { ...toFormValues(original), maxDiscountAmount: '' };

    // when
    const payload = toUpdatePayload(values, original);

    // then
    expect(payload).not.toHaveProperty('maxDiscountAmount');
  });

  it('상태가 바뀌면 status를 담는다', () => {
    // given
    const original = coupon({ status: 'ACTIVE' });
    const values = { ...toFormValues(original), status: 'DISABLED' as const };

    // when & then
    expect(toUpdatePayload(values, original)).toEqual({ status: 'DISABLED' });
  });
});

describe('generateCouponCode()', () => {
  it('기본 길이 10인 코드 문자열을 만든다', () => {
    // given & when
    const code = generateCouponCode();

    // then
    expect(code).toHaveLength(10);
    expect(code).toMatch(/^[A-HJ-NP-Z2-9]+$/);
  });

  it('길이를 지정할 수 있다', () => {
    // given & when & then
    expect(generateCouponCode(6)).toHaveLength(6);
  });
});

describe('getAdminCouponDisplayStatus()', () => {
  const now = new Date('2026-09-04T00:00:00');

  it('DISABLED 쿠폰은 만료 여부와 무관하게 DISABLED다', () => {
    // given
    const target = { status: 'DISABLED' as const, expiresAt: '2030-01-01T00:00:00' };

    // when & then
    expect(getAdminCouponDisplayStatus(target, now)).toBe('DISABLED');
  });

  it('만료일이 지났으면 EXPIRED다', () => {
    // given
    const target = { status: 'ACTIVE' as const, expiresAt: '2026-01-01T00:00:00' };

    // when & then
    expect(getAdminCouponDisplayStatus(target, now)).toBe('EXPIRED');
  });

  it('만료일이 남아 있으면 ACTIVE다', () => {
    // given
    const target = { status: 'ACTIVE' as const, expiresAt: '2030-01-01T00:00:00' };

    // when & then
    expect(getAdminCouponDisplayStatus(target, now)).toBe('ACTIVE');
  });
});
