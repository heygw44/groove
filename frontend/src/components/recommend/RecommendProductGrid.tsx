import { ProductCard, ProductCardSkeleton } from '@/components/product/ProductCard';
import { RecommendReasonBadges } from '@/components/recommend/RecommendReasonBadge';
import type { RecommendItem } from '@/types/recommend';

export function RecommendProductGrid({ items }: { items: RecommendItem[] }) {
  return (
    <div className="mt-4 grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 lg:grid-cols-4">
      {items.map((item) => (
        <ProductCard key={item.product.id} product={item.product}>
          <RecommendReasonBadges reasons={item.reasons} />
        </ProductCard>
      ))}
    </div>
  );
}

export function RecommendProductGridSkeleton({ count }: { count: number }) {
  return (
    <div className="mt-4 grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 lg:grid-cols-4">
      {Array.from({ length: count }, (_, index) => (
        <ProductCardSkeleton key={index} />
      ))}
    </div>
  );
}
