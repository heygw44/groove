// 공용 포매터 — 카탈로그/상세/장바구니/주문 뷰(#114~)가 공유한다.

const WON = new Intl.NumberFormat('ko-KR')

/** 원화 정수(원 단위)를 "12,000원" 형태로 포맷. */
export function formatWon(won) {
  return WON.format(won) + '원'
}
