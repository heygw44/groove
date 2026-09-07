import {
  RecommendProductGrid,
  RecommendProductGridSkeleton,
} from '@/components/recommend/RecommendProductGrid';
import { RELATED_PRODUCT_SIZE, RELATED_PRODUCTS_SECTION_TITLE } from '@/constants/recommendReasons';
import { useRelatedProducts } from '@/hooks/queries/useRecommendations';

interface RelatedProductsSectionProps {
  productId: number;
}

export function RelatedProductsSection({ productId }: RelatedProductsSectionProps) {
  const { data, isPending, isError } = useRelatedProducts(productId);

  if (isPending) {
    return (
      <section className="mt-12">
        <h2 className="text-lg font-bold">{RELATED_PRODUCTS_SECTION_TITLE}</h2>
        <RecommendProductGridSkeleton count={RELATED_PRODUCT_SIZE} />
      </section>
    );
  }

  if (isError || data.length === 0) {
    return null;
  }

  return (
    <section className="mt-12">
      <h2 className="text-lg font-bold">{RELATED_PRODUCTS_SECTION_TITLE}</h2>
      <RecommendProductGrid items={data} />
    </section>
  );
}
