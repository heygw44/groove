import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useBlocker, useLocation, useNavigate } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { EmptyState } from '@/components/common/EmptyState';
import { Spinner } from '@/components/common/Spinner';
import { useToast } from '@/components/common/toastContext';
import { CouponSection } from '@/components/order/CouponSection';
import type { OrderSummaryItem } from '@/components/order/OrderItemSummaryList';
import { OrderItemSummaryList } from '@/components/order/OrderItemSummaryList';
import { OrderPriceSummary } from '@/components/order/OrderPriceSummary';
import { ShippingAddressSection } from '@/components/order/ShippingAddressSection';
import { useCreateOrder } from '@/hooks/mutations/useOrderMutations';
import { addressKeys, couponKeys } from '@/hooks/queries/queryKeys';
import { useAddresses } from '@/hooks/queries/useAddresses';
import { useCart } from '@/hooks/queries/useCart';
import { useProduct } from '@/hooks/queries/useProduct';
import type { AvailableCoupon } from '@/types/coupon';
import { getErrorCode, getErrorMessage } from '@/utils/apiError';
import { parseOrderDraft, toOrderCreateRequest } from '@/utils/orderDraft';

const STOCK_ERROR_CODES = new Set(['STOCK_INSUFFICIENT', 'STOCK_CONFLICT']);
const COUPON_ERROR_CODES = new Set([
  'COUPON_NOT_FOUND',
  'COUPON_EXPIRED',
  'COUPON_DISABLED',
  'COUPON_ALREADY_USED',
  'COUPON_MIN_ORDER_AMOUNT_NOT_MET',
]);

