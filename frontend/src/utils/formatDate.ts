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
