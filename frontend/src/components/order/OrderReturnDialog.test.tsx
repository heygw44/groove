import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { OrderReturnDialog } from '@/components/order/OrderReturnDialog';
import type { OrderPayment } from '@/types/payment';

const buildPayment = (overrides: Partial<OrderPayment> = {}): OrderPayment => ({
  paymentId: 1,
  method: '카드',
  status: 'DONE',
  amount: 27000,
  approvedAt: '2026-09-13T00:01:00',
  easyPayProvider: null,
  canceledAmount: 0,
  virtualAccount: null,
  ...overrides,
});

const virtualAccountPayment = buildPayment({
  virtualAccount: {
    bankCode: '020',
    accountNumber: '110123456789',
    customerName: '그루브',
    dueDate: '2026-09-15T00:00:00',
  },
});

describe('OrderReturnDialog', () => {
  it('카드 결제면 환불계좌 입력란 없이 사유만으로 반품을 확정한다', async () => {
    // given
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    render(
      <OrderReturnDialog open onClose={vi.fn()} onConfirm={onConfirm} payment={buildPayment()} />,
    );

    // when
    await user.type(screen.getByLabelText('반품 사유 (선택)'), '파손');
    await user.click(screen.getByRole('button', { name: '반품요청' }));

    // then
    expect(screen.queryByLabelText('은행')).not.toBeInTheDocument();
    expect(onConfirm).toHaveBeenCalledWith('파손');
  });

  it('가상계좌 결제에서 환불계좌를 비우면 에러를 보이고 확정하지 않는다', async () => {
    // given
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    render(
      <OrderReturnDialog
        open
        onClose={vi.fn()}
        onConfirm={onConfirm}
        payment={virtualAccountPayment}
      />,
    );

    // when
    await user.click(screen.getByRole('button', { name: '반품요청' }));

    // then
    expect(onConfirm).not.toHaveBeenCalled();
    expect(screen.getByText('환불계좌 정보를 모두 입력해주세요.')).toBeInTheDocument();
  });

  it('가상계좌 결제에서 환불계좌를 입력하면 정리한 계좌와 함께 확정한다', async () => {
    // given
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    render(
      <OrderReturnDialog
        open
        onClose={vi.fn()}
        onConfirm={onConfirm}
        payment={virtualAccountPayment}
      />,
    );

    // when
    await user.selectOptions(screen.getByLabelText('은행'), '20');
    await user.type(screen.getByLabelText('계좌번호'), '110abc123456789');
    await user.type(screen.getByLabelText('예금주'), ' 김그루브 ');
    await user.click(screen.getByRole('button', { name: '반품요청' }));

    // then
    expect(onConfirm).toHaveBeenCalledWith(undefined, {
      bankCode: '20',
      accountNumber: '110123456789',
      holderName: '김그루브',
    });
  });
});
