import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { PaymentStatusBadge } from '@/components/payment/PaymentStatusBadge';

describe('PaymentStatusBadge', () => {
  it('status 가 DONE 이면 결제완료를 표시하고 대사 대기 표기는 없다', () => {
    // given & when
    render(<PaymentStatusBadge status="DONE" />);

    // then
    expect(screen.getByText('결제완료')).toBeInTheDocument();
    expect(screen.queryByText('대사 대기')).not.toBeInTheDocument();
  });

  it('status 가 UNKNOWN 이면 결과 확인 중과 대사 대기를 함께 표시한다', () => {
    // given & when
    render(<PaymentStatusBadge status="UNKNOWN" />);

    // then
    expect(screen.getByText('결과 확인 중')).toBeInTheDocument();
    expect(screen.getByText('대사 대기')).toBeInTheDocument();
  });

  it('status 가 CANCEL_REQUESTED 면 취소 확인 중과 대사 대기를 함께 표시한다', () => {
    // given & when
    render(<PaymentStatusBadge status="CANCEL_REQUESTED" />);

    // then
    expect(screen.getByText('취소 확인 중')).toBeInTheDocument();
    expect(screen.getByText('대사 대기')).toBeInTheDocument();
  });
});
