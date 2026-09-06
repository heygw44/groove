import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { CatalogImportJobStatusBadge } from '@/components/admin/catalog/CatalogImportJobStatusBadge';

describe('CatalogImportJobStatusBadge', () => {
  it('STARTED 면 accent 배지로 "진행 중" 을 보여준다', () => {
    // given & when
    render(<CatalogImportJobStatusBadge status="STARTED" />);

    // then
    expect(screen.getByText('진행 중')).toHaveClass('bg-accent-soft');
  });

  it('COMPLETED 면 success 배지로 "완료" 를 보여준다', () => {
    // given & when
    render(<CatalogImportJobStatusBadge status="COMPLETED" />);

    // then
    expect(screen.getByText('완료')).toHaveClass('bg-success-soft');
  });

  it('FAILED 면 danger 배지로 "실패" 를 보여준다', () => {
    // given & when
    render(<CatalogImportJobStatusBadge status="FAILED" />);

    // then
    expect(screen.getByText('실패')).toHaveClass('bg-danger-soft');
  });

  it('STOPPED 면 neutral 배지로 "중지됨" 을 보여준다', () => {
    // given & when
    render(<CatalogImportJobStatusBadge status="STOPPED" />);

    // then
    expect(screen.getByText('중지됨')).toHaveClass('bg-surface-muted');
  });

  it('문서에 없는 상태값(ABANDONED)이면 neutral 배지에 원문을 그대로 보여준다', () => {
    // given & when
    render(<CatalogImportJobStatusBadge status="ABANDONED" />);

    // then
    expect(screen.getByText('ABANDONED')).toHaveClass('bg-surface-muted');
  });

  it('타입에 없는 값이 서버에서 와도 UNKNOWN 처럼 neutral 배지로 방어한다', () => {
    // given & when
    render(<CatalogImportJobStatusBadge status="UNKNOWN" />);

    // then
    expect(screen.getByText('UNKNOWN')).toHaveClass('bg-surface-muted');
  });
});
