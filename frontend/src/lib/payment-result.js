// 결제 콜백 결과 값(?payment=) 판별 — 토스 successUrl/failUrl 콜백이 SPA 로 보존하는 두 상태(#308).
// 라우터 가드(non-component)와 usePaymentResultBanner 컴포저블이 같은 화이트리스트를 공유한다.

/** ?payment 값이 표시 가능한 결제 결과(success|fail)인지. */
export function isPaymentResult(v) {
  return v === 'success' || v === 'fail'
}