export default function OrderFormPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const queryClient = useQueryClient();
  const createOrderMutation = useCreateOrder();

  const draft = useMemo(() => parseOrderDraft(location.state), [location.state]);

  const cartQuery = useCart();
  const productQuery = useProduct(draft?.kind === 'direct' ? draft.productId : 0);
  const {
    data: addresses,
    isPending: isAddressesPending,
    isError: isAddressesError,
    refetch: refetchAddresses,
  } = useAddresses();

  const [selectedId, setSelectedId] = useState<number | undefined>(undefined);
  const [selectedCoupon, setSelectedCoupon] = useState<AvailableCoupon | null>(null);
  const submittedRef = useRef(false);

  const cartItems = cartQuery.data?.items ?? [];
  const cartOrderItems =
    draft?.kind === 'cart' ? cartItems.filter((item) => draft.cartItemIds.includes(item.id)) : [];

  const shouldReturnToCart =
    draft === null ||
    (draft.kind === 'cart' &&
      cartQuery.data !== undefined &&
      cartOrderItems.length !== draft.cartItemIds.length);

  /*
   * 주문 생성 성공 시 cart 가 무효화되어 항목이 사라지지만 그 전에 navigate 로
   * 언마운트되므로 이 효과는 돌지 않는다. submittedRef 체크는 StrictMode 의
   * 이펙트 이중 실행(ref 는 유지됨) 때문에 토스트가 중복으로 뜨는 것을 막는다.
   */
  useEffect(() => {
    if (!shouldReturnToCart || submittedRef.current) {
      return;
    }
    submittedRef.current = true;
    showToast('info', '주문할 상품을 다시 선택해주세요.');
    navigate('/cart', { replace: true });
  }, [shouldReturnToCart, navigate, showToast]);

  const orderItems: OrderSummaryItem[] =
    draft?.kind === 'cart'
      ? cartOrderItems.map((item) => ({
          key: item.id,
          title: item.title,
          artistName: item.artistName,
          thumbnailUrl: item.thumbnailUrl,
          price: item.price,
          quantity: item.quantity,
          lineAmount: item.subtotal,
        }))
      : draft?.kind === 'direct' && productQuery.data
        ? [
            {
              key: productQuery.data.id,
              title: productQuery.data.title,
              artistName: productQuery.data.artist.name,
              thumbnailUrl: productQuery.data.images[0]?.url,
              price: productQuery.data.price,
              quantity: draft.quantity,
              lineAmount: productQuery.data.price * draft.quantity,
            },
          ]
        : [];

  const effectiveSelectedId =
    selectedId ?? (addresses?.find((address) => address.isDefault) ?? addresses?.[0])?.id;

  const totalAmount = orderItems.reduce((sum, item) => sum + item.lineAmount, 0);
  const discountAmount = selectedCoupon?.expectedDiscount ?? 0;
  const finalAmount = Math.max(0, totalAmount - discountAmount);

  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      !submittedRef.current && currentLocation.pathname !== nextLocation.pathname,
  );

  const isLoading =
    draft?.kind === 'cart'
      ? cartQuery.isPending || isAddressesPending
      : draft?.kind === 'direct'
        ? productQuery.isPending || isAddressesPending
        : true;

  const isError =
    draft?.kind === 'cart'
      ? cartQuery.isError || isAddressesError
      : draft?.kind === 'direct'
        ? productQuery.isError || isAddressesError
        : false;

  const handleRetry = () => {
    if (draft?.kind === 'cart') {
      cartQuery.refetch();
    } else if (draft?.kind === 'direct') {
      productQuery.refetch();
    }
    if (isAddressesError) {
      refetchAddresses();
    }
  };

  const handleSubmit = () => {
    if (draft === null || effectiveSelectedId === undefined) {
      return;
    }

    createOrderMutation.mutate(
      toOrderCreateRequest(draft, effectiveSelectedId, selectedCoupon?.memberCouponId ?? null),
      {
        onSuccess: (data) => {
          submittedRef.current = true;
          navigate(`/orders/${data.orderId}`, { replace: true });
        },
        onError: (error) => {
          const code = getErrorCode(error);
          if (code && STOCK_ERROR_CODES.has(code)) {
            submittedRef.current = true;
            showToast('error', getErrorMessage(error));
            navigate('/cart', { replace: true });
            return;
          }
          if (code === 'MEMBER_ADDRESS_NOT_FOUND') {
            queryClient.invalidateQueries({ queryKey: addressKeys.all });
            showToast('error', getErrorMessage(error));
            return;
          }
          if (code && COUPON_ERROR_CODES.has(code)) {
            setSelectedCoupon(null);
            queryClient.invalidateQueries({ queryKey: couponKeys.all });
            showToast('error', `${getErrorMessage(error)} 쿠폰을 해제했으니 다시 확인해주세요.`);
            return;
          }
          showToast('error', getErrorMessage(error));
        },
      },
    );
  };

  if (draft === null) {
    return null;
  }

  if (isLoading) {
    return (
      <div className="flex min-h-64 items-center justify-center">
        <Spinner size="lg" />
      </div>
    );
  }

  if (isError) {
    return (
      <EmptyState
        title="주문서를 불러오지 못했습니다"
        action={<Button onClick={handleRetry}>다시 시도</Button>}
      />
    );
  }

  return (
    <div>
      <h1 className="text-xl font-bold">주문서</h1>

      <div className="mt-6 grid gap-6 md:grid-cols-[1fr_320px]">
        <div className="flex flex-col gap-8">
          <div>
            <h2 className="mb-3 text-base font-bold">주문 상품</h2>
            <div className="rounded-lg border border-line bg-surface px-5 py-4">
              <OrderItemSummaryList items={orderItems} />
            </div>
          </div>

          <ShippingAddressSection
            addresses={addresses ?? []}
            selectedId={effectiveSelectedId}
            onSelect={setSelectedId}
          />

          <CouponSection
            orderAmount={totalAmount}
            selected={selectedCoupon}
            onSelect={setSelectedCoupon}
          />
        </div>

        <div className="h-fit rounded-lg border border-line bg-surface p-5 md:sticky md:top-6">
          <OrderPriceSummary
            totalAmount={totalAmount}
            discountAmount={discountAmount}
            finalAmount={finalAmount}
            couponName={selectedCoupon?.couponName}
          />
          <Button
            className="mt-5 w-full"
            onClick={handleSubmit}
            disabled={
              effectiveSelectedId === undefined ||
              orderItems.length === 0 ||
              createOrderMutation.isPending
            }
          >
            주문하기
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
    </div>
  );
}
