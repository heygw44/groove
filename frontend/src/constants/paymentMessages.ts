/** 토스 결제위젯이 돌려주는 실패 코드 → 사용자 문구. 매핑에 없으면 토스 message 를 그대로 보여준다. */
export const TOSS_FAIL_MESSAGES: Record<string, string> = {
  PAY_PROCESS_CANCELED: '결제를 취소했습니다.',
  PAY_PROCESS_ABORTED: '결제 진행 중 오류가 발생했습니다.',
  REJECT_CARD_COMPANY: '카드사에서 결제를 거절했습니다.',
  USER_CANCEL: '결제창을 닫아 취소되었습니다.',
};

/**
 * 결제 승인(confirm) API 실패 코드 → 이 화면 전용 문구. 전역 ERROR_MESSAGES 는 다른 화면도
 * 공유하니 여기서만 덮어쓴다.
 */
export const PAYMENT_CONFIRM_ERROR_MESSAGES: Record<string, string> = {
  ORDER_EXPIRED: '주문 시간이 지나 결제가 자동 취소됐습니다.',
};
