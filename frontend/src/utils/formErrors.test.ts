import { describe, expect, it } from 'vitest';

import { getArrayFieldErrorMessage } from '@/utils/formErrors';

describe('getArrayFieldErrorMessage', () => {
  it('에러가 없으면 undefined 를 반환한다', () => {
    // given & when
    const result = getArrayFieldErrorMessage(undefined);

    // then
    expect(result).toBeUndefined();
  });

  it('배열 자체의 메시지가 있으면 그대로 반환한다', () => {
    // given
    const fieldError = { message: '최대 5명까지 고를 수 있습니다.', type: 'too_big' };

    // when
    const result = getArrayFieldErrorMessage(fieldError);

    // then
    expect(result).toBe('최대 5명까지 고를 수 있습니다.');
  });

  it('배열 메시지가 없고 root 메시지만 있으면 root 메시지를 반환한다', () => {
    // given
    const fieldError = { root: { message: '값을 다시 확인해주세요.' } };

    // when
    const result = getArrayFieldErrorMessage(fieldError);

    // then
    expect(result).toBe('값을 다시 확인해주세요.');
  });

  it('배열/root 메시지가 없으면 원소 안의 첫 에러 메시지를 찾아 반환한다', () => {
    // given
    const fieldError = {
      0: { nameEn: { type: 'invalid_type', message: 'nameEn 이 필요합니다.' } },
      1: { name: { type: 'invalid_type', message: 'name 이 필요합니다.' } },
    };

    // when
    const result = getArrayFieldErrorMessage(fieldError);

    // then
    expect(result).toBe('nameEn 이 필요합니다.');
  });

  it('원소에도 에러가 없으면 undefined 를 반환한다', () => {
    // given
    const fieldError = { 0: {}, 1: {} };

    // when
    const result = getArrayFieldErrorMessage(fieldError);

    // then
    expect(result).toBeUndefined();
  });
});
