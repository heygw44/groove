import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  formatDate,
  formatDateTime,
  formatRelativeFromNow,
  formatServerDate,
  formatServerDateTime,
} from '@/utils/formatDate';
import * as serverTime from '@/utils/serverTime';

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

describe('formatServerDate() / formatServerDateTime()', () => {
  /*
   * 서버 LocalDateTime 문자열(오프셋 없음)은 KST 로 해석해야 한다. 화면에 찍히는
   * 시:분 자체는 호스트 타임존에 따라 달라지는 게 맞으므로(뷰어 로컬 시각으로 보여주는
   * 게 의도) 값을 하드코딩하지 않는다. 대신 프로덕션 코드(toServerMs)와는 별도로
   * 이 테스트에서 직접 "+09:00" 을 붙여 기대값을 계산해, TZ=UTC 로 돌려도(호스트가
   * 뭐든) 통과하면서 KST 해석 자체가 틀어지는 회귀도 잡는다.
   */
  const iso = '2026-09-05T20:00:00';
  const expectedDate = new Date(Date.parse(`${iso}+09:00`));

  it('오프셋 없는 문자열을 KST 로 해석해 formatDate() 와 같은 형식으로 찍는다', () => {
    // given & when
    const result = formatServerDate(iso);

    // then
    expect(result).toBe(formatDate(expectedDate));
  });

  it('오프셋 없는 문자열을 KST 로 해석해 formatDateTime() 과 같은 형식으로 찍는다', () => {
    // given & when
    const result = formatServerDateTime(iso);

    // then
    expect(result).toBe(formatDateTime(expectedDate));
  });

  it('이미 오프셋이 있는 문자열은 KST 를 덧붙이지 않고 그대로 해석한다', () => {
    // given
    const isoWithOffset = '2026-09-05T20:00:00Z';

    // when
    const result = formatServerDateTime(isoWithOffset);

    // then
    expect(result).toBe(formatDateTime(new Date(isoWithOffset)));
  });
});

describe('formatRelativeFromNow()', () => {
  const NOW = '2026-09-05T12:00:00+09:00';

  afterEach(() => {
    vi.restoreAllMocks();
  });

  const stubServerNow = (iso: string) => {
    vi.spyOn(serverTime, 'getServerNowMs').mockReturnValue(new Date(iso).getTime());
  };

  it.each([
    ['0초 전은 방금 전으로 표기한다', '2026-09-05T12:00:00+09:00', '방금 전'],
    ['59초 전은 방금 전으로 표기한다', '2026-09-05T11:59:01+09:00', '방금 전'],
    ['1분 전부터 분 단위로 표기한다', '2026-09-05T11:59:00+09:00', '1분 전'],
    ['59분 전은 분 단위로 표기한다', '2026-09-05T11:01:00+09:00', '59분 전'],
    ['1시간 전부터 시간 단위로 표기한다', '2026-09-05T11:00:00+09:00', '1시간 전'],
    ['23시간 전은 시간 단위로 표기한다', '2026-09-04T13:00:00+09:00', '23시간 전'],
    ['24시간 전부터 일 단위로 표기한다', '2026-09-04T12:00:00+09:00', '1일 전'],
  ])('%s', (_label, aggregatedAt, expected) => {
    // given
    stubServerNow(NOW);

    // when
    const result = formatRelativeFromNow(aggregatedAt);

    // then
    expect(result).toBe(expected);
  });

  it('시계 오차로 미래 시각이 나오면 방금 전으로 접는다', () => {
    // given
    stubServerNow(NOW);
    const futureIso = '2026-09-05T12:05:00+09:00';

    // when
    const result = formatRelativeFromNow(futureIso);

    // then
    expect(result).toBe('방금 전');
  });
});
