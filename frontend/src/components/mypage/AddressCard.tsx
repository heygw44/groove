import { Button } from '@/components/common/Button';
import type { Address } from '@/types/member';

interface AddressCardProps {
  address: Address;
  onEdit: (address: Address) => void;
  onDelete: (address: Address) => void;
  onSetDefault: (address: Address) => void;
  disabled?: boolean;
}

export function AddressCard({
  address,
  onEdit,
  onDelete,
  onSetDefault,
  disabled = false,
}: AddressCardProps) {
  return (
    <article className="flex flex-col gap-4 rounded-lg border border-line bg-surface px-6 py-5 sm:flex-row sm:items-start sm:justify-between sm:gap-7">
      <div>
        <div className="mb-2 flex items-center gap-2.5">
          <strong className="text-[15px] font-bold">{address.recipientName}</strong>
          {address.isDefault && (
            <span className="inline-flex h-[21px] items-center rounded-sm bg-accent-soft px-1.5 text-[11.5px] font-bold text-accent-hover">
              기본 배송지
            </span>
          )}
        </div>
        <div className="flex flex-col gap-0.5 text-sm text-content-muted">
          <span className="text-content">
            {address.address1}
            {address.address2 ? ` ${address.address2}` : ''}
          </span>
          <span>
            {address.zipCode} · {address.phone}
          </span>
        </div>
      </div>

      <div className="flex shrink-0 items-center gap-1.5">
        {/* 이미 기본이면 숨긴다. 눌러도 아무 일이 없는 버튼은 두지 않는다. */}
        {!address.isDefault && (
          <Button
            variant="secondary"
            size="sm"
            onClick={() => onSetDefault(address)}
            disabled={disabled}
          >
            기본으로 지정
          </Button>
        )}
        <Button variant="secondary" size="sm" onClick={() => onEdit(address)} disabled={disabled}>
          수정
        </Button>
        <Button variant="ghost" size="sm" onClick={() => onDelete(address)} disabled={disabled}>
          삭제
        </Button>
      </div>
    </article>
  );
}
