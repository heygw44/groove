// 토스 위젯 SDK(@tosspayments/tosspayments-sdk) requestPayment 거부 코드 → 사용자 안내(한글).
// 원문 message 는 노출하지 않는다(호출부에서 console 로깅). 미매핑 코드는 일반 안내로 폴백.
const TOSS_ERROR_MESSAGE = {
  USER_CANCEL: '결제를 취소했습니다.',
  PAY_PROCESS_CANCELED: '결제가 취소되었습니다.',
  PAY_PROCESS_ABORTED: '결제가 중단되었습니다. 다시 시도해 주세요.',
  REJECT_CARD_COMPANY: '카드사에서 결제가 거절되었습니다. 다른 결제수단을 이용해 주세요.',
  INVALID_CARD_COMPANY: '카드 정보가 올바르지 않습니다.',
  INVALID_CARD_NUMBER: '카드 번호가 올바르지 않습니다.',
  NOT_SUPPORTED_METHOD: '지원하지 않는 결제수단입니다.',
}

/** 토스 SDK 에러 → 사용자 안내(한글). 미매핑 코드는 일반 안내로 폴백. */
export function tossErrorMessage(e) {
  return TOSS_ERROR_MESSAGE[e?.code] || '결제를 진행하지 못했습니다. 잠시 후 다시 시도해 주세요.'
}
