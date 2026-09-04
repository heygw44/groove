import { describe, expect, it } from 'vitest';

import type { LimitedDropDetail, LimitedDropSummary } from '@/types/limitedDrop';
import {
  formatDday,
  getDropPhase,
  getPurchaseButtonState,
  pickBannerDrop,
  splitCountdown,
} from '@/utils/limitedDrop';

const summary = (overrides: Partial<LimitedDropSummary> = {}): LimitedDropSummary => ({
  id: 1,
  product: { id: 1, title: 'LP', artistName: 'Artist', price: 30000 },
  totalQuantity: 100,
  remainingQuantity: 10,
  perMemberLimit: 1,
  openAt: '2026-09-05T20:00:00+09:00',
  closeAt: '2026-09-06T20:00:00+09:00',
  status: 'SCHEDULED',
  ...overrides,
});

describe('getDropPhase()', () => {
  it('SCHEDULED 이고 서버 시각이 openAt 이전이면 SCHEDULED 를 반환한다', () => {
    // given
    const drop = summary({ status: 'SCHEDULED', openAt: '2026-09-05T20:00:00+09:00' });
    const nowMs = new Date('2026-09-05T19:59:59+09:00').getTime();

    // when & then
    expect(getDropPhase(drop, nowMs)).toBe('SCHEDULED');
  });

  it('SCHEDULED 이지만 서버 시각이 openAt 을 지났으면 OPENING 을 반환한다', () => {
    // given
    const drop = summary({ status: 'SCHEDULED', openAt: '2026-09-05T20:00:00+09:00' });
    const nowMs = new Date('2026-09-05T20:00:01+09:00').getTime();

    // when & then
    expect(getDropPhase(drop, nowMs)).toBe('OPENING');
  });

  it('SCHEDULED 가 아니면 상태를 그대로 반환한다', () => {
    // given
    const drop = summary({ status: 'OPEN' });

    // when & then
    expect(getDropPhase(drop, Date.now())).toBe('OPEN');
  });
});

describe('splitCountdown()', () => {
  it('밀리초를 일/시/분/초로 쪼갠다', () => {
    // given
    const ms = 1 * 24 * 60 * 60 * 1000 + 2 * 60 * 60 * 1000 + 3 * 60 * 1000 + 4 * 1000;

    // when & then
    expect(splitCountdown(ms)).toEqual({ days: 1, hours: 2, minutes: 3, seconds: 4 });
  });

  it('음수는 0으로 클램프한다', () => {
    // when & then
    expect(splitCountdown(-5000)).toEqual({ days: 0, hours: 0, minutes: 0, seconds: 0 });
  });
});

describe('formatDday()', () => {
  it('24시간 이상 남았으면 D-n 을 반환한다', () => {
    // given
    const nowMs = new Date('2026-09-04T00:00:00+09:00').getTime();
    const openAtMs = new Date('2026-09-06T00:00:00+09:00').getTime();

    // when & then
    expect(formatDday(openAtMs, nowMs)).toBe('D-2');
  });

  it('24시간 미만이면 카운트다운 문자열을 반환한다', () => {
    // given
    const nowMs = new Date('2026-09-04T00:00:00+09:00').getTime();
    const openAtMs = new Date('2026-09-04T01:02:03+09:00').getTime();

    // when & then
    expect(formatDday(openAtMs, nowMs)).toBe('01:02:03');
  });
});

describe('getPurchaseButtonState()', () => {
  const notPurchased: Pick<LimitedDropDetail, 'purchased'> = { purchased: false };

  it('SCHEDULED/OPENING 이면 오픈 대기 상태다', () => {
    // when & then
    expect(getPurchaseButtonState(notPurchased, 'SCHEDULED', true)).toEqual({
      label: '오픈 대기',
      disabled: true,
    });
    expect(getPurchaseButtonState(notPurchased, 'OPENING', true)).toEqual({
      label: '오픈 대기',
      disabled: true,
    });
  });

  it('SOLD_OUT 이면 매진 상태다', () => {
    // when & then
    expect(getPurchaseButtonState(notPurchased, 'SOLD_OUT', true)).toEqual({
      label: '매진',
      disabled: true,
    });
  });

  it('CLOSED 면 마감 상태다', () => {
    // when & then
    expect(getPurchaseButtonState(notPurchased, 'CLOSED', true)).toEqual({
      label: '마감',
      disabled: true,
    });
  });

  it('OPEN 이고 이미 구매했으면 구매 완료 상태다', () => {
    // when & then
    expect(getPurchaseButtonState({ purchased: true }, 'OPEN', true)).toEqual({
      label: '구매 완료',
      disabled: true,
    });
  });

  it('OPEN 이고 구매 이력이 없으면 구매하기 상태다', () => {
    // when & then
    expect(getPurchaseButtonState(notPurchased, 'OPEN', true)).toEqual({
      label: '구매하기',
      disabled: false,
    });
  });
});

describe('pickBannerDrop()', () => {
  it('OPEN 이 있으면 그 중 openAt 이 가장 이른 것을 고른다', () => {
    // given
    const earlier = summary({ id: 1, status: 'OPEN', openAt: '2026-09-01T00:00:00+09:00' });
    const later = summary({ id: 2, status: 'OPEN', openAt: '2026-09-03T00:00:00+09:00' });
    const scheduled = summary({ id: 3, status: 'SCHEDULED', openAt: '2026-08-30T00:00:00+09:00' });

    // when & then
    expect(pickBannerDrop([later, scheduled, earlier])?.id).toBe(1);
  });

  it('OPEN 이 없으면 SCHEDULED 중 openAt 이 가장 이른 것을 고른다', () => {
    // given
    const earlier = summary({ id: 1, status: 'SCHEDULED', openAt: '2026-09-01T00:00:00+09:00' });
    const later = summary({ id: 2, status: 'SCHEDULED', openAt: '2026-09-03T00:00:00+09:00' });
    const closed = summary({ id: 3, status: 'CLOSED' });

    // when & then
    expect(pickBannerDrop([later, closed, earlier])?.id).toBe(1);
  });

  it('후보가 없으면 undefined 를 반환한다', () => {
    // given
    const soldOut = summary({ status: 'SOLD_OUT' });
    const closed = summary({ status: 'CLOSED' });

    // when & then
    expect(pickBannerDrop([soldOut, closed])).toBeUndefined();
  });
});
