// 공용 포매터 — 카탈로그/상세/장바구니/주문 뷰(#114~)가 공유한다.

const WON = new Intl.NumberFormat('ko-KR')
const DATE = new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })

/** 원화 정수(원 단위)를 "12,000원" 형태로 포맷. */
export function formatWon(won) {
  return WON.format(won) + '원'
}

/** ISO 8601/Instant 문자열을 "2025년 2월 15일" 형태로 포맷. 값이 없거나 파싱 불가면 빈 문자열. */
export function formatDate(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return DATE.format(d)
}
