import {
  RecommendProductGrid,
  RecommendProductGridSkeleton,
} from '@/components/recommend/RecommendProductGrid';
import { RELATED_PRODUCT_SIZE } from '@/constants/recommendReasons';
import { useRelatedProducts } from '@/hooks/queries/useRecommendations';

interface RelatedProductsSectionProps {
  productId: number;
}

export function RelatedProductsSection({ productId }: RelatedProductsSectionProps) {
  const { data, isPending, isError } = useRelatedProducts(productId);

  if (isPending) {
    return (
      <section className="mt-12">
        <h2 className="text-lg font-bold">이 판 다음엔</h2>
        <RecommendProductGridSkeleton count={RELATED_PRODUCT_SIZE} />
      </section>
    );
  }

  if (isError || data.length === 0) {
    return null;
  }

  return (
    <section className="mt-12">
      <h2 className="text-lg font-bold">이 판 다음엔</h2>
      <RecommendProductGrid items={data} />
    </section>
  );
}
