/** 토스 결제위젯이 돌려주는 실패 코드 → 사용자 문구. 매핑에 없으면 토스 message 를 그대로 보여준다. */
export const TOSS_FAIL_MESSAGES: Record<string, string> = {
  PAY_PROCESS_CANCELED: '사용자가 결제를 취소했습니다.',
  PAY_PROCESS_ABORTED: '결제 진행 중 오류가 발생했습니다.',
  REJECT_CARD_COMPANY: '카드사에서 결제를 거절했습니다.',
  USER_CANCEL: '결제창을 닫았습니다.',
};
