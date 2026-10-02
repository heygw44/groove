import { beforeEach, describe, expect, it } from 'vitest';

import {
  getIdleSeconds,
  getLastUserActivity,
  recordUserActivity,
  resetUserActivityForTest,
} from '@/utils/userActivity';

describe('userActivity', () => {
  beforeEach(() => {
    resetUserActivityForTest(1_000_000);
  });

  describe('recordUserActivity()', () => {
    it('더 늦은 시각이면 갱신한다', () => {
      // when
      recordUserActivity(1_005_000);

      // then
      expect(getLastUserActivity()).toBe(1_005_000);
    });

    it('더 이른 시각은 무시한다', () => {
      // when
      recordUserActivity(900_000);

      // then
      expect(getLastUserActivity()).toBe(1_000_000);
    });
  });

  describe('getIdleSeconds()', () => {
    it('경과 시간을 내림한 초로 돌려준다', () => {
      // when & then
      expect(getIdleSeconds(1_090_999)).toBe(90);
    });

    it('현재가 마지막 활동보다 이르면 0 을 돌려준다', () => {
      // when & then
      expect(getIdleSeconds(999_000)).toBe(0);
    });
  });
});
