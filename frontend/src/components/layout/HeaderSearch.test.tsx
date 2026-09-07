import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { HeaderSearch } from '@/components/layout/HeaderSearch';
import type { SearchSuggestions } from '@/types/search';

vi.mock('@/api/search', () => ({
  getSearchSuggestions: vi.fn(),
}));

const EMPTY_SUGGESTIONS: SearchSuggestions = { products: [], artists: [] };

const SUGGESTIONS: SearchSuggestions = {
  products: [{ id: 501, title: 'Kind of Blue', artistName: 'Miles Davis' }],
  artists: [{ id: 7, name: 'John Coltrane' }],
};

const renderHeaderSearch = () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/']}>
        <HeaderSearch />
        <Routes>
          <Route path="/" element={<Link to="/cart">다른 페이지로</Link>} />
          <Route path="/products" element={<p>상품 목록</p>} />
          <Route path="/products/:id" element={<p>상품 상세</p>} />
          <Route path="/cart" element={<p>장바구니</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

beforeEach(async () => {
  const { getSearchSuggestions } = await import('@/api/search');
  vi.mocked(getSearchSuggestions).mockResolvedValue(EMPTY_SUGGESTIONS);
});

afterEach(() => {
  vi.clearAllMocks();
});

describe('HeaderSearch', () => {
  it('두 글자 미만이면 제안 요청을 보내지 않는다', async () => {
    // given
    const { getSearchSuggestions } = await import('@/api/search');
    const user = userEvent.setup();
    renderHeaderSearch();

    // when
    await user.type(screen.getByRole('combobox'), 'a');
    await new Promise((resolve) => setTimeout(resolve, 400));

    // then
    expect(getSearchSuggestions).not.toHaveBeenCalled();
  });

  it('엔터를 치면 /products?keyword= 로 이동한다', async () => {
    // given
    const user = userEvent.setup();
    renderHeaderSearch();

    // when
    await user.type(screen.getByRole('combobox'), 'blue{Enter}');

    // then
    expect(await screen.findByText('상품 목록')).toBeInTheDocument();
    expect(screen.getByRole('combobox')).toHaveValue('');
  });

  it('상품 제안을 클릭하면 상품 상세로 이동한다', async () => {
    // given
    const { getSearchSuggestions } = await import('@/api/search');
    vi.mocked(getSearchSuggestions).mockResolvedValue(SUGGESTIONS);
    const user = userEvent.setup();
    renderHeaderSearch();

    // when
    await user.type(screen.getByRole('combobox'), 'blue');
    await user.click(await screen.findByText('Kind of Blue', undefined, { timeout: 2000 }));

    // then
    expect(await screen.findByText('상품 상세')).toBeInTheDocument();
  });

  it('아티스트 제안을 클릭하면 /products?artistId= 로 이동한다', async () => {
    // given
    const { getSearchSuggestions } = await import('@/api/search');
    vi.mocked(getSearchSuggestions).mockResolvedValue(SUGGESTIONS);
    const user = userEvent.setup();
    renderHeaderSearch();

    // when
    await user.type(screen.getByRole('combobox'), 'coltrane');
    await user.click(await screen.findByText('John Coltrane', undefined, { timeout: 2000 }));

    // then
    expect(await screen.findByText('상품 목록')).toBeInTheDocument();
  });

  it('아래 화살표로 첫 항목을 고르고 엔터를 치면 그 항목으로 이동한다', async () => {
    // given
    const { getSearchSuggestions } = await import('@/api/search');
    vi.mocked(getSearchSuggestions).mockResolvedValue(SUGGESTIONS);
    const user = userEvent.setup();
    renderHeaderSearch();
    const combobox = screen.getByRole('combobox');

    // when
    await user.type(combobox, 'blue');
    await screen.findByText('Kind of Blue', undefined, { timeout: 2000 });
    await user.keyboard('{ArrowDown}{Enter}');

    // then
    expect(await screen.findByText('상품 상세')).toBeInTheDocument();
  });

  it('Esc 를 누르면 드롭다운이 닫힌다', async () => {
    // given
    const { getSearchSuggestions } = await import('@/api/search');
    vi.mocked(getSearchSuggestions).mockResolvedValue(SUGGESTIONS);
    const user = userEvent.setup();
    renderHeaderSearch();

    // when
    await user.type(screen.getByRole('combobox'), 'blue');
    await screen.findByRole('listbox', undefined, { timeout: 2000 });
    await user.keyboard('{Escape}');

    // then
    await waitFor(() => expect(screen.queryByRole('listbox')).not.toBeInTheDocument());
  });

  it('라우트가 바뀌면 입력이 비워진다', async () => {
    // given
    const user = userEvent.setup();
    renderHeaderSearch();
    const combobox = screen.getByRole('combobox');
    await user.type(combobox, 'kind');
    expect(combobox).toHaveValue('kind');

    // when
    await user.click(screen.getByRole('link', { name: '다른 페이지로' }));

    // then
    expect(await screen.findByText('장바구니')).toBeInTheDocument();
    expect(combobox).toHaveValue('');
  });
});
