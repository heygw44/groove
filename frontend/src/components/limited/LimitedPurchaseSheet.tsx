import { useState } from 'react';

import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';
import { Spinner } from '@/components/common/Spinner';
import { AddressFormModal } from '@/components/mypage/AddressFormModal';
import { AddressLine } from '@/components/order/ShippingAddressSection';
import { useAddresses } from '@/hooks/queries/useAddresses';
import type { LimitedDropDetail } from '@/types/limitedDrop';
import { formatPrice } from '@/utils/formatPrice';

interface LimitedPurchaseSheetProps {
  open: boolean;
  onClose: () => void;
  drop: LimitedDropDetail;
  pending: boolean;
  onConfirm: (addressId: number) => void;
}

export function LimitedPurchaseSheet({
  open,
  onClose,
  drop,
  pending,
  onConfirm,
}: LimitedPurchaseSheetProps) {
  const { data: addresses, isPending: isAddressesPending } = useAddresses();
  const [selectedId, setSelectedId] = useState<number | undefined>(undefined);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [wasOpen, setWasOpen] = useState(open);

  // 시트를 열 때마다 이전에 고른 배송지를 지워 기본 배송지(또는 첫 배송지)로 되돌아가게 한다.
  // 렌더 중 상태를 조정하는 방식(useEffect 아님) - https://react.dev/learn/you-might-not-need-an-effect
  if (open !== wasOpen) {
    setWasOpen(open);
    if (open) {
      setSelectedId(undefined);
    }
  }

  const defaultId = addresses?.find((address) => address.isDefault)?.id ?? addresses?.[0]?.id;
  const effectiveSelectedId = selectedId ?? defaultId;

  const handleConfirm = () => {
    if (effectiveSelectedId !== undefined) {
      onConfirm(effectiveSelectedId);
    }
  };

  let addressContent;
  if (isAddressesPending) {
    addressContent = (
      <div className="flex justify-center py-6">
        <Spinner />
      </div>
    );
  } else if (addresses && addresses.length > 0) {
    addressContent = (
      <div className="flex flex-col gap-2">
        {addresses.map((address) => (
          <label
            key={address.id}
            className="flex cursor-pointer items-start gap-3 rounded-md border border-line px-4 py-3 has-[:checked]:border-content"
          >
            <input
              type="radio"
              name="limited-purchase-address"
              className="mt-1 h-4 w-4 accent-content"
              checked={effectiveSelectedId === address.id}
              onChange={() => setSelectedId(address.id)}
            />
            <AddressLine address={address} />
          </label>
        ))}
      </div>
    );
  } else {
    addressContent = (
      <div className="rounded-lg border border-dashed border-line-strong px-6 py-8 text-center">
        <p className="text-sm text-content-muted">등록된 배송지가 없습니다</p>
        <Button className="mt-4" onClick={() => setIsFormOpen(true)}>
          배송지 등록
        </Button>
      </div>
    );
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="배송지 선택"
      placement="bottom"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={pending}>
            취소
          </Button>
          <Button onClick={handleConfirm} disabled={pending || effectiveSelectedId === undefined}>
            구매 확정
          </Button>
        </>
      }
    >
      <div className="mb-4 flex items-center justify-between rounded-md bg-surface-muted px-4 py-3">
        <span className="text-sm font-medium">{drop.product.title}</span>
        <span className="text-sm font-bold">{formatPrice(drop.product.price)}</span>
      </div>

      {addressContent}

      <AddressFormModal open={isFormOpen} onClose={() => setIsFormOpen(false)} />
    </Modal>
  );
}
