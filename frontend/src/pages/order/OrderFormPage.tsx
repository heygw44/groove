import { useEffect, useMemo, useState } from 'react';
import { useBlocker, useLocation, useNavigate } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { PageContainer } from '@/components/common/PageContainer';
import { QueryErrorState } from '@/components/common/QueryErrorState';
import { Spinner } from '@/components/common/Spinner';
import { useToast } from '@/components/common/toastContext';
import { CouponSection } from '@/components/order/CouponSection';
import { OrderItemSummaryList } from '@/components/order/OrderItemSummaryList';
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
import { isSameOrderDraftSource, loadOrderFormDraft, parseOrderDraft } from '@/utils/orderDraft';
import { buildOrderName } from '@/utils/paymentRedirect';

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

  /*
   * 결제 실패 후 "주문서로 돌아가기"는 같은 location.state 로 이 페이지를 다시 연다.
   * 저장된 초안이 지금 draft 와 같은 상품 구성을 가리키면 입력값을 복원한다. 최초
   * 마운트에서만 읽으면 되므로 lazy init 을 쓴다(location.state 가 그 뒤 바뀌지 않는다).
   */
  const [restoredDraft] = useState(() => {
    const stored = loadOrderFormDraft();
    if (stored === null || draft === null || !isSameOrderDraftSource(stored.source, draft)) {
      return null;
    }
    return stored;
  });

  const [selectedId, setSelectedId] = useState<number | undefined>(restoredDraft?.addressId);
  const [selectedCoupon, setSelectedCoupon] = useState<AvailableCoupon | null>(null);
  const [method, setMethod] = useState<PaymentMethodOption>(restoredDraft?.method ?? 'CARD');
  const [isAgreed, setIsAgreed] = useState(false);

  const effectiveSelectedId =
    selectedId ?? (addresses?.find((address) => address.isDefault) ?? addresses?.[0])?.id;
  const allowVirtualAccount = draft?.kind !== 'limited';

  const { submit, isSubmitting, pendingOrder, reusableOrder, submittedRef } = useOrderFormSubmit({
    draft,
    addressId: effectiveSelectedId,
    coupon: selectedCoupon,
    orderName: buildOrderName(source.items.map((item) => ({ productName: item.title }))),
    onCouponRejected: () => setSelectedCoupon(null),
    initialPendingOrder: restoredDraft?.pendingOrder ?? null,
  });

  /*
   * 화면은 항상 배송지·쿠폰을 자유롭게 바꿀 수 있다("잠긴다" 는 동작이 없다, D2) - 상품·쿠폰이
   * 그대로면 배송지 변경은 PATCH, 그대로 재결제는 초안 재사용, 바뀌면 새 주문으로 넘어간다.
   */
  useEffect(() => {
    if (!source.invalid || submittedRef.current) {
      return;
    }
    submittedRef.current = true;
    showToast('info', source.invalidMessage);
    navigate(source.returnTo, { replace: true });
  }, [source.invalid, source.invalidMessage, source.returnTo, navigate, showToast, submittedRef]);

  const totalAmount = source.items.reduce((sum, item) => sum + item.lineAmount, 0);
  const discountAmount = source.couponAllowed ? (selectedCoupon?.expectedDiscount ?? 0) : 0;
  // 결제창이 받을 금액과 같은 주문에서 가져온다 - 재사용할 주문이 없으면 새로 계산한 금액이다.
  const finalAmount = reusableOrder?.amount ?? Math.max(0, totalAmount - discountAmount);
  // 한정반은 구매 직후 drop.purchased 가 true 로 바뀌어 "구매 완료" 로 막히는데, 그건 방금
  // 만든 내 주문 때문이지 다시 막을 이유가 아니다(pendingOrder 가 있으면 이 막힘을 무시한다).
  const isLimitedBlocked =
    pendingOrder === null && source.limited !== undefined && !source.limited.isPurchasable;

  const handleSubmit = () => {
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
              <OrderItemSummaryList items={source.items} />
            </div>
          </div>

          <ShippingAddressSection
            addresses={addresses ?? []}
            selectedId={effectiveSelectedId}
            onSelect={setSelectedId}
          />

          {source.couponAllowed ? (
            <CouponSection
              orderAmount={totalAmount}
              selected={selectedCoupon}
              onSelect={setSelectedCoupon}
              restoreCouponId={restoredDraft?.memberCouponId ?? undefined}
              heldCoupon={pendingOrder?.coupon ?? null}
            />
          ) : (
            <div>
              <h2 className="mb-3 text-base font-bold">쿠폰</h2>
              <div className="rounded-lg border border-line bg-surface px-5 py-4 text-sm text-content-muted">
                한정반은 쿠폰을 적용할 수 없습니다.
              </div>
            </div>
          )}

          <PaymentMethodSection
            method={method}
            onChange={setMethod}
            allowVirtualAccount={allowVirtualAccount}
          />
        </div>

        <div className="h-fit rounded-lg border border-line bg-surface p-5 md:sticky md:top-6">
          <OrderPriceSummary
            totalAmount={totalAmount}
            discountAmount={discountAmount}
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
              source.items.length === 0 ||
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
