import { describe, expect, it } from 'vitest';

import { ERROR_MESSAGES } from '@/constants/errorMessages';

describe('ERROR_MESSAGES', () => {
  it('ORDER_CANNOT_CANCEL 에 취소 불가 안내를 제공한다', () => {
    // given & when
    const message = ERROR_MESSAGES.ORDER_CANNOT_CANCEL;

    // then
    expect(message).toBe('취소할 수 없는 상태의 주문입니다.');
  });

  it.each([
    ['ORDER_CLAIM_NOT_ALLOWED', '취소·반품을 요청할 수 없는 상품 상태입니다.'],
    ['ORDER_CLAIM_IN_PROGRESS', '이미 처리 중인 취소·반품 요청이 있습니다.'],
    ['ORDER_CLAIM_REFUND_IN_PROGRESS', '환불을 처리하고 있습니다. 잠시 후 다시 확인해 주세요.'],
    ['ORDER_RETURN_PERIOD_EXPIRED', '반품 가능 기한(배송완료 후 7일)이 지났습니다.'],
  ])('%s 에 상품주문 클레임 안내를 제공한다', (code, expected) => {
    // given & when
    const message = ERROR_MESSAGES[code];

    // then
    expect(message).toBe(expected);
  });
});
