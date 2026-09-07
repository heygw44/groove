import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';

import { ActiveFilterChips } from '@/components/product/ActiveFilterChips';
import { referenceKeys } from '@/hooks/queries/queryKeys';
import type { ProductListFilters } from '@/utils/productFilters';

const BASE_FILTERS: ProductListFilters = { sort: 'latest', page: 0 };

/* 참조 데이터를 캐시에 미리 넣어 두면 staleTime 안이라 네트워크를 타지 않는다. */
const renderChips = (filters: Partial<ProductListFilters>, onUpdate = vi.fn()) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  queryClient.setQueryData(referenceKeys.genres, [
    { id: 1, name: 'Jazz' },
    { id: 2, name: 'Rock' },
  ]);
  queryClient.setQueryData(referenceKeys.labels, [{ id: 7, name: 'Blue Note' }]);

  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  return render(
    <ActiveFilterChips
      filters={{ ...BASE_FILTERS, ...filters }}
      onUpdate={onUpdate}
      onClearAll={vi.fn()}
    />,
    { wrapper },
  );
};

describe('ActiveFilterChips', () => {
  it('걸린 필터가 없으면 아무것도 렌더하지 않는다', () => {
    // given & when
    const { container } = renderChips({});

    // then
    expect(container).toBeEmptyDOMElement();
  });

  it('정렬만 바뀐 상태는 필터로 치지 않는다', () => {
    // given & when
    const { container } = renderChips({ sort: 'priceAsc' });

    // then
    expect(container).toBeEmptyDOMElement();
  });

  it('최소 가격만 걸리면 이상으로 표시한다', () => {
    // given & when
    renderChips({ minPrice: 10000 });

    // then
    expect(screen.getByText('10,000원 이상')).toBeInTheDocument();
  });

  it('최대 가격만 걸리면 이하로 표시한다', () => {
    // given & when
    renderChips({ maxPrice: 50000 });

    // then
    expect(screen.getByText('50,000원 이하')).toBeInTheDocument();
  });

  it('장르 칩을 해제하면 그 장르만 빠진 목록으로 갱신한다', async () => {
    // given
    const user = userEvent.setup();
    const onUpdate = vi.fn();
    renderChips({ genreIds: [1, 2] }, onUpdate);

    // when
    await user.click(screen.getByRole('button', { name: 'Jazz 필터 해제' }));

    // then
    expect(onUpdate).toHaveBeenCalledWith({ genreIds: [2] });
  });

  it('마지막 장르 칩을 해제하면 장르 조건 자체를 없앤다', async () => {
    // given
    const user = userEvent.setup();
    const onUpdate = vi.fn();
    renderChips({ genreIds: [1] }, onUpdate);

    // when
    await user.click(screen.getByRole('button', { name: 'Jazz 필터 해제' }));

    // then
    expect(onUpdate).toHaveBeenCalledWith({ genreIds: undefined });
  });

  it('가격 칩을 해제하면 최소·최대를 함께 지운다', async () => {
    // given
    const user = userEvent.setup();
    const onUpdate = vi.fn();
    renderChips({ minPrice: 10000, maxPrice: 50000 }, onUpdate);

    // when
    await user.click(screen.getByRole('button', { name: '10,000원 ~ 50,000원 필터 해제' }));

    // then
    expect(onUpdate).toHaveBeenCalledWith({ minPrice: undefined, maxPrice: undefined });
  });

  it('국가 칩을 표시하고 해제하면 국가 조건을 없앤다', async () => {
    // given
    const user = userEvent.setup();
    const onUpdate = vi.fn();
    renderChips({ country: 'Japan' }, onUpdate);

    // when
    expect(screen.getByText('국가: 일본')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '국가: 일본 필터 해제' }));

    // then
    expect(onUpdate).toHaveBeenCalledWith({ country: undefined });
  });

  it('에디션 칩을 표시하고 해제하면 에디션 조건을 없앤다', async () => {
    // given
    const user = userEvent.setup();
    const onUpdate = vi.fn();
    renderChips({ editionType: 'REISSUE' }, onUpdate);

    // when
    expect(screen.getByText('에디션: 재발매반')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '에디션: 재발매반 필터 해제' }));

    // then
    expect(onUpdate).toHaveBeenCalledWith({ editionType: undefined });
  });

  it('프레싱 연도 범위가 모두 있으면 물결로 이어 표시한다', () => {
    // given & when
    renderChips({ pressingYearFrom: 1959, pressingYearTo: 1970 });

    // then
    expect(screen.getByText('제작 연도: 1959~1970')).toBeInTheDocument();
  });

  it('프레싱 연도 시작만 있으면 이후로 표시한다', () => {
    // given & when
    renderChips({ pressingYearFrom: 1959 });

    // then
    expect(screen.getByText('제작 연도: 1959 이후')).toBeInTheDocument();
  });

  it('프레싱 연도 종료만 있으면 이전으로 표시한다', () => {
    // given & when
    renderChips({ pressingYearTo: 1970 });

    // then
    expect(screen.getByText('제작 연도: 1970 이전')).toBeInTheDocument();
  });

  it('프레싱 연도 칩을 해제하면 시작·종료를 함께 지운다', async () => {
    // given
    const user = userEvent.setup();
    const onUpdate = vi.fn();
    renderChips({ pressingYearFrom: 1959, pressingYearTo: 1970 }, onUpdate);

    // when
    await user.click(screen.getByRole('button', { name: '제작 연도: 1959~1970 필터 해제' }));

    // then
    expect(onUpdate).toHaveBeenCalledWith({
      pressingYearFrom: undefined,
      pressingYearTo: undefined,
    });
  });
});
