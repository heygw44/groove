import { useState } from 'react';

import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';
import { Textarea } from '@/components/common/Textarea';
import type { AdminMemberChangeableStatus } from '@/types/adminMember';

const REASON_MAX_LENGTH = 200;

interface MemberStatusChangeDialogProps {
  open: boolean;
  nextStatus: AdminMemberChangeableStatus;
  pending?: boolean;
  onClose: () => void;
  onConfirm: (reason?: string) => void;
}

const DIALOG_TEXT: Record<
  AdminMemberChangeableStatus,
  { title: string; description: string; confirmLabel: string }
> = {
  SUSPENDED: {
    title: '이 회원을 정지할까요?',
    description: '정지하면 로그인이 막히고 진행 중인 세션도 즉시 끊깁니다.',
    confirmLabel: '정지',
  },
  ACTIVE: {
    title: '이 회원의 정지를 해제할까요?',
    description: '해제하면 즉시 다시 로그인할 수 있습니다.',
    confirmLabel: '해제',
  },
};

export function MemberStatusChangeDialog({
  open,
  nextStatus,
  pending = false,
  onClose,
  onConfirm,
}: MemberStatusChangeDialogProps) {
  const [reason, setReason] = useState('');
  // 렌더 중 open 전환을 감지해 재오픈 시 이전 입력을 지운다(effect 대신 파생 상태로 처리, OrderCancelDialog 와 동일 패턴).
  const [prevOpen, setPrevOpen] = useState(open);
  if (open !== prevOpen) {
    setPrevOpen(open);
    if (open) {
      setReason('');
    }
  }

  const text = DIALOG_TEXT[nextStatus];

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={text.title}
      description={text.description}
      size="sm"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={pending}>
            닫기
          </Button>
          <Button
            variant={nextStatus === 'SUSPENDED' ? 'danger' : 'primary'}
            onClick={() => onConfirm(reason.trim() || undefined)}
            disabled={pending}
          >
            {text.confirmLabel}
          </Button>
        </>
      }
    >
      <div>
        <label
          htmlFor="member-status-change-reason"
          className="mb-1.5 block text-sm text-content-muted"
        >
          사유 (선택)
        </label>
        <Textarea
          id="member-status-change-reason"
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
