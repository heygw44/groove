import { useState } from 'react';

import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';
import { Textarea } from '@/components/common/Textarea';

const REASON_MAX_LENGTH = 200;

interface OrderReturnDialogProps {
  open: boolean;
  onClose: () => void;
  onConfirm: (reason: string | undefined) => void;
  pending?: boolean;
}

export function OrderReturnDialog({
  open,
  onClose,
  onConfirm,
  pending = false,
}: OrderReturnDialogProps) {
  const [reason, setReason] = useState('');
  // 렌더 중 open 전환을 감지해 재오픈 시 이전 입력을 지운다(OrderCancelDialog 와 같은 방식).
  const [prevOpen, setPrevOpen] = useState(open);
  if (open !== prevOpen) {
    setPrevOpen(open);
    if (open) {
      setReason('');
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="반품을 요청하시겠습니까?"
      description="배송완료 후 7일 이내 상품만 반품할 수 있습니다. 수거가 끝나면 환불됩니다."
      size="sm"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={pending}>
            닫기
          </Button>
          <Button
            variant="danger"
            onClick={() => onConfirm(reason.trim() || undefined)}
            loading={pending}
          >
            반품 요청
          </Button>
        </>
      }
    >
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
