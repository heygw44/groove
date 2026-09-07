import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { ToastProvider } from '@/components/common/Toast';
import { AlbumWatchButton } from '@/components/notification/AlbumWatchButton';
import { albumWatchKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { AlbumWatchListResponse } from '@/types/albumWatch';

interface RenderOptions {
  isLoggedIn?: boolean;
  watches?: AlbumWatchListResponse;
}

const renderButton = ({ isLoggedIn = true, watches }: RenderOptions = {}) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  if (watches) {
    queryClient.setQueryData(albumWatchKeys.all, watches);
  }
  useAuthStore.setState({
    accessToken: isLoggedIn ? 'token' : null,
    member: null,
    isBootstrapping: false,
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={['/albums/10']}>
          <Routes>
            <Route
              path="/albums/:id"
              element={<AlbumWatchButton albumId={10} albumTitle="Kind of Blue" />}
            />
            <Route path="/login" element={<p>로그인 페이지</p>} />
          </Routes>
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
};

describe('AlbumWatchButton', () => {
  it('구독 중이면 알림 받는 중 라벨과 aria-pressed=true 를 보여준다', () => {
    // given & when
    renderButton({
      watches: { content: [{ id: 1, albumId: 10, albumTitle: 'Kind of Blue', createdAt: '2026-01-01T00:00:00' }] },
    });

    // then
    const button = screen.getByRole('button', { name: '알림 받는 중' });
    expect(button).toHaveAttribute('aria-pressed', 'true');
  });

  it('미구독이면 새 프레싱 알림 받기 라벨과 aria-pressed=false 를 보여준다', () => {
    // given & when
    renderButton({ watches: { content: [] } });

    // then
    const button = screen.getByRole('button', { name: '새 프레싱 알림 받기' });
    expect(button).toHaveAttribute('aria-pressed', 'false');
  });

  it('비로그인 상태에서 클릭하면 로그인 페이지로 이동한다', async () => {
    // given
    const user = userEvent.setup();
    renderButton({ isLoggedIn: false });

    // when
    await user.click(screen.getByRole('button', { name: '새 프레싱 알림 받기' }));

    // then
    expect(screen.getByText('로그인 페이지')).toBeInTheDocument();
  });
});
