import { afterEach, describe, expect, it, vi } from 'vitest';

import { applyServerTime, getServerNowMs, toServerMs } from '@/utils/serverTime';

/**
 * performance.now() 를 직접 스텁한다. vitest 의 fake timers 는 setSystemTime 이
 * performance.now() 도 같이 밀어버려(sinon 기본 동작) "시스템 시계 조작에 영향받지
 * 않는다"는 명제 자체를 테스트할 수 없다.
 */
const stubPerformanceNow = (value: number) => vi.spyOn(performance, 'now').mockReturnValue(value);

describe('serverTime', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('applyServerTime()', () => {
    it('동기화 직후에는 서버 시각을 그대로 반환한다', () => {
      // given
      stubPerformanceNow(1000);
      const serverTime = '2026-09-04T12:00:00+09:00';

      // when
      applyServerTime(serverTime);

      // then
      expect(getServerNowMs()).toBe(new Date(serverTime).getTime());
    });

    it('동기화 이후 시스템 시계가 조작돼도 performance.now() 경과만큼만 흐른다', () => {
      // given
      stubPerformanceNow(1000);
      const serverTime = '2026-09-04T12:00:00+09:00';
      applyServerTime(serverTime);

      // when: 시스템 시계를 5분 앞으로 돌려도(performance.now() 는 무관하게 5초만 경과)
      vi.setSystemTime(new Date('2026-09-04T12:05:00+09:00'));
      stubPerformanceNow(1000 + 5000);

      // then
      expect(getServerNowMs()).toBe(new Date(serverTime).getTime() + 5000);
    });
  });

  describe('toServerMs()', () => {
    it('오프셋이 없는 문자열은 +09:00 을 붙여 해석한다', () => {
      // given & when
      const result = toServerMs('2026-09-05T20:00:00');

      // then
      expect(result).toBe(new Date('2026-09-05T20:00:00+09:00').getTime());
    });

    it('오프셋이 이미 있는 문자열은 그대로 해석한다', () => {
      // given & when
      const result = toServerMs('2026-09-05T20:00:00Z');

      // then
      expect(result).toBe(new Date('2026-09-05T20:00:00Z').getTime());
    });
  });
});
