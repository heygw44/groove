import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { PaymentMethodSection } from '@/components/order/PaymentMethodSection';

describe('PaymentMethodSection', () => {
  it('allowVirtualAccount 가 true 면 무통장입금을 포함한 5개 수단을 보여준다', () => {
    // given & when
    render(<PaymentMethodSection method="CARD" onChange={vi.fn()} allowVirtualAccount />);

    // then
    expect(screen.getByRole('radio', { name: '신용·체크카드' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: '토스페이' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: '네이버페이' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: '카카오페이' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: '무통장입금' })).toBeInTheDocument();
  });

  it('allowVirtualAccount 가 false 면 무통장입금을 숨기고 이유를 보여준다', () => {
    // given & when
    render(<PaymentMethodSection method="CARD" onChange={vi.fn()} allowVirtualAccount={false} />);

    // then
    expect(screen.queryByRole('radio', { name: '무통장입금' })).not.toBeInTheDocument();
    expect(screen.getByText('한정반은 무통장입금을 지원하지 않습니다.')).toBeInTheDocument();
  });

  it('다른 수단을 클릭하면 onChange 를 그 값으로 호출한다', async () => {
    // given
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<PaymentMethodSection method="CARD" onChange={onChange} allowVirtualAccount />);

    // when
    await user.click(screen.getByRole('radio', { name: '카카오페이' }));

    // then
    expect(onChange).toHaveBeenCalledWith('KAKAOPAY');
  });
});
