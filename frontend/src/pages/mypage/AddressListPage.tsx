import { useState } from 'react';

import { Button } from '@/components/common/Button';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { Spinner } from '@/components/common/Spinner';
import { useToast } from '@/components/common/Toast';
import { AddressCard } from '@/components/mypage/AddressCard';
import { AddressFormModal } from '@/components/mypage/AddressFormModal';
import { useDeleteAddress, useSetDefaultAddress } from '@/hooks/mutations/useAddressMutations';
import { useAddresses } from '@/hooks/queries/useAddresses';
import type { Address } from '@/types/member';
import { getErrorMessage } from '@/utils/apiError';

/** 백엔드 AddressService.MAX_ADDRESS_COUNT 와 같은 값. */
const MAX_ADDRESS_COUNT = 10;

export default function AddressListPage() {
  const [editing, setEditing] = useState<Address | undefined>(undefined);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [deleting, setDeleting] = useState<Address | undefined>(undefined);

  const { showToast } = useToast();
  const { data: addresses, isPending, isError } = useAddresses();
  const deleteMutation = useDeleteAddress();
  const setDefaultMutation = useSetDefaultAddress();

  const openCreate = () => {
    setEditing(undefined);
    setIsFormOpen(true);
  };

  const openEdit = (address: Address) => {
    setEditing(address);
    setIsFormOpen(true);
  };

  const handleSetDefault = (address: Address) => {
    setDefaultMutation.mutate(address.id, {
      onSuccess: () => showToast('success', '기본 배송지를 변경했습니다.'),
      onError: (error) => showToast('error', getErrorMessage(error)),
    });
  };

  const handleDelete = () => {
    if (!deleting) {
      return;
    }
    deleteMutation.mutate(deleting.id, {
      onSuccess: () => {
        showToast('success', '배송지를 삭제했습니다.');
        setDeleting(undefined);
      },
      onError: (error) => {
        setDeleting(undefined);
        showToast('error', getErrorMessage(error));
      },
    });
  };

  if (isPending) {
    return (
      <div className="flex min-h-64 items-center justify-center">
        <Spinner />
      </div>
    );
  }

  if (isError || !addresses) {
    return <p className="text-sm text-danger">배송지를 불러오지 못했습니다.</p>;
  }

  const isFull = addresses.length >= MAX_ADDRESS_COUNT;

  return (
    <div>
      <div className="mb-4 flex items-end justify-between gap-6">
        <div>
          <h2 className="text-[17px] font-bold tracking-tight">배송지</h2>
          <p className="mt-1.5 text-sm text-content-muted">
            등록된 배송지 {addresses.length} / {MAX_ADDRESS_COUNT}개
          </p>
        </div>
        {/* 서버도 막지만, 다 채운 뒤에 실패를 알려주는 건 늦다. */}
        <Button onClick={openCreate} disabled={isFull}>
          <svg
            width="15"
            height="15"
            viewBox="0 0 20 20"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            aria-hidden
          >
            <path d="M10 4.5v11" />
            <path d="M4.5 10h11" />
          </svg>
          배송지 추가
        </Button>
      </div>

      {addresses.length === 0 ? (
        <div className="rounded-lg border border-dashed border-line-strong px-6 py-12 text-center">
          <p className="text-sm text-content-muted">등록된 배송지가 없습니다.</p>
          <Button className="mt-4" onClick={openCreate}>
            첫 배송지 추가
          </Button>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {addresses.map((address) => (
            <AddressCard
              key={address.id}
              address={address}
              onEdit={openEdit}
              onDelete={setDeleting}
              onSetDefault={handleSetDefault}
              disabled={setDefaultMutation.isPending || deleteMutation.isPending}
            />
          ))}
        </div>
      )}

      <p className="mt-3.5 text-xs text-content-muted">
        {isFull
          ? `배송지는 최대 ${MAX_ADDRESS_COUNT}개까지 등록할 수 있습니다. 지우고 다시 추가하세요.`
          : '첫 배송지는 자동으로 기본 배송지가 됩니다. 기본 배송지를 삭제하면 남은 배송지 중 하나가 기본으로 지정됩니다.'}
      </p>

      <AddressFormModal open={isFormOpen} onClose={() => setIsFormOpen(false)} address={editing} />

      <ConfirmDialog
        open={Boolean(deleting)}
        onClose={() => setDeleting(undefined)}
        onConfirm={handleDelete}
        title="배송지를 삭제할까요?"
        description={
          deleting
            ? `'${deleting.recipientName} · ${deleting.address1}' 배송지가 목록에서 사라집니다. 되돌릴 수 없습니다.`
            : ''
        }
        pending={deleteMutation.isPending}
      />
    </div>
  );
}
