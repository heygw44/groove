import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { OrderCancelDialog } from '@/components/order/OrderCancelDialog';
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

describe('OrderCancelDialog', () => {
  it('결제 전이면 환불 안내 없이 바로 취소를 확정한다', async () => {
    // given
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    render(<OrderCancelDialog open onClose={vi.fn()} onConfirm={onConfirm} />);

    // when
    await user.click(screen.getByRole('button', { name: '주문취소' }));

    // then
    expect(onConfirm).toHaveBeenCalledWith(undefined);
  });

  it('카드 결제 완료(DONE)면 일반 환불 안내 문구를 보여준다', () => {
    // given & when
    render(
      <OrderCancelDialog
        open
        onClose={vi.fn()}
        onConfirm={vi.fn()}
        payment={buildPayment({ status: 'DONE' })}
      />,
    );

    // then
    expect(screen.getByText('결제금액은 결제수단으로 환불됩니다.')).toBeInTheDocument();
  });

  it('가상계좌 입금대기면 계좌를 닫을 뿐 환불이 필요 없다는 안내를 보여준다', () => {
    // given & when
    render(
      <OrderCancelDialog
        open
        onClose={vi.fn()}
        onConfirm={vi.fn()}
        payment={buildPayment({
          status: 'WAITING_FOR_DEPOSIT',
          virtualAccount: {
            bankCode: '020',
            accountNumber: '110123456789',
            customerName: '그루브',
            dueDate: '2026-09-15T00:00:00',
          },
        })}
      />,
    );

    // then
    expect(
      screen.getByText('발급된 입금 계좌를 닫습니다. 아직 입금 전이라 환불할 금액은 없습니다.'),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText('은행')).not.toBeInTheDocument();
  });

  it('가상계좌 부분취소(PARTIAL_CANCELED)여도 환불계좌 입력란을 보여준다', () => {
    // given & when
    render(
      <OrderCancelDialog
        open
        onClose={vi.fn()}
        onConfirm={vi.fn()}
        payment={buildPayment({
          status: 'PARTIAL_CANCELED',
          virtualAccount: {
            bankCode: '020',
            accountNumber: '110123456789',
            customerName: '그루브',
            dueDate: '2026-09-15T00:00:00',
          },
        })}
      />,
    );

    // then
    expect(screen.getByLabelText('은행')).toBeInTheDocument();
    expect(screen.queryByText('결제금액은 결제수단으로 환불됩니다.')).not.toBeInTheDocument();
  });

  it('가상계좌 입금완료(DONE)면 환불계좌 입력 없이는 취소를 막는다', async () => {
    // given
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    render(
      <OrderCancelDialog
        open
        onClose={vi.fn()}
        onConfirm={onConfirm}
        payment={buildPayment({
          status: 'DONE',
          virtualAccount: {
            bankCode: '020',
            accountNumber: '110123456789',
            customerName: '그루브',
            dueDate: '2026-09-15T00:00:00',
          },
        })}
      />,
    );

    // when
    await user.click(screen.getByRole('button', { name: '주문취소' }));

    // then
    expect(onConfirm).not.toHaveBeenCalled();
    expect(screen.getByText('환불계좌 정보를 모두 입력해주세요.')).toBeInTheDocument();
  });

  it('가상계좌 입금완료(DONE)면 환불계좌를 입력해야 취소를 확정한다', async () => {
    // given
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    render(
      <OrderCancelDialog
        open
        onClose={vi.fn()}
        onConfirm={onConfirm}
        payment={buildPayment({
          status: 'DONE',
          virtualAccount: {
            bankCode: '020',
            accountNumber: '110123456789',
            customerName: '그루브',
            dueDate: '2026-09-15T00:00:00',
          },
        })}
      />,
    );

    // when
    await user.selectOptions(screen.getByLabelText('은행'), '20');
    await user.type(screen.getByLabelText('계좌번호'), '110abc123456789');
    await user.type(screen.getByLabelText('예금주'), '김그루브');
    await user.click(screen.getByRole('button', { name: '주문취소' }));

    // then
    expect(onConfirm).toHaveBeenCalledWith(undefined, {
      bankCode: '20',
      accountNumber: '110123456789',
      holderName: '김그루브',
    });
  });
});
