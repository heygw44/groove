import { EDITION_TYPE_LABELS, getCountryLabel } from '@/constants/product';
import type { ProductSummary } from '@/types/product';

export function buildPressingMetaLine(product: ProductSummary): string {
  return [
    product.country && getCountryLabel(product.country),
    product.pressingYear?.toString(),
    EDITION_TYPE_LABELS[product.editionType],
  ]
    .filter((value): value is string => value !== undefined)
    .join(' · ');
}
