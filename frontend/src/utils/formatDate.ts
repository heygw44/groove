import { toServerMs } from '@/utils/serverTime';

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

/** datetime-local input 의 min/value 속성은 로컬 시각 "YYYY-MM-DDTHH:mm" 형식을 요구한다. */
export function toDatetimeLocalValue(date: Date): string {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}T${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
}
