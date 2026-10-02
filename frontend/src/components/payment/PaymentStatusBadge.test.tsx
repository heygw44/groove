import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { PaymentStatusBadge } from '@/components/payment/PaymentStatusBadge';

describe('PaymentStatusBadge', () => {
  it('status 가 DONE 이면 결제완료를 표시한다', () => {
    // given & when
    render(<PaymentStatusBadge status="DONE" />);

    // then
    expect(screen.getByText('결제완료')).toBeInTheDocument();
  });

  it('status 가 UNKNOWN 이면 결과 확인 중을 표시한다', () => {
    // given & when
    render(<PaymentStatusBadge status="UNKNOWN" />);

    // then
    expect(screen.getByText('결과 확인 중')).toBeInTheDocument();
  });

  it('status 가 CANCEL_REQUESTED 면 취소 처리 중을 표시한다', () => {
    // given & when
    render(<PaymentStatusBadge status="CANCEL_REQUESTED" />);

    // then
    expect(screen.getByText('취소 처리 중')).toBeInTheDocument();
  });
});
