import { useState } from 'react';

import { Badge } from '@/components/common/Badge';
import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';
import { AddressFormModal } from '@/components/mypage/AddressFormModal';
import type { Address } from '@/types/member';

interface ShippingAddressSectionProps {
  addresses: Address[];
  selectedId?: number;
  onSelect: (id: number) => void;
}

function AddressLine({ address }: { address: Address }) {
  return (
    <div className="flex flex-col gap-0.5 text-sm">
      <div className="flex items-center gap-2">
        <strong className="font-bold text-content">{address.recipientName}</strong>
        <span className="text-content-muted">{address.phone}</span>
        {address.isDefault && <Badge variant="accent">기본</Badge>}
      </div>
      <p className="text-content-muted">
        ({address.zipCode}) {address.address1}
        {address.address2 ? ` ${address.address2}` : ''}
      </p>
    </div>
  );
}

export function ShippingAddressSection({
  addresses,
  selectedId,
  onSelect,
}: ShippingAddressSectionProps) {
  const [isPickerOpen, setIsPickerOpen] = useState(false);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [pendingId, setPendingId] = useState<number | undefined>(undefined);

  const selectedAddress = addresses.find((address) => address.id === selectedId);

  const openPicker = () => {
    setPendingId(selectedId);
    setIsPickerOpen(true);
  };

  const confirmPicker = () => {
    if (pendingId !== undefined) {
      onSelect(pendingId);
    }
    setIsPickerOpen(false);
  };

  if (addresses.length === 0) {
    return (
      <div>
        <h2 className="mb-3 text-base font-bold">배송지</h2>
        <div className="rounded-lg border border-dashed border-line-strong px-6 py-8 text-center">
          <p className="text-sm text-content-muted">등록된 배송지가 없습니다</p>
          <Button className="mt-4" onClick={() => setIsFormOpen(true)}>
            배송지 등록
          </Button>
        </div>

        <AddressFormModal open={isFormOpen} onClose={() => setIsFormOpen(false)} />
      </div>
    );
  }

  return (
    <div>
      <h2 className="mb-3 text-base font-bold">배송지</h2>
      <div className="flex items-start justify-between gap-4 rounded-lg border border-line bg-surface px-5 py-4">
        {selectedAddress ? (
          <AddressLine address={selectedAddress} />
        ) : (
          <p className="text-sm text-content-muted">배송지를 선택해주세요.</p>
        )}
        <Button variant="secondary" size="sm" onClick={openPicker}>
          변경
        </Button>
      </div>

      <Modal
        open={isPickerOpen}
        onClose={() => setIsPickerOpen(false)}
        title="배송지 선택"
        footer={
          <>
            <Button variant="secondary" onClick={() => setIsPickerOpen(false)}>
              취소
            </Button>
            <Button onClick={confirmPicker} disabled={pendingId === undefined}>
              선택
            </Button>
          </>
        }
      >
        <div className="flex flex-col gap-2">
          {addresses.map((address) => (
            <label
              key={address.id}
              className="flex cursor-pointer items-start gap-3 rounded-md border border-line px-4 py-3 has-[:checked]:border-content"
            >
              <input
                type="radio"
                name="shipping-address"
                className="mt-1 h-4 w-4 accent-content"
                checked={pendingId === address.id}
                onChange={() => setPendingId(address.id)}
              />
              <AddressLine address={address} />
            </label>
          ))}
        </div>
      </Modal>
    </div>
  );
}
