import { describe, expect, it } from 'vitest';

import { emailField } from '@/schemas/auth';

describe('emailField', () => {
  it('ASCII 이메일은 통과한다', () => {
    expect(emailField.safeParse('user@example.com').success).toBe(true);
  });

  it('전각 문자가 섞이면 실패한다', () => {
    // given
    const email = 'ｕser@example.com';

    // when
    const result = emailField.safeParse(email);

    // then
    expect(result.success).toBe(false);
  });

  it('100자를 넘으면 실패한다', () => {
    // given
    const email = `${'a'.repeat(95)}@e.com`;

    // when
    const result = emailField.safeParse(email);

    // then
    expect(result.success).toBe(false);
  });
});
