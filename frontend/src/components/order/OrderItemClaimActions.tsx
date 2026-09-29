import { useState } from 'react';

import { Button } from '@/components/common/Button';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { useToast } from '@/components/common/toastContext';
import { OrderCancelDialog } from '@/components/order/OrderCancelDialog';
import { OrderReturnDialog } from '@/components/order/OrderReturnDialog';
import {
  useCancelOrder,
  useCancelOrderItem,
  useConfirmOrderItem,
  useReturnOrderItem,
  useWithdrawOrderClaim,
} from '@/hooks/mutations/useOrderMutations';
import type { OrderItem, RefundAccount } from '@/types/order';
import type { OrderPayment } from '@/types/payment';
import { getErrorMessage } from '@/utils/apiError';
import { getOrderCancelSuccessMessage } from '@/utils/paymentStatus';

type OpenDialog = 'cancel' | 'return' | 'withdraw' | 'confirm' | null;

interface OrderItemClaimActionsProps {
  orderId: number;
  item: OrderItem;
  /** 가상계좌 결제면 취소 시 환불계좌를 받기 위해 필요하다. */
  payment?: OrderPayment;
}

interface CancelDialogTextParams {
  isAwaitingDeposit: boolean;
  isImmediateCancel: boolean;
  pending: boolean;
}

const getCancelDialogProps = ({
  isAwaitingDeposit,
  isImmediateCancel,
  pending,
}: CancelDialogTextParams) => {
  if (isAwaitingDeposit) {
    return {
      pending,
      title: '주문을 취소하시겠습니까?',
      description: '입금 전 주문은 주문 전체가 취소됩니다.',
      confirmLabel: '상품 취소',
    };
  }
  if (isImmediateCancel) {
    return {
      pending,
      title: '상품을 취소하시겠습니까?',
      description: '취소하면 되돌릴 수 없습니다. 이 상품의 결제 금액만 환불됩니다.',
      confirmLabel: '상품 취소',
    };
  }
  return {
    pending,
    title: '취소를 요청하시겠습니까?',
    description: '배송 준비 중인 상품은 확인 후 취소됩니다. 요청은 수거 전까지 철회할 수 있습니다.',
    confirmLabel: '취소 요청',
  };
};

/**
 * 상품주문 하나의 취소·취소 요청·반품 요청·요청 철회·구매확정 버튼과 확인 다이얼로그.
 * 어떤 버튼을 보일지는 서버가 내려준 `availableActions` 만 따른다.
 */
export function OrderItemClaimActions({ orderId, item, payment }: OrderItemClaimActionsProps) {
  const { showToast } = useToast();
  const [openDialog, setOpenDialog] = useState<OpenDialog>(null);
  const cancelMutation = useCancelOrderItem();
  const cancelOrderMutation = useCancelOrder();
  const returnMutation = useReturnOrderItem();
  const withdrawMutation = useWithdrawOrderClaim();
  const confirmMutation = useConfirmOrderItem();

  const { availableActions, claimId } = item;
  const isImmediateCancel = availableActions.includes('CANCEL');
  // 입금 전 가상계좌는 부분 취소가 안 돼 주문 전체를 취소한다.
  const isAwaitingDeposit = item.status === 'PAYMENT_WAITING';
  const canCancel = isImmediateCancel || availableActions.includes('CANCEL_REQUEST');
  const canReturn = availableActions.includes('RETURN_REQUEST');
  const canWithdraw = availableActions.includes('WITHDRAW_CLAIM') && claimId !== undefined;
  const canConfirm = availableActions.includes('CONFIRM');

  const closeDialog = () => setOpenDialog(null);

  const cancelDialogProps = getCancelDialogProps({
    isAwaitingDeposit,
    isImmediateCancel,
    pending: isAwaitingDeposit ? cancelOrderMutation.isPending : cancelMutation.isPending,
  });

  const callbacks = (successMessage: string) => ({
    onSuccess: () => {
      showToast('success', successMessage);
      closeDialog();
    },
    onError: (error: unknown) => {
      showToast('error', getErrorMessage(error));
    },
  });

  const handleCancel = (reason?: string, refundAccount?: RefundAccount) => {
    if (isAwaitingDeposit) {
      cancelOrderMutation.mutate(
        { orderId, reason, refundAccount },
        {
          onSuccess: (response) => {
            showToast('success', getOrderCancelSuccessMessage(response.payment?.status));
            closeDialog();
          },
          onError: (error: unknown) => {
            showToast('error', getErrorMessage(error));
          },
        },
      );
      return;
    }
    const successMessage = isImmediateCancel
      ? '상품을 취소했습니다.'
      : '취소 요청이 접수됐습니다. 승인되면 취소됩니다.';
    cancelMutation.mutate(
      { orderId, itemId: item.id, reason, refundAccount },
      callbacks(successMessage),
    );
  };

  const handleReturn = (reason?: string) => {
    returnMutation.mutate(
      { orderId, itemId: item.id, reason },
      callbacks('반품 요청이 접수됐습니다.'),
    );
  };

  const handleWithdraw = () => {
    if (claimId === undefined) {
      return;
    }
    withdrawMutation.mutate(claimId, callbacks('요청을 철회했습니다.'));
  };

  const handleConfirm = () => {
    confirmMutation.mutate({ orderId, itemId: item.id }, callbacks('구매를 확정했습니다.'));
  };

  return (
    <>
      {canCancel && (
        <Button variant="secondary" size="sm" onClick={() => setOpenDialog('cancel')}>
          {isImmediateCancel ? '상품 취소' : '취소 요청'}
        </Button>
      )}
      {canReturn && (
        <Button variant="secondary" size="sm" onClick={() => setOpenDialog('return')}>
          반품 요청
        </Button>
      )}
      {canWithdraw && (
        <Button variant="secondary" size="sm" onClick={() => setOpenDialog('withdraw')}>
          요청 철회
        </Button>
      )}
      {canConfirm && (
        <Button variant="primary" size="sm" onClick={() => setOpenDialog('confirm')}>
          구매확정
        </Button>
      )}

      <OrderCancelDialog
        open={openDialog === 'cancel'}
        onClose={closeDialog}
        onConfirm={handleCancel}
        payment={payment}
        {...cancelDialogProps}
      />
      <OrderReturnDialog
        open={openDialog === 'return'}
        onClose={closeDialog}
        onConfirm={handleReturn}
        pending={returnMutation.isPending}
      />
      <ConfirmDialog
        open={openDialog === 'withdraw'}
        onClose={closeDialog}
        onConfirm={handleWithdraw}
        pending={withdrawMutation.isPending}
        title="요청을 철회하시겠습니까?"
        description="취소·반품 요청을 거두고 주문 상태를 그대로 유지합니다."
        confirmLabel="요청 철회"
      />
      <ConfirmDialog
        open={openDialog === 'confirm'}
        onClose={closeDialog}
        onConfirm={handleConfirm}
        pending={confirmMutation.isPending}
        title="구매를 확정하시겠습니까?"
        description="구매확정 후에는 취소·반품을 요청할 수 없습니다."
        confirmLabel="구매확정"
        variant="primary"
      />
    </>
  );
}
