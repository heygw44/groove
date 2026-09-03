import { useState } from 'react';

import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';
import { Textarea } from '@/components/common/Textarea';

const REASON_MAX_LENGTH = 200;

interface OrderCancelDialogProps {
  open: boolean;
  onClose: () => void;
  onConfirm: (reason?: string) => void;
  pending?: boolean;
}

export function OrderCancelDialog({
  open,
  onClose,
  onConfirm,
  pending = false,
}: OrderCancelDialogProps) {
  const [reason, setReason] = useState('');
  // 렌더 중 open 전환을 감지해 재오픈 시 이전 입력을 지운다(effect 대신 파생 상태로 처리).
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
      title="주문을 취소할까요?"
      description="취소하면 재고가 복구되며 되돌릴 수 없습니다."
      size="sm"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={pending}>
            닫기
          </Button>
          <Button
            variant="danger"
            onClick={() => onConfirm(reason.trim() || undefined)}
            disabled={pending}
          >
            주문 취소
          </Button>
        </>
      }
    >
      <div>
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
