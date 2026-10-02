import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { reissue } from '@/api/auth';
import { refreshClient } from '@/api/client';
import { resetUserActivityForTest } from '@/utils/userActivity';

describe('reissue()', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(1_000_000_000);
    resetUserActivityForTest(1_000_000_000 - 125_500);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('마지막 사용자 입력 이후 경과 초를 헤더로 보낸다', async () => {
    // given
    const post = vi
      .spyOn(refreshClient, 'post')
      .mockResolvedValue({ data: { data: { accessToken: 'new-token' } } });

    // when
    const result = await reissue();

    // then
    expect(result).toEqual({ accessToken: 'new-token' });
    expect(post).toHaveBeenCalledWith('/auth/reissue', undefined, {
      headers: { 'X-Client-Idle-Seconds': '125' },
    });
  });
});
