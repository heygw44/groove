import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ToastProvider } from '@/components/common/Toast';
import { VirtualAccountNotice } from '@/components/order/VirtualAccountNotice';
import type { VirtualAccount } from '@/types/payment';

const buildVirtualAccount = (overrides: Partial<VirtualAccount> = {}): VirtualAccount => ({
  bankCode: '020',
  accountNumber: '110123456789',
  customerName: '그루브',
  dueDate: '2026-09-15T23:59:59',
  ...overrides,
});

const renderNotice = (virtualAccount: VirtualAccount, amount = 75600) =>
  render(
    <ToastProvider>
      <VirtualAccountNotice virtualAccount={virtualAccount} amount={amount} />
    </ToastProvider>,
  );

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('VirtualAccountNotice', () => {
  it('은행명·계좌번호·입금기한·금액을 보여준다', () => {
    // given & when
    renderNotice(buildVirtualAccount());

    // then
    expect(screen.getByText('우리은행')).toBeInTheDocument();
    expect(screen.getByText('110123456789')).toBeInTheDocument();
    expect(screen.getByText('예금주 그루브')).toBeInTheDocument();
    expect(screen.getByText('75,600원')).toBeInTheDocument();
  });

  it('모르는 은행 코드면 코드를 그대로 보여준다', () => {
    // given & when
    renderNotice(buildVirtualAccount({ bankCode: '99' }));

    // then
    expect(screen.getByText('99')).toBeInTheDocument();
  });

  it('복사 버튼을 누르면 계좌번호를 클립보드에 복사한다', async () => {
    // given
    const user = userEvent.setup();
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal('navigator', { ...navigator, clipboard: { writeText } });
    renderNotice(buildVirtualAccount());

    // when
    await user.click(screen.getByRole('button', { name: '복사' }));

    // then
    expect(writeText).toHaveBeenCalledWith('110123456789');
    expect(await screen.findByText('계좌번호를 복사했습니다.')).toBeInTheDocument();
  });
});
