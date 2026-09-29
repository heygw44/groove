import { describe, expect, it } from 'vitest';

import { ERROR_MESSAGES } from '@/constants/errorMessages';

describe('ERROR_MESSAGES', () => {
  it('ORDER_CANNOT_CANCEL 에 취소 불가 안내를 제공한다', () => {
    // given & when
    const message = ERROR_MESSAGES.ORDER_CANNOT_CANCEL;

    // then
    expect(message).toBe('취소할 수 없는 상태의 주문입니다.');
  });
});
