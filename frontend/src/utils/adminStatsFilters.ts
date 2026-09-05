import type { StatsPeriodParams } from '@/types/adminStats';

export type StatsPeriodPreset = '7d' | '30d';

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
const MS_PER_DAY = 86_400_000;
const MAX_PERIOD_SPAN_DAYS = 365;

const pad2 = (value: number): string => String(value).padStart(2, '0');

const toDateString = (date: Date): string =>
  `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;

/*
 * 형식(yyyy-MM-dd)과 실존 날짜 여부를 모두 확인한다. Date 는 2026-02-30 같은
 * 값을 3월로 롤오버해버리므로, 파싱한 날짜를 다시 같은 형식으로 찍어 원래
 * 문자열과 비교해야 존재하지 않는 날짜를 걸러낼 수 있다.
 */
const isValidDate = (value: string): boolean => {
  if (!DATE_PATTERN.test(value)) {
    return false;
  }
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) {
    return false;
  }
  return toDateString(date) === value;
};

const parseDate = (value: string | null): string | undefined =>
  value !== null && isValidDate(value) ? value : undefined;

/** from ≤ to 이고 기간이 365일 미만이어야 서버·클라이언트 양쪽 제약을 만족한다. */
export const isValidStatsPeriod = (from: string, to: string): boolean => {
  if (!isValidDate(from) || !isValidDate(to)) {
    return false;
  }
  const fromMs = new Date(`${from}T00:00:00`).getTime();
  const toMs = new Date(`${to}T00:00:00`).getTime();
  if (fromMs > toMs) {
    return false;
  }
  const spanDays = (toMs - fromMs) / MS_PER_DAY;
  return spanDays < MAX_PERIOD_SPAN_DAYS;
};

/** 잘못된 형식이거나 범위 제약을 어기면 두 값 모두 버려 페이지가 기본값(최근 30일)을 쓰게 한다. */
export const parseStatsPeriod = (searchParams: URLSearchParams): StatsPeriodParams => {
  const from = parseDate(searchParams.get('from'));
  const to = parseDate(searchParams.get('to'));
  const isInvalidRange = from !== undefined && to !== undefined && !isValidStatsPeriod(from, to);

  return {
    from: isInvalidRange ? undefined : from,
    to: isInvalidRange ? undefined : to,
  };
};

export const serializeStatsPeriod = (period: StatsPeriodParams): URLSearchParams => {
  const params = new URLSearchParams();

  if (period.from !== undefined) {
    params.set('from', period.from);
  }
  if (period.to !== undefined) {
    params.set('to', period.to);
  }

  return params;
};

/** 프리셋은 오늘을 포함한 최근 N 일이다(7d → 오늘 포함 7일, 30d → 오늘 포함 30일). */
export const resolvePresetPeriod = (
  preset: StatsPeriodPreset,
  today: Date,
): Required<StatsPeriodParams> => {
  const days = preset === '7d' ? 7 : 30;
  const from = new Date(today);
  from.setDate(from.getDate() - (days - 1));

  return {
    from: toDateString(from),
    to: toDateString(today),
  };
};

/** 매진까지 걸린 시간 표시용. 초 단위 미만은 다루지 않는다(매진 이벤트 특성상 충분). */
export const formatDuration = (seconds: number): string => {
  if (seconds < 60) {
    return `${seconds}초`;
  }
  if (seconds < 3600) {
    const minutes = Math.floor(seconds / 60);
    const remainSeconds = seconds % 60;
    return remainSeconds > 0 ? `${minutes}분 ${remainSeconds}초` : `${minutes}분`;
  }
  const hours = Math.floor(seconds / 3600);
  const remainMinutes = Math.floor((seconds % 3600) / 60);
  return remainMinutes > 0 ? `${hours}시간 ${remainMinutes}분` : `${hours}시간`;
};
