import { Link } from 'react-router-dom';

import { Badge } from '@/components/common/Badge';
import { Skeleton } from '@/components/common/Skeleton';
import { WishButton } from '@/components/product/WishButton';
import type { ProductSummary } from '@/types/product';
import { formatPrice } from '@/utils/formatPrice';

interface ProductCardProps {
  product: ProductSummary;
}

function ProductThumbnail({ url, soldOut }: { url?: string; soldOut: boolean }) {
  return (
    <div
      className={`aspect-square overflow-hidden rounded-md bg-surface-muted ${soldOut ? 'opacity-60' : ''}`}
    >
      {url ? (
        <img
          src={url}
          alt=""
          loading="lazy"
          className="h-full w-full object-cover transition-transform group-hover:scale-105"
        />
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

export function ProductCard({ product }: ProductCardProps) {
  const soldOut = product.status === 'SOLD_OUT';

  return (
    <Link
      to={`/products/${product.id}`}
      className="group block no-underline hover:no-underline text-content"
    >
      <div className="relative">
        <ProductThumbnail url={product.thumbnailUrl} soldOut={soldOut} />
        {soldOut && (
          <Badge variant="danger" className="absolute left-2 top-2">
            품절
          </Badge>
        )}
        <WishButton
          size="sm"
          productId={product.id}
          wishlisted={product.wishlisted}
          className="absolute right-2 top-2"
        />
      </div>
      <p className="mt-2.5 line-clamp-2 text-sm font-medium">{product.title}</p>
      <p className="mt-0.5 text-xs text-content-muted">{product.artistName}</p>
      <p className="mt-1 text-sm font-bold">{formatPrice(product.price)}</p>
    </Link>
  );
}

export function ProductCardSkeleton() {
  return (
    <div>
      <Skeleton className="aspect-square w-full" />
      <Skeleton className="mt-2.5 h-4 w-4/5" />
      <Skeleton className="mt-1.5 h-3 w-2/5" />
      <Skeleton className="mt-1.5 h-4 w-1/3" />
    </div>
  );
}
