import { ProductCard, ProductCardSkeleton } from '@/components/product/ProductCard';
import { EDITION_TYPE_LABELS, getCountryLabel } from '@/constants/product';
import { useAlbum } from '@/hooks/queries/useAlbum';
import type { ProductSummary } from '@/types/product';

interface AlbumPressingsSectionProps {
  albumId: number;
  currentProductId: number;
}

const SKELETON_COUNT = 4;

function buildMetaLine(product: ProductSummary): string {
  return [
    product.country && getCountryLabel(product.country),
    product.pressingYear?.toString(),
    EDITION_TYPE_LABELS[product.editionType],
  ]
    .filter((value): value is string => value !== undefined)
    .join(' · ');
}

export function AlbumPressingsSection({
  albumId,
  currentProductId,
}: AlbumPressingsSectionProps) {
  const { data, isPending, isError } = useAlbum(albumId);

  if (isPending) {
    return (
      <section className="mt-12">
        <h2 className="text-lg font-bold">이 앨범의 다른 프레싱</h2>
        <div className="mt-4 grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 lg:grid-cols-4">
          {Array.from({ length: SKELETON_COUNT }, (_, index) => (
            <ProductCardSkeleton key={index} />
          ))}
        </div>
      </section>
    );
  }

  if (isError) {
    return null;
  }

  const pressings = data.pressings.filter((product) => product.id !== currentProductId);
  if (pressings.length === 0) {
    return null;
  }

  return (
    <section className="mt-12">
      <h2 className="text-lg font-bold">이 앨범의 다른 프레싱</h2>
      <div className="mt-4 grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 lg:grid-cols-4">
        {pressings.map((product) => (
          <ProductCard key={product.id} product={product}>
            <p className="mt-1 text-xs text-content-muted">{buildMetaLine(product)}</p>
          </ProductCard>
        ))}
      </div>
    </section>
  );
}
