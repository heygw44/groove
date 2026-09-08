import { Link } from 'react-router-dom';

import { Badge } from '@/components/common/Badge';
import { Button } from '@/components/common/Button';
import { Skeleton } from '@/components/common/Skeleton';
import { PRODUCT_STATUS_META } from '@/constants/product';
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
    <div className="flex h-full flex-col">
      <Link to={`/products/${item.productId}`} className="block">
        <WishlistThumbnail url={item.thumbnailUrl} soldOut={soldOut} />
      </Link>

      <div className="mt-2.5 flex items-start gap-2">
        <Link
          to={`/products/${item.productId}`}
          className="line-clamp-2 min-w-0 text-sm font-medium text-content hover:text-accent-hover"
        >
          {item.title}
        </Link>
        {soldOut && (
          <Badge variant="danger" className="shrink-0">
            {PRODUCT_STATUS_META.SOLD_OUT.label}
          </Badge>
        )}
      </div>
      <p className="mt-0.5 text-xs text-content-muted">{item.artistName}</p>
      <p className="mt-1 text-sm font-bold">{formatPrice(item.price)}</p>

      <div className="mt-auto flex gap-2 pt-3">
        <Button
          size="sm"
          className="flex-1"
          disabled={soldOut}
          loading={addingToCart}
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

export function WishlistCardSkeleton() {
  return (
    <div className="flex h-full flex-col">
      <Skeleton className="aspect-square w-full" />
      <Skeleton className="mt-2.5 h-4 w-4/5" />
      <Skeleton className="mt-1.5 h-4 w-3/5" />
      <Skeleton className="mt-1.5 h-3 w-2/5" />
      <Skeleton className="mt-1.5 h-4 w-1/3" />
      <div className="mt-auto flex gap-2 pt-3">
        <Skeleton className="h-8 flex-1" />
        <Skeleton className="h-8 w-14" />
      </div>
    </div>
  );
}
