import { Badge } from '@/components/common/Badge';
import type { PaymentStatus } from '@/types/payment';
import { PAYMENT_STATUS_BADGE, PAYMENT_STATUS_LABEL } from '@/utils/paymentStatus';

interface PaymentStatusBadgeProps {
  status: PaymentStatus;
}

export function PaymentStatusBadge({ status }: PaymentStatusBadgeProps) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <Badge variant={PAYMENT_STATUS_BADGE[status]}>{PAYMENT_STATUS_LABEL[status]}</Badge>
    </span>
  );
}
