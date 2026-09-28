import { useEffect, useMemo, useState } from 'react';
import { useBlocker, useLocation, useNavigate } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { PageContainer } from '@/components/common/PageContainer';
import { QueryErrorState } from '@/components/common/QueryErrorState';
import { Spinner } from '@/components/common/Spinner';
import { useToast } from '@/components/common/toastContext';
import { CouponSection } from '@/components/order/CouponSection';
import {
  OrderItemSummaryList,
  type OrderSummaryItem,
} from '@/components/order/OrderItemSummaryList';
import { OrderPriceSummary } from '@/components/order/OrderPriceSummary';
import { PaymentMethodSection } from '@/components/order/PaymentMethodSection';
import { ShippingAddressSection } from '@/components/order/ShippingAddressSection';
import { useAddresses } from '@/hooks/queries/useAddresses';
import { useOrderFormSource } from '@/hooks/useOrderFormSource';
import { useOrderFormSubmit } from '@/hooks/useOrderFormSubmit';
import type { AvailableCoupon } from '@/types/coupon';
import type { PaymentMethodOption } from '@/types/payment';
import { formatServerDateTime } from '@/utils/formatDate';
import { formatPrice } from '@/utils/formatPrice';
import { parseOrderDraft } from '@/utils/orderDraft';
import { buildOrderName } from '@/utils/paymentRedirect';

interface CheckoutSnapshot {
  items: OrderSummaryItem[];
  totalAmount: number;
  discountAmount: number;
  finalAmount: number;
}

