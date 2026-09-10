import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { AdminAuditLogTable } from '@/components/admin/audit/AdminAuditLogTable';
import type { AdminAuditLog } from '@/types/adminAuditLog';

const aggregationLog = (): AdminAuditLog =>
  ({
    id: 1,
    adminId: 2,
    adminNickname: '관리자',
    action: 'SALES_AGGREGATION_RUN',
    targetType: 'SALES_AGGREGATION',
    createdAt: '2026-09-11T10:00:00',
  });

describe('AdminAuditLogTable', () => {
  it('집계 실행 로그에 대상 id가 없으면 라벨만 표시하고 링크를 만들지 않는다', () => {
    // given
    const log = aggregationLog();

    // when
    render(
      <MemoryRouter>
        <AdminAuditLogTable logs={[log]} />
      </MemoryRouter>,
    );

    // then
    expect(screen.getByText('매출 집계 실행')).toBeInTheDocument();
    const targetCell = screen.getByText('매출 집계').closest('td');
    expect(targetCell).not.toBeNull();
    expect(within(targetCell as HTMLElement).queryByRole('link')).not.toBeInTheDocument();
    expect(document.body).not.toHaveTextContent('#null');
    expect(document.body).not.toHaveTextContent('#undefined');
  });

  it('숫자 PRODUCT 대상은 기존 편집 링크를 유지한다', () => {
    // given
    const log: AdminAuditLog = {
      id: 2,
      adminId: 2,
      adminNickname: '관리자',
      action: 'PRODUCT_UPDATE',
      targetType: 'PRODUCT',
      targetId: 42,
      createdAt: '2026-09-11T10:00:00',
    };

    // when
    render(
      <MemoryRouter>
        <AdminAuditLogTable logs={[log]} />
      </MemoryRouter>,
    );

    // then
    expect(screen.getByRole('link', { name: '상품 #42' })).toHaveAttribute(
      'href',
      '/admin/products/42/edit',
    );
  });
});
