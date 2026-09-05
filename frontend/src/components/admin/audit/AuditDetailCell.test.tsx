import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { AuditDetailCell } from '@/components/admin/audit/AuditDetailCell';

describe('AuditDetailCell', () => {
  it('40자 이하면 그대로 보여주고 펼치기 토글이 없다', () => {
    // given
    const detail = 'ACTIVE->SUSPENDED (반복 어뷰징 신고)';

    // when
    render(<AuditDetailCell detail={detail} />);

    // then
    expect(screen.getByText(detail)).toBeInTheDocument();
    expect(document.querySelector('details')).not.toBeInTheDocument();
  });

  it('40자를 넘으면 앞부분만 보여주고 펼치기 토글로 전체를 보여준다', () => {
    // given
    const detail = 'A'.repeat(50);

    // when
    render(<AuditDetailCell detail={detail} />);

    // then
    const details = document.querySelector('details');
    expect(details).toBeInTheDocument();
    expect(screen.getByText(`${'A'.repeat(40)}…`)).toBeInTheDocument();
    expect(screen.getByText(detail)).toBeInTheDocument();
  });
});
