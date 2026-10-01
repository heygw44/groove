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

interface OrderReturnDialogProps {
  open: boolean;
  onClose: () => void;
  onConfirm: (reason: string | undefined, refundAccount?: RefundAccount) => void;
  pending?: boolean;
  /** 가상계좌 결제면 환불계좌를 받기 위해 필요하다. */
  payment?: OrderPayment;
}

export function OrderReturnDialog({
  open,
  onClose,
  onConfirm,
  pending = false,
  payment,
}: OrderReturnDialogProps) {
  const [reason, setReason] = useState('');
  const [refundAccount, setRefundAccount] = useState<RefundAccount>(EMPTY_REFUND_ACCOUNT);
  const [refundAccountError, setRefundAccountError] = useState<string | undefined>();
  // 렌더 중 open 전환을 감지해 재오픈 시 이전 입력을 지운다(OrderCancelDialog 와 같은 방식).
  const [prevOpen, setPrevOpen] = useState(open);
  if (open !== prevOpen) {
    setPrevOpen(open);
    if (open) {
      setReason('');
      setRefundAccount(EMPTY_REFUND_ACCOUNT);
      setRefundAccountError(undefined);
    }
  }

  const needsRefundAccount = requiresRefundAccount(payment);

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
      title="반품을 요청하시겠습니까?"
      description="배송완료 후 7일 이내 상품만 반품할 수 있습니다. 수거가 끝나면 환불됩니다."
      size="sm"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={pending}>
            닫기
          </Button>
          <Button variant="danger" onClick={handleConfirm} loading={pending}>
            반품요청
          </Button>
        </>
      }
    >
      {needsRefundAccount && (
        <RefundAccountFields
          idPrefix="return-refund"
          value={refundAccount}
          onChange={setRefundAccount}
          error={refundAccountError}
        />
      )}
      <label htmlFor="order-return-reason" className="mb-1.5 block text-sm text-content-muted">
        반품 사유 (선택)
      </label>
      <Textarea
        id="order-return-reason"
        value={reason}
        maxLength={REASON_MAX_LENGTH}
        rows={3}
        onChange={(event) => setReason(event.target.value)}
      />
      <p className="mt-1 text-right text-xs text-content-subtle">
        {reason.length}/{REASON_MAX_LENGTH}
      </p>
    </Modal>
  );
}
