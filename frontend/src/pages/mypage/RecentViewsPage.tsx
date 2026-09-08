import { Link } from 'react-router-dom';

import { EmptyState } from '@/components/common/EmptyState';
import { QueryErrorState } from '@/components/common/QueryErrorState';
import { ProductCard, ProductCardSkeleton } from '@/components/product/ProductCard';
import { useRecentViews } from '@/hooks/queries/useRecentViews';

export default function RecentViewsPage() {
  const { data, isPending, isError, error, refetch } = useRecentViews();

  return (
    <div>
      <h2 className="text-xl font-bold">최근 본 상품</h2>
      <p className="mt-1 text-sm text-content-muted">
        최근에 본 순서대로 최대 20개를 보여드립니다.
      </p>

      {isPending && (
        <div className="mt-5 grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 lg:grid-cols-4">
          {Array.from({ length: 8 }, (_, i) => (
            <ProductCardSkeleton key={i} />
          ))}
        </div>
      )}

      {!isPending && isError && (
        <QueryErrorState
          error={error}
          onRetry={refetch}
          title="최근 본 상품을 불러오지 못했습니다."
        />
      )}

      {!isPending && !isError && data && data.length === 0 && (
        <EmptyState
          title="아직 본 상품이 없습니다"
          description="상품을 보면 여기에 기록됩니다."
          action={
            <Link
              to="/products"
              className="inline-flex h-10 items-center justify-center gap-1.5 rounded-md border border-line-strong bg-surface px-4 text-sm font-medium text-content hover:bg-surface-muted"
            >
              상품 보러 가기
            </Link>
          }
        />
      )}

      {!isPending && !isError && data && data.length > 0 && (
        <div className="mt-5 grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 lg:grid-cols-4">
          {data.map((product) => (
            <ProductCard key={product.id} product={product} />
          ))}
        </div>
      )}
    </div>
  );
}
