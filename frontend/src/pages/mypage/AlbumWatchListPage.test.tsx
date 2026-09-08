import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ToastProvider } from '@/components/common/Toast';
import AlbumWatchListPage from '@/pages/mypage/AlbumWatchListPage';
import { useAuthStore } from '@/store/authStore';
import type { AlbumWatch } from '@/types/albumWatch';
import type { PageResponse } from '@/types/api';

vi.mock('@/api/albumWatch', () => ({
  getAlbumWatches: vi.fn(),
  addAlbumWatch: vi.fn(),
  removeAlbumWatch: vi.fn(),
}));

const buildWatch = (id: number): AlbumWatch => ({
  id,
  albumId: id,
  albumTitle: `앨범 ${id}`,
  createdAt: '2026-01-01T00:00:00',
});

const buildPage = (
  content: AlbumWatch[],
  overrides: Partial<PageResponse<AlbumWatch>> = {},
): PageResponse<AlbumWatch> => ({
  content,
  page: 0,
  size: 20,
  totalElements: content.length,
  totalPages: 1,
  ...overrides,
});

const renderPage = (initialPath = '/mypage/album-watches') => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  useAuthStore.setState({ accessToken: 'token', member: null, isBootstrapping: false });

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={[initialPath]}>
          <Routes>
            <Route path="/mypage/album-watches" element={<AlbumWatchListPage />} />
          </Routes>
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
};

afterEach(() => {
  vi.clearAllMocks();
});

describe('AlbumWatchListPage', () => {
  it('구독한 앨범이 없으면 빈 상태를 보여준다', async () => {
    // given
    const { getAlbumWatches } = await import('@/api/albumWatch');
    vi.mocked(getAlbumWatches).mockResolvedValue(buildPage([]));

    // when
    renderPage();

    // then
    expect(await screen.findByText('구독한 앨범이 없습니다')).toBeInTheDocument();
  });

  it('페이지 번호를 누르면 다음 페이지를 불러온다', async () => {
    // given
    const { getAlbumWatches } = await import('@/api/albumWatch');
    vi.mocked(getAlbumWatches).mockImplementation((params) => {
      const page = params.page ?? 0;
      const content = page === 0 ? [buildWatch(1)] : [buildWatch(2)];
      return Promise.resolve(buildPage(content, { page, totalElements: 2, totalPages: 2 }));
    });
    const user = userEvent.setup();

    // when
    renderPage();
    expect(await screen.findByText('앨범 1')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '2' }));

    // then
    expect(await screen.findByText('앨범 2')).toBeInTheDocument();
    expect(screen.queryByText('앨범 1')).not.toBeInTheDocument();
  });

  it('마지막 페이지의 마지막 한 건을 해지하면 이전 페이지로 내려간다', async () => {
    // given
    const { getAlbumWatches, removeAlbumWatch } = await import('@/api/albumWatch');
    vi.mocked(getAlbumWatches).mockImplementation((params) => {
      const page = params.page ?? 0;
      const content = page === 0 ? [buildWatch(1)] : [buildWatch(2)];
      return Promise.resolve(buildPage(content, { page, totalElements: 2, totalPages: 2 }));
    });
    vi.mocked(removeAlbumWatch).mockResolvedValue(undefined);
    const user = userEvent.setup();

    // when
    renderPage('/mypage/album-watches?page=1');
    expect(await screen.findByText('앨범 2')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '구독 해지' }));

    // then
    await waitFor(() => expect(removeAlbumWatch).toHaveBeenCalledWith(2));
    expect(await screen.findByText('앨범 1')).toBeInTheDocument();
  });
});
