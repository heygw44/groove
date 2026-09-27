import { describe, expect, it } from 'vitest';

import { getIdlePhase } from '@/utils/idlePhase';

describe('getIdlePhase()', () => {
  it('경과 13분 59.999초면 active 다(remaining 60_001ms)', () => {
    // given
    const lastActivity = 0;
    const now = 13 * 60 * 1000 + 59_999;

    // when
    const state = getIdlePhase(now, lastActivity);

    // then
    expect(state).toEqual({ phase: 'active', remainingMs: 60_001 });
  });

  it('경과 정확히 14분이면 warning 이다', () => {
    // given
    const lastActivity = 0;
    const now = 14 * 60 * 1000;

    // when
    const state = getIdlePhase(now, lastActivity);

    // then
    expect(state).toEqual({ phase: 'warning', remainingMs: 60_000 });
  });

  it('경과 정확히 15분이면 expired 다', () => {
    // given
    const lastActivity = 0;
    const now = 15 * 60 * 1000;

    // when
    const state = getIdlePhase(now, lastActivity);

    // then
    expect(state).toEqual({ phase: 'expired', remainingMs: 0 });
  });

  it('15분을 넘겨도 expired 이고 remainingMs 는 0 아래로 내려가지 않는다', () => {
    // given
    const lastActivity = 0;
    const now = 15 * 60 * 1000 + 10_000;

    // when
    const state = getIdlePhase(now, lastActivity);

    // then
    expect(state).toEqual({ phase: 'expired', remainingMs: 0 });
  });
});
