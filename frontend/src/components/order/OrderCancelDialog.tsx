import { useState } from 'react';

import { Button } from '@/components/common/Button';
import { Input } from '@/components/common/Input';
import { Modal } from '@/components/common/Modal';
import { Select } from '@/components/common/Select';
import { Textarea } from '@/components/common/Textarea';
import { BANK_OPTIONS } from '@/constants/banks';
import type { RefundAccount } from '@/types/order';
import type { OrderPayment } from '@/types/payment';

const REASON_MAX_LENGTH = 200;

interface OrderCancelDialogProps {
  open: boolean;
  onClose: () => void;
  onConfirm: (reason: string | undefined, refundAccount?: RefundAccount) => void;
  pending?: boolean;
  /** 취소 대상 주문의 결제 정보. 가상계좌 여부·입금 여부에 따라 안내/입력을 다르게 보여준다. */
  payment?: OrderPayment;
}

const EMPTY_REFUND_ACCOUNT: RefundAccount = { bankCode: '', accountNumber: '', holderName: '' };

export function OrderCancelDialog({
  open,
  onClose,
  onConfirm,
  pending = false,
  payment,
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

  const isVirtualAccount = Boolean(payment?.virtualAccount);
  const isPaid = payment?.status === 'DONE';
  const isWaitingForDeposit = payment?.status === 'WAITING_FOR_DEPOSIT';
  const requiresRefundAccount = isVirtualAccount && isPaid;
  const showGenericRefundNotice = isPaid && !isVirtualAccount;

  const handleConfirm = () => {
    if (requiresRefundAccount) {
      const { bankCode, accountNumber, holderName } = refundAccount;
      if (!bankCode || !accountNumber || !holderName.trim()) {
        setRefundAccountError('환불계좌 정보를 모두 입력해주세요.');
        return;
      }
      onConfirm(reason.trim() || undefined, {
        bankCode,
        accountNumber,
        holderName: holderName.trim(),
      });
      return;
    }
    onConfirm(reason.trim() || undefined);
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="주문을 취소하시겠습니까?"
      description="취소하면 되돌릴 수 없습니다."
      size="sm"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={pending}>
            닫기
          </Button>
          <Button variant="danger" onClick={handleConfirm} loading={pending}>
            주문 취소
          </Button>
        </>
      }
    >
      <div>
        {showGenericRefundNotice && (
          <p className="mb-3 rounded-md border border-line bg-surface-muted px-3 py-2.5 text-sm text-content-muted">
            결제 금액은 결제 수단으로 환불됩니다.
          </p>
        )}
        {isWaitingForDeposit && (
          <p className="mb-3 rounded-md border border-line bg-surface-muted px-3 py-2.5 text-sm text-content-muted">
            발급된 입금 계좌를 닫습니다. 아직 입금 전이라 환불할 금액은 없습니다.
          </p>
        )}
        {requiresRefundAccount && (
          <div className="mb-4 flex flex-col gap-3">
            <p className="text-sm text-content-muted">
              무통장입금은 환불계좌로 직접 환불됩니다. 계좌 정보를 입력해주세요.
            </p>
            <div>
              <label htmlFor="refund-bank" className="mb-1.5 block text-sm text-content-muted">
                은행
              </label>
              <Select
                id="refund-bank"
                value={refundAccount.bankCode}
                onChange={(event) =>
                  setRefundAccount((prev) => ({ ...prev, bankCode: event.target.value }))
                }
              >
                <option value="">은행 선택</option>
                {BANK_OPTIONS.map((bank) => (
                  <option key={bank.code} value={bank.code}>
                    {bank.name}
                  </option>
                ))}
              </Select>
            </div>
            <div>
              <label
                htmlFor="refund-account-number"
                className="mb-1.5 block text-sm text-content-muted"
              >
                계좌번호
              </label>
              <Input
                id="refund-account-number"
                inputMode="numeric"
                value={refundAccount.accountNumber}
                onChange={(event) =>
                  setRefundAccount((prev) => ({
                    ...prev,
                    accountNumber: event.target.value.replace(/\D/g, ''),
                  }))
                }
              />
            </div>
            <div>
              <label
                htmlFor="refund-holder-name"
                className="mb-1.5 block text-sm text-content-muted"
              >
                예금주
              </label>
              <Input
                id="refund-holder-name"
                value={refundAccount.holderName}
                onChange={(event) =>
                  setRefundAccount((prev) => ({ ...prev, holderName: event.target.value }))
                }
              />
            </div>
            {refundAccountError && <p className="text-sm text-danger">{refundAccountError}</p>}
          </div>
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
