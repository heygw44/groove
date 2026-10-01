import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { RefundAccountFields } from '@/components/order/RefundAccountFields';
import { EMPTY_REFUND_ACCOUNT } from '@/utils/refundAccount';

describe('RefundAccountFields', () => {
  it('은행·계좌번호·예금주 입력란과 에러 문구를 보여준다', () => {
    // given & when
    render(
      <RefundAccountFields
        idPrefix="test"
        value={EMPTY_REFUND_ACCOUNT}
        onChange={vi.fn()}
        error="입력 오류"
      />,
    );

    // then
    expect(screen.getByLabelText('은행')).toBeInTheDocument();
    expect(screen.getByLabelText('계좌번호')).toBeInTheDocument();
    expect(screen.getByLabelText('예금주')).toBeInTheDocument();
    expect(screen.getByText('입력 오류')).toBeInTheDocument();
  });

  it('계좌번호에서 숫자가 아닌 문자를 걸러 변경 값을 전달한다', async () => {
    // given
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <RefundAccountFields idPrefix="test" value={EMPTY_REFUND_ACCOUNT} onChange={onChange} />,
    );

    // when
    await user.type(screen.getByLabelText('계좌번호'), 'a');
    await user.type(screen.getByLabelText('계좌번호'), '1');

    // then
    expect(onChange).toHaveBeenNthCalledWith(1, { ...EMPTY_REFUND_ACCOUNT, accountNumber: '' });
    expect(onChange).toHaveBeenNthCalledWith(2, { ...EMPTY_REFUND_ACCOUNT, accountNumber: '1' });
  });

  it('예금주와 은행 변경을 나머지 값을 유지한 채 전달한다', async () => {
    // given
    const user = userEvent.setup();
    const onChange = vi.fn();
    const value = { bankCode: '20', accountNumber: '123', holderName: '' };
    render(<RefundAccountFields idPrefix="test" value={value} onChange={onChange} />);

    // when
    await user.type(screen.getByLabelText('예금주'), '김');
    await user.selectOptions(screen.getByLabelText('은행'), '39');

    // then
    expect(onChange).toHaveBeenCalledWith({ ...value, holderName: '김' });
    expect(onChange).toHaveBeenCalledWith({ ...value, bankCode: '39' });
  });

  it('계좌번호는 64자, 예금주는 100자까지만 입력할 수 있다', () => {
    // given & when
    render(<RefundAccountFields idPrefix="test" value={EMPTY_REFUND_ACCOUNT} onChange={vi.fn()} />);

    // then
    expect(screen.getByLabelText('계좌번호')).toHaveAttribute('maxlength', '64');
    expect(screen.getByLabelText('예금주')).toHaveAttribute('maxlength', '100');
  });
});
