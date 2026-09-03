import { Link } from 'react-router-dom';

import { Badge } from '@/components/common/Badge';
import { Button } from '@/components/common/Button';
import { QuantitySelector } from '@/components/product/QuantitySelector';
import type { CartItem } from '@/types/cart';
import { isCartItemSoldOut, maxSelectableQuantity } from '@/utils/cart';
import { formatPrice } from '@/utils/formatPrice';

interface CartItemRowProps {
  item: CartItem;
  selected: boolean;
  onSelectChange: (checked: boolean) => void;
  onQuantityChange: (quantity: number) => void;
  onRemove: () => void;
  disabled?: boolean;
}

function CartItemThumbnail({ url, soldOut }: { url?: string; soldOut: boolean }) {
  return (
    <div
      className={`h-20 w-20 shrink-0 overflow-hidden rounded-md bg-surface-muted ${soldOut ? 'opacity-60' : ''}`}
    >
      {url ? (
        <img src={url} alt="" loading="lazy" className="h-full w-full object-cover" />
      ) : (
        <div className="flex h-full w-full items-center justify-center text-content-subtle">
          <svg
            width="28"
            height="28"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.4"
            aria-hidden
          >
            <circle cx="12" cy="12" r="9" />
            <circle cx="12" cy="12" r="3" />
          </svg>
        </div>
      )}
    </div>
  );
}

export function CartItemRow({
  item,
  selected,
  onSelectChange,
  onQuantityChange,
  onRemove,
  disabled = false,
}: CartItemRowProps) {
  const soldOut = isCartItemSoldOut(item);

  const handleQuantityChange = (quantity: number) => {
    // 입력칸이 비어 있으면 QuantitySelector 가 0을 넘긴다 - 유효하지 않거나 변화가 없는 값은 무시하고 onBlur 클램프가 최종값을 보낸다.
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > maxSelectableQuantity(item)) {
      return;
    }
    if (quantity === item.quantity) {
      return;
    }
    onQuantityChange(quantity);
  };

  return (
    <div className="flex items-center gap-4 border-b border-line py-4">
      <input
        type="checkbox"
        aria-label={`${item.title} 선택`}
        checked={selected && !soldOut}
        disabled={soldOut || disabled}
        onChange={(event) => onSelectChange(event.target.checked)}
        className="h-4 w-4 shrink-0 accent-content"
      />

      <CartItemThumbnail url={item.thumbnailUrl} soldOut={soldOut} />

      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <Link
            to={`/products/${item.productId}`}
            className="truncate text-sm font-medium text-content no-underline hover:underline"
          >
            {item.title}
          </Link>
          {soldOut && <Badge variant="danger">품절</Badge>}
        </div>
        <p className="mt-0.5 text-xs text-content-muted">{item.artistName}</p>
        <p className="mt-1 text-sm text-content-muted">{formatPrice(item.price)}</p>
      </div>

      <QuantitySelector
        value={item.quantity}
        onChange={handleQuantityChange}
        max={maxSelectableQuantity(item)}
        disabled={soldOut || disabled}
      />

      <p className="w-24 shrink-0 text-right text-sm font-bold">{formatPrice(item.subtotal)}</p>

      <Button variant="ghost" size="sm" onClick={onRemove} disabled={disabled}>
        삭제
      </Button>
    </div>
  );
}
