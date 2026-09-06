import { Link } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { ProductCard, ProductCardSkeleton } from '@/components/product/ProductCard';
import { useRecentViews } from '@/hooks/queries/useRecentViews';
import { getErrorMessage } from '@/utils/apiError';

export default function RecentViewsPage() {
  const { data, isPending, isError, error, refetch } = useRecentViews();

  return (
    <div>
      <h2 className="text-xl font-bold">최근 본 상품</h2>
      <p className="mt-1 text-sm text-content-muted">최근에 본 순서대로 최대 20장을 보여드려요.</p>

      {isPending && (
        <div className="mt-5 grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 lg:grid-cols-4">
          {Array.from({ length: 8 }, (_, i) => (
            <ProductCardSkeleton key={i} />
          ))}
        </div>
      )}

      {!isPending && isError && (
        <EmptyState
          title="최근 본 상품을 불러오지 못했습니다"
          description={getErrorMessage(error)}
          action={
            <Button variant="secondary" onClick={() => refetch()}>
              다시 시도
            </Button>
          }
        />
      )}

      {!isPending && !isError && data && data.length === 0 && (
        <EmptyState
          title="아직 본 상품이 없습니다"
          description="상품을 둘러보면 여기에 쌓여요."
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
