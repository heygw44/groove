import axios from 'axios';
import { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';

import { Badge } from '@/components/common/Badge';
import { PageContainer } from '@/components/common/PageContainer';
import { QueryErrorState } from '@/components/common/QueryErrorState';
import { StarRatingDisplay } from '@/components/common/StarRating';
import { AlbumWatchButton } from '@/components/notification/AlbumWatchButton';
import { AlbumPressingsSection } from '@/components/product/AlbumPressingsSection';
import { PressingSpecTable } from '@/components/product/PressingSpecTable';
import { ProductDetailSkeleton } from '@/components/product/ProductDetailSkeleton';
import { ProductImageGallery } from '@/components/product/ProductImageGallery';
import { ProductPurchasePanel } from '@/components/product/ProductPurchasePanel';
import { RelatedProductsSection } from '@/components/recommend/RelatedProductsSection';
import { ReviewSection } from '@/components/review/ReviewSection';
import { useProduct } from '@/hooks/queries/useProduct';
import NotFoundPage from '@/pages/NotFoundPage';
import { getErrorCode } from '@/utils/apiError';

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
      <PageContainer>
        <ProductDetailSkeleton />
      </PageContainer>
    );
  }

  const isNotFoundStatus = axios.isAxiosError(error) && error.response?.status === 404;
  if (isError && (isNotFoundStatus || NOT_FOUND_CODES.has(getErrorCode(error) ?? ''))) {
    return <NotFoundPage />;
  }

  if (isError || !product) {
    return (
      <PageContainer>
        <QueryErrorState error={error} onRetry={refetch} title="상품을 불러오지 못했습니다." />
      </PageContainer>
    );
  }

  return (
    <PageContainer>
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

          <PressingSpecTable
            pressing={product.pressing}
            label={product.label}
            releaseDate={product.releaseDate}
            colorVariant={product.colorVariant}
            pressingInfo={product.pressingInfo}
          />

          {product.genres.length > 0 && (
            <div className="mt-2 flex flex-wrap gap-1.5">
              {product.genres.map((genre) => (
                <Link key={genre.id} to={`/products?genreIds=${genre.id}`}>
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

      <AlbumPressingsSection
        albumId={product.album.id}
        currentProductId={product.id}
        hasOtherPressings={product.album.pressingCount > 1}
        action={
          <AlbumWatchButton
            albumId={product.album.id}
            albumTitle={product.album.title}
            watched={product.album.watched}
          />
        }
      />

      <RelatedProductsSection productId={product.id} />

      <ReviewSection
        productId={product.id}
        averageRating={product.averageRating}
        reviewCount={product.reviewCount ?? 0}
      />
    </PageContainer>
  );
}
