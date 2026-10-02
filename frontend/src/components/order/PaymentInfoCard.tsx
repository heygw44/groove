import { OrderPriceSummary } from '@/components/order/OrderPriceSummary';
import { PaymentStatusBadge } from '@/components/payment/PaymentStatusBadge';
import type { OrderPayment } from '@/types/payment';
import { formatServerDateTime } from '@/utils/formatDate';
import { getPaymentMethodLabel, isCancellationPending } from '@/utils/paymentStatus';

interface PaymentInfoCardProps {
  totalAmount: number;
  discountAmount: number;
  finalAmount: number;
  couponName?: string;
  payment?: OrderPayment;
}

/** 상품금액·쿠폰할인·최종금액 + 결제수단 줄을 한 카드에 담는다. 결제 전(PENDING)이면 "결제 전"만 보여준다. */
export function PaymentInfoCard({
  totalAmount,
  discountAmount,
  finalAmount,
  couponName,
  payment,
}: PaymentInfoCardProps) {
  return (
    <section className="mt-8">
      <h2 className="mb-3 text-base font-bold">결제정보</h2>
      <div className="rounded-lg border border-line bg-surface p-5">
        <OrderPriceSummary
          totalAmount={totalAmount}
          discountAmount={discountAmount}
          finalAmount={finalAmount}
          couponName={couponName}
        />

        <div className="mt-4 space-y-1.5 border-t border-line pt-4 text-sm">
          {payment && isCancellationPending(payment.status) && (
            <p className="flex items-center gap-2">
              <span className="text-content-muted">결제상태</span>
              <PaymentStatusBadge status={payment.status} />
            </p>
          )}
          <p className="flex items-center justify-between gap-2">
            <span className="text-content-muted">결제수단</span>
            <span className="font-medium">{getPaymentMethodLabel(payment)}</span>
          </p>
          {payment?.approvedAt && (
            <p className="flex items-center justify-between gap-2">
              <span className="text-content-muted">승인 시각</span>
              <span className="font-medium">{formatServerDateTime(payment.approvedAt)}</span>
            </p>
          )}
          {payment?.status === 'CANCELED' && payment.canceledAt && (
            <p className="flex items-center justify-between gap-2">
              <span className="text-content-muted">취소 시각</span>
              <span className="font-medium">{formatServerDateTime(payment.canceledAt)}</span>
            </p>
          )}
        </div>
      </div>
    </section>
  );
}
