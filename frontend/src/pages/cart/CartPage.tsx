import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { CartItemRow } from '@/components/cart/CartItemRow';
import { CartSummary } from '@/components/cart/CartSummary';
import { Button } from '@/components/common/Button';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { EmptyState } from '@/components/common/EmptyState';
import { Spinner } from '@/components/common/Spinner';
import { useToast } from '@/components/common/toastContext';
import { useRemoveCartItem, useUpdateCartItemQuantity } from '@/hooks/mutations/useCartMutations';
import { useCart } from '@/hooks/queries/useCart';
import type { CartItem } from '@/types/cart';
import { getErrorMessage } from '@/utils/apiError';
import { isCartItemSoldOut, sumSelectedSubtotal } from '@/utils/cart';

export default function CartPage() {
  const { data: cart, isPending, isError, refetch } = useCart();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const updateQuantityMutation = useUpdateCartItemQuantity();
  const removeMutation = useRemoveCartItem();

  // null = 아직 사용자가 손대지 않은 상태 → 판매 가능한 항목 전부가 선택된 것으로 취급한다.
  const [selectedIds, setSelectedIds] = useState<Set<number> | null>(null);
  const [removing, setRemoving] = useState<CartItem | undefined>(undefined);

  const items = cart?.items ?? [];
  const selectableIds = items.filter((item) => !isCartItemSoldOut(item)).map((item) => item.id);

  // 삭제되거나 품절로 바뀐 항목의 선택은 지운다.
  const effectiveSelected = new Set(
    (selectedIds ? [...selectedIds] : selectableIds).filter((id) => selectableIds.includes(id)),
  );

  const isAllSelected =
    selectableIds.length > 0 && selectableIds.every((id) => effectiveSelected.has(id));

  const toggleAll = (checked: boolean) => {
    setSelectedIds(checked ? new Set(selectableIds) : new Set());
  };

  const toggleOne = (itemId: number, checked: boolean) => {
    const next = new Set(effectiveSelected);
    if (checked) {
      next.add(itemId);
    } else {
      next.delete(itemId);
    }
    setSelectedIds(next);
  };

  const handleQuantityChange = (cartItemId: number, quantity: number) => {
    updateQuantityMutation.mutate(
      { cartItemId, quantity },
      { onError: (error) => showToast('error', getErrorMessage(error)) },
    );
  };

  const handleRemove = () => {
    if (!removing) {
      return;
    }
    removeMutation.mutate(removing.id, {
      onSuccess: () => {
        showToast('success', '삭제했습니다.');
        setRemoving(undefined);
      },
      onError: (error) => {
        setRemoving(undefined);
        showToast('error', getErrorMessage(error));
      },
    });
  };

  const handleOrder = () => {
    navigate('/orders/new', { state: { cartItemIds: [...effectiveSelected] } });
  };

  if (isPending) {
    return (
      <div className="flex min-h-64 items-center justify-center">
        <Spinner size="lg" />
      </div>
    );
  }

  if (isError || !cart) {
    return (
      <EmptyState
        title="장바구니를 불러오지 못했습니다"
        action={<Button onClick={() => refetch()}>다시 시도</Button>}
      />
    );
  }

  return (
    <div>
      <h1 className="text-xl font-bold">장바구니</h1>

      {items.length === 0 ? (
        <div className="mt-6">
          <EmptyState
            title="장바구니가 비어 있습니다"
            action={
              <Link to="/products">
                <Button>상품 보러 가기</Button>
              </Link>
            }
          />
        </div>
      ) : (
        <div className="mt-6 flex flex-col gap-6">
          <div className="rounded-lg border border-line bg-surface px-5">
            <div className="flex items-center gap-2 border-b border-line py-3">
              <input
                type="checkbox"
                aria-label="전체 선택"
                checked={isAllSelected}
                onChange={(event) => toggleAll(event.target.checked)}
                className="h-4 w-4 accent-content"
              />
              <span className="text-sm text-content-muted">전체 선택</span>
            </div>

            {items.map((item) => (
              <CartItemRow
                key={item.id}
                item={item}
                selected={effectiveSelected.has(item.id)}
                onSelectChange={(checked) => toggleOne(item.id, checked)}
                onQuantityChange={(quantity) => handleQuantityChange(item.id, quantity)}
                onRemove={() => setRemoving(item)}
                disabled={removeMutation.isPending}
              />
            ))}
          </div>

          <CartSummary
            selectedCount={effectiveSelected.size}
            totalAmount={sumSelectedSubtotal(items, effectiveSelected)}
            onOrder={handleOrder}
          />
        </div>
      )}

      <ConfirmDialog
        open={Boolean(removing)}
        onClose={() => setRemoving(undefined)}
        onConfirm={handleRemove}
        title="장바구니에서 삭제할까요?"
        description={removing ? `'${removing.title}'을(를) 장바구니에서 삭제합니다.` : ''}
        confirmLabel="삭제"
        pending={removeMutation.isPending}
      />
    </div>
  );
}
