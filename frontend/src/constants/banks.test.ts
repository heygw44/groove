import { describe, expect, it } from 'vitest';

import { BANK_OPTIONS, getBankName } from './banks';

describe('getBankName', () => {
  it('두 자리·세 자리 코드를 모두 은행명으로 바꾼다', () => {
    expect(getBankName('20')).toBe('우리은행');
    expect(getBankName('020')).toBe('우리은행');
  });

  it('국민은행은 두 자리 06 과 세 자리 004 가 같은 은행이다', () => {
    expect(getBankName('06')).toBe('KB국민은행');
    expect(getBankName('004')).toBe('KB국민은행');
  });

  it('모르는 코드는 그대로 돌려준다', () => {
    expect(getBankName('999')).toBe('999');
  });
});

describe('BANK_OPTIONS', () => {
  it('환불계좌 요청에 쓰도록 두 자리 코드만 담는다', () => {
    expect(BANK_OPTIONS.every((option) => option.code.length === 2)).toBe(true);
  });
});
