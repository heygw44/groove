import { useState } from 'react';

import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';
import { Textarea } from '@/components/common/Textarea';
import { RefundAccountFields } from '@/components/order/RefundAccountFields';
import type { RefundAccount } from '@/types/order';
import type { OrderPayment } from '@/types/payment';
import { requiresRefundAccount } from '@/utils/paymentStatus';
import {
  EMPTY_REFUND_ACCOUNT,
  normalizeRefundAccount,
  REFUND_ACCOUNT_INCOMPLETE_MESSAGE,
} from '@/utils/refundAccount';

const REASON_MAX_LENGTH = 200;

interface OrderCancelDialogProps {
  open: boolean;
  onClose: () => void;
  onConfirm: (reason: string | undefined, refundAccount?: RefundAccount) => void;
  pending?: boolean;
  /** 취소 대상 주문의 결제 정보. 가상계좌 여부·입금 여부에 따라 안내/입력을 다르게 보여준다. */
  payment?: OrderPayment;
  title?: string;
  description?: string;
  /** 확정 버튼 라벨. 상품 단위 취소·취소 요청처럼 문구가 다른 곳에서 바꿔 쓴다. */
  confirmLabel?: string;
}

export function OrderCancelDialog({
  open,
  onClose,
  onConfirm,
  pending = false,
  payment,
  title = '주문을 취소하시겠습니까?',
  description = '취소하면 되돌릴 수 없습니다.',
  confirmLabel = '주문취소',
}: OrderCancelDialogProps) {
  const [reason, setReason] = useState('');
  const [refundAccount, setRefundAccount] = useState<RefundAccount>(EMPTY_REFUND_ACCOUNT);
  const [refundAccountError, setRefundAccountError] = useState<string | undefined>();
  // 렌더 중 open 전환을 감지해 재오픈 시 이전 입력을 지운다(effect 대신 파생 상태로 처리).
  const [prevOpen, setPrevOpen] = useState(open);
  if (open !== prevOpen) {
    setPrevOpen(open);
    if (open) {
      setReason('');
      setRefundAccount(EMPTY_REFUND_ACCOUNT);
      setRefundAccountError(undefined);
    }
  }

  const isPaid = payment?.status === 'DONE' || payment?.status === 'PARTIAL_CANCELED';
  const isWaitingForDeposit = payment?.status === 'WAITING_FOR_DEPOSIT';
  const needsRefundAccount = requiresRefundAccount(payment);
  const showGenericRefundNotice = isPaid && !payment?.virtualAccount;

  const handleConfirm = () => {
    const trimmedReason = reason.trim() || undefined;
    if (!needsRefundAccount) {
      onConfirm(trimmedReason);
      return;
    }
    const normalized = normalizeRefundAccount(refundAccount);
    if (!normalized) {
      setRefundAccountError(REFUND_ACCOUNT_INCOMPLETE_MESSAGE);
      return;
    }
    onConfirm(trimmedReason, normalized);
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      dismissible={!pending}
      title={title}
      description={description}
      size="sm"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={pending}>
            닫기
          </Button>
          <Button variant="danger" onClick={handleConfirm} loading={pending}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      <div>
        {showGenericRefundNotice && (
          <p className="mb-3 rounded-md border border-line bg-surface-muted px-3 py-2.5 text-sm text-content-muted">
            결제금액은 결제수단으로 환불됩니다.
          </p>
        )}
        {isWaitingForDeposit && (
          <p className="mb-3 rounded-md border border-line bg-surface-muted px-3 py-2.5 text-sm text-content-muted">
            발급된 입금 계좌를 닫습니다. 아직 입금 전이라 환불할 금액은 없습니다.
          </p>
        )}
        {needsRefundAccount && (
          <RefundAccountFields
            idPrefix="cancel-refund"
            value={refundAccount}
            onChange={setRefundAccount}
            error={refundAccountError}
          />
        )}
        <label htmlFor="order-cancel-reason" className="mb-1.5 block text-sm text-content-muted">
          취소 사유 (선택)
        </label>
        <Textarea
          id="order-cancel-reason"
          value={reason}
          maxLength={REASON_MAX_LENGTH}
          rows={3}
          onChange={(event) => setReason(event.target.value)}
        />
        <p className="mt-1 text-right text-xs text-content-subtle">
          {reason.length}/{REASON_MAX_LENGTH}
        </p>
      </div>
    </Modal>
  );
}
