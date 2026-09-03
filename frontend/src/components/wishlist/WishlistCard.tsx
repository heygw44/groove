import { Link } from 'react-router-dom';

import { Badge } from '@/components/common/Badge';
import { Button } from '@/components/common/Button';
import type { WishlistItem } from '@/types/wishlist';
import { formatPrice } from '@/utils/formatPrice';

interface WishlistCardProps {
  item: WishlistItem;
  onRemove: () => void;
  onAddToCart: () => void;
  addingToCart?: boolean;
}

function WishlistThumbnail({ url, soldOut }: { url?: string; soldOut: boolean }) {
  return (
    <div
      className={`aspect-square overflow-hidden rounded-md bg-surface-muted ${soldOut ? 'opacity-60' : ''}`}
    >
      {url ? (
        <img src={url} alt="" loading="lazy" className="h-full w-full object-cover" />
      ) : (
        <div className="flex h-full w-full items-center justify-center text-content-subtle">
          <svg
            width="40"
            height="40"
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

export function WishlistCard({
  item,
  onRemove,
  onAddToCart,
  addingToCart = false,
}: WishlistCardProps) {
  const soldOut = item.productStatus === 'SOLD_OUT' || item.stockQuantity <= 0;

  return (
    <div>
      <Link to={`/products/${item.productId}`} className="block">
        <WishlistThumbnail url={item.thumbnailUrl} soldOut={soldOut} />
      </Link>

      <div className="mt-2.5 flex items-center gap-2">
        <Link
          to={`/products/${item.productId}`}
          className="line-clamp-2 text-sm font-medium text-content no-underline hover:underline"
        >
          {item.title}
        </Link>
        {soldOut && <Badge variant="danger">품절</Badge>}
      </div>
      <p className="mt-0.5 text-xs text-content-muted">{item.artistName}</p>
      <p className="mt-1 text-sm font-bold">{formatPrice(item.price)}</p>

      <div className="mt-3 flex gap-2">
        <Button
          size="sm"
          className="flex-1"
          disabled={soldOut || addingToCart}
          onClick={onAddToCart}
        >
          장바구니 담기
        </Button>
        <Button variant="ghost" size="sm" onClick={onRemove}>
          삭제
        </Button>
      </div>
    </div>
  );
}
