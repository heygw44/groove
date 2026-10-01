import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ToastProvider } from '@/components/common/Toast';
import {
  useCancelAdminOrderItem,
  useConfirmAdminOrderItems,
  useDeliverAdminOrderItems,
} from '@/hooks/mutations/useAdminOrderMutations';
import { useAdminOrderItemCount } from '@/hooks/queries/useAdminOrderItemCount';
import { useAdminOrderItems } from '@/hooks/queries/useAdminOrderItems';
import AdminOrdersPage from '@/pages/admin/AdminOrdersPage';
import type { AdminOrderItemSummary } from '@/types/adminOrder';

vi.mock('@/hooks/mutations/useAdminOrderMutations', () => ({
  useCancelAdminOrderItem: vi.fn(),
  useConfirmAdminOrderItems: vi.fn(),
  useDeliverAdminOrderItems: vi.fn(),
  useShipAdminOrderItems: vi.fn(),
}));

vi.mock('@/hooks/queries/useAdminOrderItems', () => ({ useAdminOrderItems: vi.fn() }));
vi.mock('@/hooks/queries/useAdminOrderItemCount', () => ({ useAdminOrderItemCount: vi.fn() }));
vi.mock('@/components/admin/AdminOrderDetailDrawer', () => ({
  AdminOrderDetailDrawer: () => null,
}));

const buildItem = (id: number): AdminOrderItemSummary => ({
  id,
  orderId: 1,
  productOrderNumber: `ORD-1-0${id}`,
  orderNumber: 'ORD-1',
  memberEmail: 'member@groove.com',
  productName: `레코드 ${id}`,
  quantity: 1,
  status: 'PREPARING',
  virtualAccountPayment: false,
  createdAt: '2026-09-13T00:00:00',
});

const mockList = (hasNext: boolean) => {
  vi.mocked(useAdminOrderItems).mockReturnValue({
    data: { content: [buildItem(1)], page: 0, size: 20, hasNext },
    isPending: false,
    isError: false,
    error: null,
    isPlaceholderData: false,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useAdminOrderItems>);
};

const mockCount = (state: Record<string, unknown>) => {
  const refetch = vi.fn();
  vi.mocked(useAdminOrderItemCount).mockReturnValue({
    data: undefined,
    isPending: false,
    isError: false,
    isPlaceholderData: false,
    isFetching: false,
    refetch,
    ...state,
  } as unknown as ReturnType<typeof useAdminOrderItemCount>);
  return { refetch };
};

const mutationStub = <T,>() =>
  ({ mutate: vi.fn(), isPending: false, reset: vi.fn() }) as unknown as T;

const LocationProbe = () => {
  const location = useLocation();
  return <div data-testid="location">{`${location.pathname}${location.search}`}</div>;
};

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/admin/orders']}>
      <ToastProvider>
        <AdminOrdersPage />
        <LocationProbe />
      </ToastProvider>
    </MemoryRouter>,
  );

describe('AdminOrdersPage 건수·페이지네이션', () => {
  beforeEach(() => {
    vi.mocked(useConfirmAdminOrderItems).mockReturnValue(mutationStub());
    vi.mocked(useDeliverAdminOrderItems).mockReturnValue(mutationStub());
    vi.mocked(useCancelAdminOrderItem).mockReturnValue(mutationStub());
  });

  afterEach(() => vi.clearAllMocks());

  it('건수 조회가 실패해도 목록 hasNext 로 다음 페이지로 이동할 수 있다', async () => {
    // given
    const user = userEvent.setup();
    mockList(true);
    mockCount({ isError: true });
    renderPage();

    // when
    const next = screen.getByRole('button', { name: '다음 페이지' });
    expect(next).toBeEnabled();
    await user.click(next);

    // then
    expect(screen.getByTestId('location')).toHaveTextContent('page=1');
  });

  it('건수 조회가 실패하면 안내와 다시 시도 버튼을 보이고 누르면 refetch 한다', async () => {
    // given
    const user = userEvent.setup();
    mockList(false);
    const count = mockCount({ isError: true });
    renderPage();

    // when
    expect(screen.getByText(/상품주문 건수를 불러오지 못했습니다/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '다시 시도' }));

    // then
    expect(count.refetch).toHaveBeenCalledTimes(1);
  });

  it('건수가 placeholder 면 불러오는 중을 보이고 이전 숫자는 숨긴다', () => {
    // given
    mockList(false);
    mockCount({ data: { totalElements: 77 }, isPlaceholderData: true });

    // when
    renderPage();

    // then
    expect(screen.getByText('불러오는 중…')).toBeInTheDocument();
    expect(screen.queryByText(/77/)).not.toBeInTheDocument();
  });

  it('건수를 불러오면 총 건수를 보인다', () => {
    // given
    mockList(false);
    mockCount({ data: { totalElements: 45 } });

    // when
    renderPage();

    // then
    expect(screen.getByText('상품주문 총 45건')).toBeInTheDocument();
  });
});
