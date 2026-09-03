/* 원화는 소수를 쓰지 않는다. 서버 price 가 DECIMAL 이라 소수가 섞여 와도 원 단위로 반올림해 보여준다(Intl 기본 halfExpand). */
const priceFormatter = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 0 });

export function formatPrice(amount: number): string {
  return `${priceFormatter.format(amount)}원`;
}
