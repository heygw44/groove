import { describe, expect, it } from 'vitest';

import {
  formatDuration,
  isValidStatsPeriod,
  parseStatsPeriod,
  resolvePresetPeriod,
  serializeStatsPeriod,
} from '@/utils/adminStatsFilters';

describe('parseStatsPeriod()', () => {
  it('유효한 from/to 를 그대로 반환한다', () => {
    // given
    const searchParams = new URLSearchParams({ from: '2026-08-01', to: '2026-08-31' });

    // when
    const result = parseStatsPeriod(searchParams);

    // then
    expect(result).toEqual({ from: '2026-08-01', to: '2026-08-31' });
  });

  it('파라미터가 없으면 둘 다 undefined 를 반환한다', () => {
    // given
    const searchParams = new URLSearchParams();

    // when
    const result = parseStatsPeriod(searchParams);

    // then
    expect(result).toEqual({ from: undefined, to: undefined });
  });

  it('형식에 맞지 않는 날짜는 무시한다', () => {
    // given
    const searchParams = new URLSearchParams({ from: '2026/08/01', to: '2026-08-31' });

    // when
    const result = parseStatsPeriod(searchParams);

    // then
    expect(result).toEqual({ from: undefined, to: '2026-08-31' });
  });

  it('존재하지 않는 날짜는 무시한다', () => {
    // given
    const searchParams = new URLSearchParams({ from: '2026-02-30', to: '2026-08-31' });

    // when
    const result = parseStatsPeriod(searchParams);

    // then
    expect(result).toEqual({ from: undefined, to: '2026-08-31' });
  });

  it('from 이 to 보다 뒤면 둘 다 버린다', () => {
    // given
    const searchParams = new URLSearchParams({ from: '2026-08-31', to: '2026-08-01' });

    // when
    const result = parseStatsPeriod(searchParams);

    // then
    expect(result).toEqual({ from: undefined, to: undefined });
  });

  it('365일 이상 범위는 둘 다 버린다', () => {
    // given
    const searchParams = new URLSearchParams({ from: '2025-01-01', to: '2026-01-02' });

    // when
    const result = parseStatsPeriod(searchParams);

    // then
    expect(result).toEqual({ from: undefined, to: undefined });
  });
});

describe('serializeStatsPeriod()', () => {
  it('from/to 가 있으면 쿼리 문자열에 담는다', () => {
    // given & when
    const params = serializeStatsPeriod({ from: '2026-08-01', to: '2026-08-31' });

    // then
    expect(params.toString()).toBe('from=2026-08-01&to=2026-08-31');
  });

  it('값이 없으면 빈 파라미터를 반환한다', () => {
    // given & when
    const params = serializeStatsPeriod({});

    // then
    expect(params.toString()).toBe('');
  });
});

describe('resolvePresetPeriod()', () => {
  it('7d 는 오늘을 포함한 7일 범위를 반환한다', () => {
    // given
    const today = new Date('2026-08-31T12:00:00');

    // when
    const result = resolvePresetPeriod('7d', today);

    // then
    expect(result).toEqual({ from: '2026-08-25', to: '2026-08-31' });
  });

  it('30d 는 오늘을 포함한 30일 범위를 반환한다', () => {
    // given
    const today = new Date('2026-08-31T12:00:00');

    // when
    const result = resolvePresetPeriod('30d', today);

    // then
    expect(result).toEqual({ from: '2026-08-02', to: '2026-08-31' });
  });

  it('월 경계를 넘는 범위도 올바르게 계산한다', () => {
    // given
    const today = new Date('2026-09-03T00:00:00');

    // when
    const result = resolvePresetPeriod('7d', today);

    // then
    expect(result).toEqual({ from: '2026-08-28', to: '2026-09-03' });
  });
});

describe('isValidStatsPeriod()', () => {
  it.each<[string, string, boolean]>([
    ['2026-08-01', '2026-08-31', true],
    ['2026-08-01', '2026-08-01', true],
    ['2026-08-31', '2026-08-01', false],
    ['2025-01-01', '2026-01-02', false],
  ])('from=%s, to=%s 는 유효성이 %s 이다', (from, to, expected) => {
    // given & when
    const result = isValidStatsPeriod(from, to);

    // then
    expect(result).toBe(expected);
  });
});

describe('formatDuration()', () => {
  it.each<[number, string]>([
    [45, '45초'],
    [60, '1분'],
    [221, '3분 41초'],
    [3600, '1시간'],
    [3720, '1시간 2분'],
  ])('%d초는 "%s"로 표시한다', (seconds, expected) => {
    // given & when
    const result = formatDuration(seconds);

    // then
    expect(result).toBe(expected);
  });
});
