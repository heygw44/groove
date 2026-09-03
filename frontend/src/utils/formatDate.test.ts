import { describe, expect, it } from 'vitest';

import { formatDate, formatDateTime } from '@/utils/formatDate';

// TZ 모호성을 피하려고 문자열이 아니라 로컬 Date 객체를 직접 만든다.
const FIXED_DATE = new Date(2026, 8, 3, 9, 5);
const MIDNIGHT_DATE = new Date(2026, 0, 1, 0, 0);

describe('formatDate()', () => {
  it('연.월.일 을 두 자리로 채워 점으로 구분한다', () => {
    // given & when
    const result = formatDate(FIXED_DATE);

    // then
    expect(result).toBe('2026. 09. 03.');
  });
});

describe('formatDateTime()', () => {
  it('연.월.일 뒤에 24시간제 시:분을 붙인다', () => {
    // given & when
    const result = formatDateTime(FIXED_DATE);

    // then
    expect(result).toBe('2026. 09. 03. 09:05');
  });

  it('자정은 24시가 아닌 00시로 표기한다', () => {
    // given & when
    const result = formatDateTime(MIDNIGHT_DATE);

    // then
    expect(result).toBe('2026. 01. 01. 00:00');
  });

  it('문자열 입력도 Date 와 동일하게 처리한다', () => {
    // given
    const isoLikeString = `${FIXED_DATE.getFullYear()}-${String(FIXED_DATE.getMonth() + 1).padStart(2, '0')}-${String(
      FIXED_DATE.getDate(),
    ).padStart(2, '0')}T${String(FIXED_DATE.getHours()).padStart(2, '0')}:${String(
      FIXED_DATE.getMinutes(),
    ).padStart(2, '0')}:00`;

    // when
    const result = formatDateTime(isoLikeString);

    // then
    expect(result).toBe(formatDateTime(FIXED_DATE));
  });
});
