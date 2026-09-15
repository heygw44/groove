import { afterEach, describe, expect, it, vi } from 'vitest';

import { withReissueLock } from '@/utils/reissueLock';

describe('withReissueLock()', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('navigator.locks 가 있으면 그 이름으로 잠금을 걸고 결과를 그대로 반환한다', async () => {
    // given
    const request = vi.fn((_name: string, fn: () => Promise<unknown>) => fn());
    vi.stubGlobal('navigator', { locks: { request } });
    const fn = vi.fn().mockResolvedValue('result');

    // when
    const result = await withReissueLock(fn);

    // then
    expect(result).toBe('result');
    expect(request).toHaveBeenCalledWith('groove-reissue', fn);
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it('navigator.locks 가 없으면 잠금 없이 fn 을 바로 실행한다', async () => {
    // given
    vi.stubGlobal('navigator', {});
    const fn = vi.fn().mockResolvedValue('result');

    // when
    const result = await withReissueLock(fn);

    // then
    expect(result).toBe('result');
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it('fn 이 실패하면 잠금을 통해서도 실패가 그대로 전파된다', async () => {
    // given
    const request = vi.fn((_name: string, fn: () => Promise<unknown>) => fn());
    vi.stubGlobal('navigator', { locks: { request } });
    const error = new Error('boom');
    const fn = vi.fn().mockRejectedValue(error);

    // when & then
    await expect(withReissueLock(fn)).rejects.toThrow(error);
  });
});
