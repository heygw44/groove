import { useEffect } from 'react';
import { Link } from 'react-router-dom';

import { LimitedDropBanner } from '@/components/limited/LimitedDropBanner';
import { ProductCard, ProductCardSkeleton } from '@/components/product/ProductCard';
import { useLimitedDrops } from '@/hooks/queries/useLimitedDrops';
import { useProducts } from '@/hooks/queries/useProducts';
import { useServerNow } from '@/hooks/useServerNow';
import { pickBannerDrop } from '@/utils/limitedDrop';
import { applyServerTime } from '@/utils/serverTime';

const NEW_ARRIVAL_SIZE = 8;

export default function HomePage() {
  const nowMs = useServerNow();
  const { data: limitedDropData } = useLimitedDrops();
  const { data: productData, isPending: isProductPending } = useProducts({
    sort: 'latest',
    size: NEW_ARRIVAL_SIZE,
  });

  useEffect(() => {
    if (limitedDropData?.serverTime) {
      applyServerTime(limitedDropData.serverTime);
    }
  }, [limitedDropData?.serverTime]);

  const bannerDrop = limitedDropData ? pickBannerDrop(limitedDropData.drops) : undefined;

  return (
    <div className="mx-auto max-w-6xl px-4 py-8">
      {bannerDrop && <LimitedDropBanner drop={bannerDrop} nowMs={nowMs} />}

      <section className={bannerDrop ? 'mt-10' : undefined}>
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold">신보</h2>
          <Link to="/products?sort=latest" className="text-sm text-content-muted">
            더보기 →
          </Link>
        </div>

        <div className="mt-4 grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 lg:grid-cols-4">
          {isProductPending &&
            Array.from({ length: NEW_ARRIVAL_SIZE }, (_, index) => (
              <ProductCardSkeleton key={index} />
            ))}

          {!isProductPending &&
            productData?.content.map((product) => (
              <ProductCard key={product.id} product={product} />
            ))}
        </div>
      </section>
    </div>
  );
}
