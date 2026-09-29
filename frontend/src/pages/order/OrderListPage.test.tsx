import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { useOrders } from '@/hooks/queries/useOrders';
import OrderListPage from '@/pages/order/OrderListPage';

vi.mock('@/hooks/queries/useOrders', () => ({
  useOrders: vi.fn(),
}));

const mockOrders = () => {
  vi.mocked(useOrders).mockReturnValue({
    data: { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 },
    isPending: false,
    isError: false,
    error: null,
    isPlaceholderData: false,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useOrders>);
};

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/orders']}>
      <Routes>
        <Route path="/orders" element={<OrderListPage />} />
      </Routes>
    </MemoryRouter>,
  );

afterEach(() => {
  vi.clearAllMocks();
});

describe('OrderListPage', () => {
  it('상태 탭을 누르면 statusGroup 쿼리로 목록을 다시 요청한다', async () => {
    // given
    const user = userEvent.setup();
    mockOrders();
    renderPage();

    // when
    await user.click(screen.getByRole('button', { name: '결제완료' }));

    // then
    expect(useOrders).toHaveBeenLastCalledWith(
      expect.objectContaining({ statusGroup: 'PAID', page: 0 }),
    );
  });

  it('탭을 바꾸면 이전 페이지 번호는 첫 페이지로 되돌아간다', async () => {
    // given
    const user = userEvent.setup();
    mockOrders();
    renderPage();

    // when
    await user.click(screen.getByRole('button', { name: '취소·반품' }));

    // then
    expect(useOrders).toHaveBeenLastCalledWith(
      expect.objectContaining({ statusGroup: 'CANCEL_RETURN', page: 0 }),
    );
  });
});
