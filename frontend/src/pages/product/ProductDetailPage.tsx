import axios from 'axios';
import { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';

import { Badge } from '@/components/common/Badge';
import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { StarRatingDisplay } from '@/components/common/StarRating';
import { ProductDetailSkeleton } from '@/components/product/ProductDetailSkeleton';
import { ProductImageGallery } from '@/components/product/ProductImageGallery';
import { ProductPurchasePanel } from '@/components/product/ProductPurchasePanel';
import { ReviewSection } from '@/components/review/ReviewSection';
import { useProduct } from '@/hooks/queries/useProduct';
import NotFoundPage from '@/pages/NotFoundPage';
import { getErrorCode, getErrorMessage } from '@/utils/apiError';
import { formatDate } from '@/utils/formatDate';

const NOT_FOUND_CODES = new Set(['PRODUCT_NOT_FOUND', 'PRODUCT_HIDDEN']);

const ID_PATTERN = /^\d+$/;

export default function ProductDetailPage() {
  const { id: idParam } = useParams();
  const isValidId = idParam !== undefined && ID_PATTERN.test(idParam);
  const id = isValidId ? Number(idParam) : -1;

  const { data: product, isPending, isError, error, refetch } = useProduct(id);

  useEffect(() => {
    if (!product) {
      return undefined;
    }
    const previousTitle = document.title;
    document.title = `${product.title} - ${product.artist.name} | GROOVE`;
    return () => {
      document.title = previousTitle;
    };
  }, [product]);

  // enabled:false 여도 isPending 은 true 이므로, 잘못된 id 분기를 로딩 분기보다 먼저 둔다.
  if (!isValidId) {
    return <NotFoundPage />;
  }

  if (isPending) {
    return (
      <div className="mx-auto max-w-6xl px-4 py-8">
        <ProductDetailSkeleton />
      </div>
    );
  }

  const isNotFoundStatus = axios.isAxiosError(error) && error.response?.status === 404;
  if (isError && (isNotFoundStatus || NOT_FOUND_CODES.has(getErrorCode(error) ?? ''))) {
    return <NotFoundPage />;
  }

  if (isError || !product) {
    return (
      <div className="mx-auto max-w-6xl px-4 py-8">
        <EmptyState
          title="상품을 불러오지 못했습니다."
          description={getErrorMessage(error)}
          action={
            <Button variant="secondary" onClick={() => refetch()}>
              다시 시도
            </Button>
          }
        />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-8">
      <Link to="/products" className="text-sm text-content-muted">
        ← 상품 목록
      </Link>

      <div className="mt-4 grid gap-8 md:grid-cols-2" key={product.id}>
        <ProductImageGallery images={product.images} />

        <div>
          <h1 className="text-2xl font-bold tracking-tight">{product.title}</h1>
          <Link
            to={`/products?artistId=${product.artist.id}`}
            className="mt-1 inline-block text-sm text-content-muted"
          >
            {product.artist.name}
          </Link>

          <dl className="mt-4 flex flex-col gap-2 text-sm">
            {product.label && (
              <div className="flex gap-2">
                <dt className="w-20 shrink-0 text-content-muted">레이블</dt>
                <dd className="m-0">{product.label.name}</dd>
              </div>
            )}
            {product.releaseDate && (
              <div className="flex gap-2">
                <dt className="w-20 shrink-0 text-content-muted">발매일</dt>
                <dd className="m-0">{formatDate(product.releaseDate)}</dd>
              </div>
            )}
            {product.pressingInfo && (
              <div className="flex gap-2">
                <dt className="w-20 shrink-0 text-content-muted">프레싱</dt>
                <dd className="m-0">{product.pressingInfo}</dd>
              </div>
            )}
            {product.colorVariant && (
              <div className="flex gap-2">
                <dt className="w-20 shrink-0 text-content-muted">컬러반</dt>
                <dd className="m-0">{product.colorVariant}</dd>
              </div>
            )}
          </dl>

          {product.genres.length > 0 && (
            <div className="mt-3 flex flex-wrap gap-1.5">
              {product.genres.map((genre) => (
                <Link
                  key={genre.id}
                  to={`/products?genreIds=${genre.id}`}
                >
                  <Badge variant="accent">{genre.name}</Badge>
                </Link>
              ))}
            </div>
          )}

          <a href="#reviews" className="mt-3 flex items-center gap-1.5 text-sm text-content-muted">
            {product.averageRating !== undefined ? (
              <>
                <StarRatingDisplay value={product.averageRating} size="sm" />
                {product.averageRating.toFixed(1)} · 리뷰 {product.reviewCount ?? 0}개
              </>
            ) : (
              `리뷰 ${product.reviewCount ?? 0}개`
            )}
          </a>

          <ProductPurchasePanel product={product} />
        </div>
      </div>

      {product.description && (
        <p className="mt-10 whitespace-pre-line text-sm text-content">{product.description}</p>
      )}

      <ReviewSection
        productId={product.id}
        averageRating={product.averageRating}
        reviewCount={product.reviewCount ?? 0}
      />
    </div>
  );
}
