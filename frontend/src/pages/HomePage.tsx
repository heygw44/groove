import { useEffect } from 'react';
import { Link } from 'react-router-dom';

import { EmptyState } from '@/components/common/EmptyState';
import { PageContainer } from '@/components/common/PageContainer';
import { QueryErrorState } from '@/components/common/QueryErrorState';
import { LimitedDropBanner } from '@/components/limited/LimitedDropBanner';
import { ProductCard, ProductCardSkeleton } from '@/components/product/ProductCard';
import { DiggingSection } from '@/components/recommend/DiggingSection';
import { useLimitedDrops } from '@/hooks/queries/useLimitedDrops';
import { useProducts } from '@/hooks/queries/useProducts';
import { useServerNow } from '@/hooks/useServerNow';
import { pickBannerDrop } from '@/utils/limitedDrop';
import { applyServerTime } from '@/utils/serverTime';

const NEW_ARRIVAL_SIZE = 8;

export default function HomePage() {
  const nowMs = useServerNow();
  const { data: limitedDropData } = useLimitedDrops();
  const {
    data: productData,
    isPending: isProductPending,
    isError: isProductError,
    error: productError,
    refetch: refetchProducts,
  } = useProducts({
    sort: 'latest',
    size: NEW_ARRIVAL_SIZE,
  });

  useEffect(() => {
    if (limitedDropData?.serverTime) {
      applyServerTime(limitedDropData.serverTime);
    }
  }, [limitedDropData?.serverTime]);

  const bannerDrop = limitedDropData ? pickBannerDrop(limitedDropData.drops) : undefined;
  const hasBanner = Boolean(bannerDrop);

  return (
    <PageContainer>
      <h1 className="sr-only">GROOVE — 바이닐 레코드 스토어</h1>

      {bannerDrop && <LimitedDropBanner drop={bannerDrop} nowMs={nowMs} />}

      <DiggingSection className={hasBanner ? 'mt-10' : undefined} />

      {/* DiggingSection 이 null 일 수 있어 first:mt-0 으로 앞 형제 유무에 따라 상단 여백을 정리한다. */}
      <section className="mt-10 first:mt-0">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold">새로 나온 앨범</h2>
          <Link to="/products?sort=latest" className="text-sm text-content-muted">
            더보기 →
          </Link>
        </div>

        {isProductPending && (
          <div className="mt-4 grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 lg:grid-cols-4">
            {Array.from({ length: NEW_ARRIVAL_SIZE }, (_, index) => (
              <ProductCardSkeleton key={index} />
            ))}
          </div>
        )}

        {!isProductPending && isProductError && (
          <QueryErrorState
            error={productError}
            onRetry={refetchProducts}
            title="새로 나온 앨범을 불러오지 못했습니다."
          />
        )}

        {!isProductPending &&
          !isProductError &&
          productData &&
          productData.content.length === 0 && <EmptyState title="새로 나온 앨범이 없습니다" />}

        {!isProductPending && !isProductError && productData && productData.content.length > 0 && (
          <div className="mt-4 grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 lg:grid-cols-4">
            {productData.content.map((product) => (
              <ProductCard key={product.id} product={product} />
            ))}
          </div>
        )}
      </section>
    </PageContainer>
  );
}
