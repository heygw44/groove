import type { RefundAccount } from '@/types/order';

export const EMPTY_REFUND_ACCOUNT: RefundAccount = {
  bankCode: '',
  accountNumber: '',
  holderName: '',
};

export const REFUND_ACCOUNT_INCOMPLETE_MESSAGE = '환불계좌 정보를 모두 입력해주세요.';

/** 세 필드가 모두 채워졌으면 예금주를 다듬어 돌려주고, 비어 있으면 undefined. */
export const normalizeRefundAccount = (account: RefundAccount): RefundAccount | undefined => {
  const holderName = account.holderName.trim();
  if (!account.bankCode || !account.accountNumber || !holderName) {
    return undefined;
  }
  return { bankCode: account.bankCode, accountNumber: account.accountNumber, holderName };
};
