import { Badge } from '@/components/common/Badge';
import { PRODUCT_STATUS_META } from '@/constants/product';
import type { ProductStatus } from '@/types/product';

export function ProductStatusBadge({ status }: { status: ProductStatus }) {
  const { label, variant } = PRODUCT_STATUS_META[status];
  return <Badge variant={variant}>{label}</Badge>;
}
