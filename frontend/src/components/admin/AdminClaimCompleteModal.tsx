import { useState } from 'react';

import { Button } from '@/components/common/Button';
import { FormError } from '@/components/common/FormError';
import { Modal } from '@/components/common/Modal';

interface AdminClaimCompleteModalProps {
  productOrderNumber: string;
  pending: boolean;
  errorMessage?: string;
  onClose: () => void;
  onSubmit: (restock: boolean) => void;
}

/** 반품 수거 완료. 환불이 함께 실행되므로 재입고 여부를 명시적으로 고르게 한다. */
export function AdminClaimCompleteModal({
  productOrderNumber,
  pending,
  errorMessage,
  onClose,
  onSubmit,
}: AdminClaimCompleteModalProps) {
  const [restock, setRestock] = useState(true);

  return (
    <Modal
      open
      onClose={onClose}
      title="반품 수거 완료"
      description={`${productOrderNumber} 수거를 완료하고 환불합니다.`}
      size="sm"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={pending}>
            취소
          </Button>
          <Button onClick={() => onSubmit(restock)} loading={pending}>
            수거 완료
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-3">
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={restock}
            onChange={(event) => setRestock(event.target.checked)}
          />
          반품 상품을 재고로 되돌립니다(재입고)
        </label>
        <FormError message={errorMessage} />
      </div>
    </Modal>
  );
}
