import type { ReactNode } from 'react';

import { ProductCard, ProductCardSkeleton } from '@/components/product/ProductCard';
import { useAlbum } from '@/hooks/queries/useAlbum';

interface AlbumPressingsSectionProps {
  albumId: number;
  currentProductId: number;
  /** false 면 다른 프레싱이 없다는 뜻이라 조회 자체를 생략한다. */
  hasOtherPressings: boolean;
  action?: ReactNode;
}

const SKELETON_COUNT = 4;

function SectionHeading({ action }: { action?: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <h2 className="text-lg font-bold">이 앨범의 다른 에디션</h2>
      {action}
    </div>
  );
}

function GuidanceMessage({ text }: { text: string }) {
  return <p className="mt-4 text-sm text-content-muted">{text}</p>;
}

export function AlbumPressingsSection({
  albumId,
  currentProductId,
  hasOtherPressings,
  action,
}: AlbumPressingsSectionProps) {
  const { data, isPending, isError } = useAlbum(albumId, { enabled: hasOtherPressings });

  if (!hasOtherPressings) {
    return (
      <section className="mt-12">
        <SectionHeading action={action} />
        <GuidanceMessage text="아직 다른 에디션이 없습니다." />
      </section>
    );
  }

  if (isPending) {
    return (
      <section className="mt-12">
        <SectionHeading action={action} />
        <div className="mt-4 grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 lg:grid-cols-4">
          {Array.from({ length: SKELETON_COUNT }, (_, index) => (
            <ProductCardSkeleton key={index} />
          ))}
        </div>
      </section>
    );
  }

  if (isError) {
    return (
      <section className="mt-12">
        <SectionHeading action={action} />
        <GuidanceMessage text="에디션 목록을 불러오지 못했습니다." />
      </section>
    );
  }

  const pressings = data.pressings.filter((product) => product.id !== currentProductId);

  return (
    <section className="mt-12">
      <SectionHeading action={action} />
      {pressings.length === 0 ? (
        <GuidanceMessage text="아직 다른 에디션이 없습니다." />
      ) : (
        <div className="mt-4 grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 lg:grid-cols-4">
          {pressings.map((product) => (
            <ProductCard key={product.id} product={product} />
          ))}
        </div>
      )}
    </section>
  );
}
