import { Badge } from '@/components/common/Badge';
import type { ProductStatus } from '@/types/product';

const STATUS_LABEL: Record<ProductStatus, { label: string; variant: 'success' | 'danger' | 'neutral' }> = {
  ON_SALE: { label: '판매중', variant: 'success' },
  SOLD_OUT: { label: '품절', variant: 'danger' },
  HIDDEN: { label: '숨김', variant: 'neutral' },
};

export function ProductStatusBadge({ status }: { status: ProductStatus }) {
  const { label, variant } = STATUS_LABEL[status];
  return <Badge variant={variant}>{label}</Badge>;
}
