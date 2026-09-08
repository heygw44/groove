import { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';

import { EmptyState } from '@/components/common/EmptyState';
import { PageContainer } from '@/components/common/PageContainer';
import { QueryErrorState } from '@/components/common/QueryErrorState';
import { Spinner } from '@/components/common/Spinner';
import { AlbumWatchButton } from '@/components/notification/AlbumWatchButton';
import { ProductCard } from '@/components/product/ProductCard';
import { useAlbum } from '@/hooks/queries/useAlbum';
import { buildPressingMetaLine } from '@/utils/pressing';

const ID_PATTERN = /^\d+$/;

export default function AlbumDetailPage() {
  const { id: idParam } = useParams();
  const isValidId = idParam !== undefined && ID_PATTERN.test(idParam);
  const id = isValidId ? Number(idParam) : -1;

  const { data: album, isPending, isError, error, refetch } = useAlbum(id);

  useEffect(() => {
    if (!album) {
      return undefined;
    }
    const previousTitle = document.title;
    document.title = `${album.title} - ${album.artist.name} | GROOVE`;
    return () => {
      document.title = previousTitle;
    };
  }, [album]);

  const backToProducts = (
    <Link
      to="/products"
      className="inline-flex h-10 items-center justify-center gap-1.5 rounded-md border border-line-strong bg-surface px-4 text-sm font-medium text-content hover:bg-surface-muted"
    >
      상품 보러 가기
    </Link>
  );

  if (!isValidId) {
    return (
      <PageContainer>
        <EmptyState
          title="앨범을 찾을 수 없습니다"
          description="주소를 다시 확인해주세요."
          action={backToProducts}
        />
      </PageContainer>
    );
  }

  if (isPending) {
    return (
      <PageContainer>
        <div className="flex min-h-64 items-center justify-center">
          <Spinner size="lg" />
        </div>
      </PageContainer>
    );
  }

  if (isError || !album) {
    return (
      <PageContainer>
        <QueryErrorState error={error} onRetry={refetch} title="앨범 정보를 불러오지 못했습니다." />
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">{album.title}</h1>
          <p className="mt-1 text-sm text-content-muted">{album.artist.name}</p>
          {album.originalReleaseYear && (
            <p className="mt-0.5 text-sm text-content-muted">{album.originalReleaseYear}</p>
          )}
        </div>
        <AlbumWatchButton albumId={album.id} albumTitle={album.title} watched={album.watched} />
      </div>

      {album.description && (
        <p className="mt-6 whitespace-pre-line text-sm text-content">{album.description}</p>
      )}

      <section className="mt-10">
        <h2 className="text-lg font-bold">에디션</h2>
        <div className="mt-4 grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 lg:grid-cols-4">
          {album.pressings.map((product) => (
            <ProductCard key={product.id} product={product}>
              <p className="mt-1 text-xs text-content-muted">{buildPressingMetaLine(product)}</p>
            </ProductCard>
          ))}
        </div>
      </section>
    </PageContainer>
  );
}
