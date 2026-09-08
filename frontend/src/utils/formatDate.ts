import { getServerNowMs, toServerMs } from '@/utils/serverTime';

/**
 * 이미 오프셋이 붙은 문자열(Z, ±hh:mm)이나 Date 객체 전용이다. 서버가 내려주는
 * 오프셋 없는 LocalDateTime 문자열에는 formatServerDate/formatServerDateTime 을 쓴다
 * (그대로 new Date() 에 넘기면 브라우저 로컬 타임존으로 해석돼 값이 어긋난다).
 */
export function formatDate(value: string | Date): string {
  const date = typeof value === 'string' ? new Date(value) : value;
  return date.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
}

const pad2 = (value: number): string => String(value).padStart(2, '0');

/*
 * toLocaleString 의 ko-KR + hour12:false 조합은 자정을 "24:00" 으로 표기하는
 * ICU 버그가 있어(환경별로 다름) 직접 포매팅한다.
 */
export function formatDateTime(value: string | Date): string {
  const date = typeof value === 'string' ? new Date(value) : value;
  const datePart = formatDate(date);
  const time = `${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
  return `${datePart} ${time}`;
}

/** 서버 LocalDateTime 문자열(오프셋 없음) 전용. toServerMs 로 KST 해석한 뒤 formatDate 를 재사용한다. */
export function formatServerDate(iso: string): string {
  return formatDate(new Date(toServerMs(iso)));
}

/** 서버 LocalDateTime 문자열(오프셋 없음) 전용. toServerMs 로 KST 해석한 뒤 formatDateTime 을 재사용한다. */
export function formatServerDateTime(iso: string): string {
  return formatDateTime(new Date(toServerMs(iso)));
}

const MINUTE_MS = 60_000;
const HOUR_MS = 60 * MINUTE_MS;
const DAY_MS = 24 * HOUR_MS;

/**
 * 집계 시각(서버 LocalDateTime 문자열, 오프셋 없음) 을 "N분 전" 형태의 상대 시간으로
 * 표기한다. toServerMs 로 KST 해석한 값을 대상 시각으로 쓰고, 기준 시각은 getServerNowMs()
 * - 클라이언트 시계가 틀어져 있어도 값이 정확해야 하기 때문이다.
 * 시계 오차로 미래 시각이 나오면(음수 경과) "방금 전" 으로 접는다.
 */
export function formatRelativeFromNow(iso: string): string {
  const elapsedMs = getServerNowMs() - toServerMs(iso);

  if (elapsedMs < MINUTE_MS) {
    return '방금 전';
  }
  if (elapsedMs < HOUR_MS) {
    return `${Math.floor(elapsedMs / MINUTE_MS)}분 전`;
  }
  if (elapsedMs < DAY_MS) {
    return `${Math.floor(elapsedMs / HOUR_MS)}시간 전`;
  }
  return `${Math.floor(elapsedMs / DAY_MS)}일 전`;
}

/** datetime-local input 의 min/value 속성은 로컬 시각 "YYYY-MM-DDTHH:mm" 형식을 요구한다. */
export function toDatetimeLocalValue(date: Date): string {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}T${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
}
