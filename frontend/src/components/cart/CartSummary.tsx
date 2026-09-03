import { Button } from '@/components/common/Button';
import { formatPrice } from '@/utils/formatPrice';

interface CartSummaryProps {
  selectedCount: number;
  totalAmount: number;
  onOrder: () => void;
  disabled?: boolean;
}

export function CartSummary({
  selectedCount,
  totalAmount,
  onOrder,
  disabled = false,
}: CartSummaryProps) {
  return (
    <div className="flex flex-col items-end gap-2 rounded-lg border border-line bg-surface p-5">
      <p className="text-sm text-content-muted">선택 상품 {selectedCount}개</p>
      <p className="text-lg font-bold">총 결제 예정 금액 {formatPrice(totalAmount)}</p>
      <Button onClick={onOrder} disabled={disabled || selectedCount === 0}>
        주문하기
      </Button>
    </div>
  );
}
