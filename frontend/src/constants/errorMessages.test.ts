import { describe, expect, it } from 'vitest';

import { ERROR_MESSAGES } from '@/constants/errorMessages';

describe('ERROR_MESSAGES', () => {
  it('ORDER_CANCEL_IN_PROGRESS 에 취소 진행 중 안내를 제공한다', () => {
    // given & when
    const message = ERROR_MESSAGES.ORDER_CANCEL_IN_PROGRESS;

    // then
    expect(message).toBe('취소가 진행 중인 주문입니다.');
  });
});
