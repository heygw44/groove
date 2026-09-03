import { formatPrice } from '@/utils/formatPrice';

interface OrderPriceSummaryProps {
  totalAmount: number;
  discountAmount: number;
  finalAmount: number;
}

export function OrderPriceSummary({
  totalAmount,
  discountAmount,
  finalAmount,
}: OrderPriceSummaryProps) {
  return (
    <div className="flex flex-col gap-2.5">
      <div className="flex items-center justify-between text-sm">
        <span className="text-content-muted">상품 금액</span>
        <span>{formatPrice(totalAmount)}</span>
      </div>
      <div className="flex items-center justify-between text-sm">
        <span className="text-content-muted">할인 금액</span>
        <span>{discountAmount > 0 ? `-${formatPrice(discountAmount)}` : '0원'}</span>
      </div>
      <div className="flex items-center justify-between border-t border-line pt-2.5 text-base font-bold">
        <span>총 결제 금액</span>
        <span>{formatPrice(finalAmount)}</span>
      </div>
    </div>
  );
}
