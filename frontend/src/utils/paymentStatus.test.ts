import { describe, expect, it } from 'vitest';

import type { PaymentStatus } from '@/types/payment';
import { PAYMENT_STATUSES, PAYMENT_STATUS_LABEL, isReconcilePending } from '@/utils/paymentStatus';

describe('PAYMENT_STATUS_LABEL', () => {
  it.each(PAYMENT_STATUSES)('%s 상태의 한글 라벨을 갖는다', (status) => {
    // given & when
    const label = PAYMENT_STATUS_LABEL[status];

    // then
    expect(label).toBeTruthy();
  });
});

describe('isReconcilePending()', () => {
  it.each<[PaymentStatus, boolean]>([
    ['READY', false],
    ['DONE', false],
    ['CANCELED', false],
    ['FAILED', false],
    ['UNKNOWN', true],
    ['CANCEL_REQUESTED', true],
  ])('%s 상태는 대사 대기 여부가 %s 이다', (status, expected) => {
    // given & when
    const result = isReconcilePending(status);

    // then
    expect(result).toBe(expected);
  });
});
