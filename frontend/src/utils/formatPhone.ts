const SEOUL_AREA_CODE = '02';
const MAX_DIGITS = 11;
const SEOUL_MAX_DIGITS = 10;
/* 국번 뒤가 7자리 이하면 3-4, 8자리면 4-4 로 끊는다. */
const SHORT_MIDDLE_MAX_REST = 7;

/**
 * 입력 중인 전화번호에 하이픈을 채운다. 저장 형식(010-1234-5678)은 백엔드 정규식과 같다.
 * 서울(02)은 국번이 2자리라 따로 끊는다.
 */
export function formatPhone(value: string): string {
  const allDigits = value.replace(/\D/g, '');
  const isSeoul = allDigits.startsWith(SEOUL_AREA_CODE);
  const digits = allDigits.slice(0, isSeoul ? SEOUL_MAX_DIGITS : MAX_DIGITS);
  const areaLength = isSeoul ? SEOUL_AREA_CODE.length : 3;

  if (digits.length <= areaLength) {
    return digits;
  }

  const area = digits.slice(0, areaLength);
  const rest = digits.slice(areaLength);
  if (rest.length <= 3) {
    return `${area}-${rest}`;
  }

  const middleLength = rest.length <= SHORT_MIDDLE_MAX_REST ? 3 : 4;
  return `${area}-${rest.slice(0, middleLength)}-${rest.slice(middleLength)}`;
}
