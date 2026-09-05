import type { ShippingAddress } from '@/types/order';

interface ShippingAddressCardProps {
  address: ShippingAddress;
}

export function ShippingAddressCard({ address }: ShippingAddressCardProps) {
  return (
    <div className="rounded-lg border border-line bg-surface px-5 py-4 text-sm">
      <div className="flex items-center gap-2">
        <strong className="font-bold text-content">{address.recipientName}</strong>
        <span className="text-content-muted">{address.phone}</span>
      </div>
      <p className="mt-1 text-content-muted">
        ({address.zipCode}) {address.address1}
        {address.address2 ? ` ${address.address2}` : ''}
      </p>
    </div>
  );
}
