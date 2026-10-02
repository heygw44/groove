import { useState } from 'react';

import { Button } from '@/components/common/Button';
import { Field } from '@/components/common/Field';
import { FormError } from '@/components/common/FormError';
import { Modal } from '@/components/common/Modal';
import { Textarea } from '@/components/common/Textarea';

const REASON_MAX_LENGTH = 200;

interface AdminReasonModalProps {
  title: string;
  description?: string;
  /** 대상 상품주문번호. 문장과 분리해 본문에 따로 보여준다. */
  productOrderNumber?: string;
  label: string;
  submitLabel: string;
  /** true 면 공백만 있는 사유로는 제출할 수 없다(클레임 거부). false 면 비워도 된다(판매취소). */
  required: boolean;
  pending: boolean;
  errorMessage?: string;
  onClose: () => void;
  onSubmit: (reason: string) => void;
}

/** 거부·판매취소처럼 사유 한 줄을 받는 관리자 모달. 열려 있는 동안만 마운트해 입력을 초기화한다. */
export function AdminReasonModal({
  title,
  description,
  productOrderNumber,
  label,
  submitLabel,
  required,
  pending,
  errorMessage,
  onClose,
  onSubmit,
}: AdminReasonModalProps) {
  const [reason, setReason] = useState('');
  const trimmed = reason.trim();

  return (
    <Modal
      open
      onClose={onClose}
      dismissible={!pending}
      title={title}
      description={description}
      size="sm"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={pending}>
            취소
          </Button>
          <Button
            variant="danger"
            onClick={() => onSubmit(trimmed)}
            disabled={required && trimmed === ''}
            loading={pending}
          >
            {submitLabel}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-3">
        {productOrderNumber && (
          <p className="text-sm text-content-muted">상품주문번호 {productOrderNumber}</p>
        )}
        <Field htmlFor="admin-reason" label={label} required={required}>
          <Textarea
            id="admin-reason"
            rows={3}
            maxLength={REASON_MAX_LENGTH}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
          />
        </Field>
        <FormError message={errorMessage} />
      </div>
    </Modal>
  );
}
