import { describe, expect, it } from 'vitest';

import { formatPhone } from '@/utils/formatPhone';

describe('formatPhone()', () => {
  it.each([
    ['01032524855', '010-3252-4855'],
    ['0101234567', '010-123-4567'],
    ['0311234567', '031-123-4567'],
    ['021234567', '02-123-4567'],
    ['0212345678', '02-1234-5678'],
  ])('숫자만 입력한 %s 를 %s 로 끊는다', (input, expected) => {
    // given & when & then
    expect(formatPhone(input)).toBe(expected);
  });

  it.each([
    ['0', '0'],
    ['010', '010'],
    ['0103', '010-3'],
    ['010325', '010-325'],
    ['0103252', '010-325-2'],
    ['02', '02'],
    ['021', '02-1'],
  ])('입력 중인 %s 는 %s 로 채운다', (input, expected) => {
    // given & when & then
    expect(formatPhone(input)).toBe(expected);
  });

  it.each([
    ['010 3252 4855', '010-3252-4855'],
    ['010-3252-4855', '010-3252-4855'],
    ['010.3252.4855', '010-3252-4855'],
  ])('숫자가 아닌 문자가 섞인 %s 는 숫자만 남겨 다시 끊는다', (input, expected) => {
    // given & when & then
    expect(formatPhone(input)).toBe(expected);
  });

  it('최대 자릿수를 넘는 숫자는 잘라낸다', () => {
    // given & when & then
    expect(formatPhone('010325248559')).toBe('010-3252-4855');
    expect(formatPhone('02123456789')).toBe('02-1234-5678');
  });

  it('빈 값은 빈 문자열을 돌려준다', () => {
    // given & when & then
    expect(formatPhone('')).toBe('');
  });
});
