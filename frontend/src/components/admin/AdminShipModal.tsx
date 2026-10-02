import { useState } from 'react';

import { Button } from '@/components/common/Button';
import { Field } from '@/components/common/Field';
import { FormError } from '@/components/common/FormError';
import { Input } from '@/components/common/Input';
import { Modal } from '@/components/common/Modal';
import { Select } from '@/components/common/Select';
import { COURIERS } from '@/constants/couriers';
import { useShipAdminOrderItems } from '@/hooks/mutations/useAdminOrderMutations';
import type { AdminOrderItemBulkResult, AdminOrderItemSummary } from '@/types/adminOrder';
import type { CourierCode } from '@/types/order';
import { getErrorMessage } from '@/utils/apiError';

const TRACKING_NUMBER_MAX_LENGTH = 50;
const COURIER_CODES = Object.keys(COURIERS) as CourierCode[];

interface AdminShipModalProps {
  /** 발송 가능한(배송준비) 상품주문만 넘긴다. 열려 있는 동안만 마운트해 입력 상태를 초기화한다. */
  items: AdminOrderItemSummary[];
  onClose: () => void;
  onCompleted: (result: AdminOrderItemBulkResult) => void;
}

export function AdminShipModal({ items, onClose, onCompleted }: AdminShipModalProps) {
  const [courierCode, setCourierCode] = useState<CourierCode>(COURIER_CODES[0]);
  const [trackingNumbers, setTrackingNumbers] = useState<Record<number, string>>({});
  const shipMutation = useShipAdminOrderItems();

  const isComplete = items.every((item) => (trackingNumbers[item.id] ?? '').trim() !== '');

  const handleSubmit = () => {
    shipMutation.mutate(
      {
        items: items.map((item) => ({
          orderItemId: item.id,
          courierCode,
          trackingNumber: (trackingNumbers[item.id] ?? '').trim(),
        })),
      },
      { onSuccess: onCompleted },
    );
  };

  return (
    <Modal
      open
      onClose={onClose}
      dismissible={!shipMutation.isPending}
      title="발송처리"
      description={`선택한 ${items.length}건에 택배사와 송장번호를 입력해주세요.`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={shipMutation.isPending}>
            취소
          </Button>
          <Button onClick={handleSubmit} disabled={!isComplete} loading={shipMutation.isPending}>
            발송처리
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <Field htmlFor="ship-courier" label="택배사" required>
          <Select
            id="ship-courier"
            value={courierCode}
            onChange={(event) => setCourierCode(event.target.value as CourierCode)}
          >
            {COURIER_CODES.map((code) => (
              <option key={code} value={code}>
                {COURIERS[code].name}
              </option>
            ))}
          </Select>
        </Field>

        {items.map((item) => (
          <Field
            key={item.id}
            htmlFor={`ship-tracking-${item.id}`}
            label={`송장번호 · ${item.productOrderNumber} ${item.productName}`}
            required
          >
            <Input
              id={`ship-tracking-${item.id}`}
              value={trackingNumbers[item.id] ?? ''}
              maxLength={TRACKING_NUMBER_MAX_LENGTH}
              onChange={(event) =>
                setTrackingNumbers((prev) => ({ ...prev, [item.id]: event.target.value }))
              }
            />
          </Field>
        ))}

        <FormError
          message={shipMutation.isError ? getErrorMessage(shipMutation.error) : undefined}
        />
      </div>
    </Modal>
  );
}