export default function OrderFormPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { showToast } = useToast();

  const draft = useMemo(() => parseOrderDraft(location.state), [location.state]);
  const source = useOrderFormSource(draft);

  const {
    data: addresses,
    isPending: isAddressesPending,
    isError: isAddressesError,
    error: addressesError,
    refetch: refetchAddresses,
  } = useAddresses();

  const [selectedId, setSelectedId] = useState<number | undefined>(undefined);
  const [selectedCoupon, setSelectedCoupon] = useState<AvailableCoupon | null>(null);
  const [method, setMethod] = useState<PaymentMethodOption>('CARD');
  const [isAgreed, setIsAgreed] = useState(false);
  const [snapshot, setSnapshot] = useState<CheckoutSnapshot | null>(null);

  const effectiveSelectedId =
    selectedId ?? (addresses?.find((address) => address.isDefault) ?? addresses?.[0])?.id;
  const allowVirtualAccount = draft?.kind !== 'limited';

  const { submit, isSubmitting, pendingOrder, submittedRef } = useOrderFormSubmit({
    draft,
    addressId: effectiveSelectedId,
    memberCouponId: selectedCoupon?.memberCouponId ?? null,
    orderName: buildOrderName(
      (snapshot?.items ?? source.items).map((item) => ({ productName: item.title })),
    ),
    onCouponRejected: () => setSelectedCoupon(null),
  });
  const isOrderLocked = pendingOrder !== null;

  /*
   * 주문 생성 성공 시 draft 가 가리키던 데이터가 무효화되어 사라지지만 그 전에
   * navigate 로 언마운트되므로 이 효과는 돌지 않는다. submittedRef 체크는 StrictMode 의
   * 이펙트 이중 실행(ref 는 유지됨) 때문에 토스트가 중복으로 뜨는 것을 막는다.
   */
  useEffect(() => {
    // 주문이 이미 생겼으면 장바구니 항목이 지워져 source 가 invalid 로 바뀐다. 결제창을 여는 중이니 튕기지 않는다.
    if (!source.invalid || submittedRef.current || isOrderLocked) {
      return;
    }
    submittedRef.current = true;
    showToast('info', source.invalidMessage);
    navigate(source.returnTo, { replace: true });
  }, [
    source.invalid,
    source.invalidMessage,
    source.returnTo,
    navigate,
    showToast,
    submittedRef,
    isOrderLocked,
  ]);

  const liveTotalAmount = source.items.reduce((sum, item) => sum + item.lineAmount, 0);
  const liveDiscountAmount = source.couponAllowed ? (selectedCoupon?.expectedDiscount ?? 0) : 0;
  const view: CheckoutSnapshot = snapshot ?? {
    items: source.items,
    totalAmount: liveTotalAmount,
    discountAmount: liveDiscountAmount,
    finalAmount: Math.max(0, liveTotalAmount - liveDiscountAmount),
  };
  const finalAmount = pendingOrder?.amount ?? view.finalAmount;
  const isLimitedBlocked =
    !isOrderLocked && source.limited !== undefined && !source.limited.isPurchasable;

  const handleSubmit = () => {
    // 주문 생성 뒤 장바구니·한정반 데이터가 무효화돼도 제출 시점의 주문 내용을 그대로 보여 준다.
    if (!snapshot) {
      setSnapshot(view);
    }
    submit(method);
  };

  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      !submittedRef.current && currentLocation.pathname !== nextLocation.pathname,
  );

  const isLoading = source.isLoading || isAddressesPending;
  const isError = source.isError || isAddressesError;
  const formError = source.error ?? addressesError;

  const handleRetry = () => {
    source.retry();
    if (isAddressesError) {
      refetchAddresses();
    }
  };

  if (draft === null) {
    return null;
  }

  if (isLoading) {
    return (
      <PageContainer size="md">
        <div className="flex min-h-64 items-center justify-center">
          <Spinner size="lg" />
        </div>
      </PageContainer>
    );
  }

  if (isError) {
    return (
      <PageContainer size="md">
        <QueryErrorState
          error={formError}
          onRetry={handleRetry}
          title="주문서를 불러오지 못했습니다."
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer size="md">
      <h1 className="text-xl font-bold">주문서</h1>

      <div className="mt-6 grid gap-6 md:grid-cols-[1fr_320px]">
        <div className="flex flex-col gap-8">
          <div>
            <h2 className="mb-3 text-base font-bold">주문 상품</h2>
            {source.limited && (
              <div className="mb-3 rounded-lg border border-line-strong bg-surface-muted px-4 py-3 text-sm text-content-muted">
                주문하기를 누르는 순간 선착순으로 구매가 확정됩니다. 마감{' '}
                {formatServerDateTime(source.limited.drop.closeAt)}
              </div>
            )}
            <div className="rounded-lg border border-line bg-surface px-5 py-4">
              <OrderItemSummaryList items={view.items} />
            </div>
          </div>

          {isOrderLocked && (
            <p className="rounded-lg border border-line-strong bg-surface-muted px-4 py-3 text-sm text-content-muted">
              주문이 생성되어 배송지와 쿠폰은 바꿀 수 없습니다. 결제수단만 바꿔 다시 결제할 수
              있어요.
            </p>
          )}

          <fieldset disabled={isOrderLocked} className="contents">
            <ShippingAddressSection
              addresses={addresses ?? []}
              selectedId={effectiveSelectedId}
              onSelect={setSelectedId}
            />

            {source.couponAllowed ? (
              <CouponSection
                orderAmount={view.totalAmount}
                selected={selectedCoupon}
                onSelect={setSelectedCoupon}
              />
            ) : (
              <div>
                <h2 className="mb-3 text-base font-bold">쿠폰</h2>
                <div className="rounded-lg border border-line bg-surface px-5 py-4 text-sm text-content-muted">
                  한정반은 쿠폰을 적용할 수 없습니다.
                </div>
              </div>
            )}
          </fieldset>

          <PaymentMethodSection
            method={method}
            onChange={setMethod}
            allowVirtualAccount={allowVirtualAccount}
          />
        </div>

        <div className="h-fit rounded-lg border border-line bg-surface p-5 md:sticky md:top-6">
          <OrderPriceSummary
            totalAmount={view.totalAmount}
            discountAmount={view.discountAmount}
            finalAmount={finalAmount}
            couponName={source.couponAllowed ? selectedCoupon?.couponName : undefined}
          />
          <label className="mt-5 flex cursor-pointer items-start gap-2 text-sm text-content-muted">
            <input
              type="checkbox"
              className="mt-0.5 h-4 w-4 accent-content"
              checked={isAgreed}
              onChange={(event) => setIsAgreed(event.target.checked)}
            />
            주문 내용을 확인했으며 결제에 동의합니다
          </label>
          <Button
            className="mt-3 w-full"
            onClick={handleSubmit}
            disabled={
              effectiveSelectedId === undefined ||
              view.items.length === 0 ||
              isLimitedBlocked ||
              !isAgreed
            }
            loading={isSubmitting}
          >
            {formatPrice(finalAmount)} 결제하기
          </Button>
        </div>
      </div>

      <ConfirmDialog
        open={blocker.state === 'blocked'}
        onClose={() => blocker.state === 'blocked' && blocker.reset?.()}
        onConfirm={() => blocker.state === 'blocked' && blocker.proceed?.()}
        title="주문서를 벗어나시겠습니까?"
        description="작성 중인 주문 내용은 저장되지 않습니다."
        confirmLabel="나가기"
        variant="primary"
      />
    </PageContainer>
  );
}
