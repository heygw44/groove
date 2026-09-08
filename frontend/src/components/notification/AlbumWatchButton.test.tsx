import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { ToastProvider } from '@/components/common/Toast';
import { AlbumWatchButton } from '@/components/notification/AlbumWatchButton';
import { useAuthStore } from '@/store/authStore';

interface RenderOptions {
  isLoggedIn?: boolean;
  watched?: boolean;
}

const renderButton = ({ isLoggedIn = true, watched }: RenderOptions = {}) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
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
              element={
                <AlbumWatchButton albumId={10} albumTitle="Kind of Blue" watched={watched} />
              }
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
    renderButton({ watched: true });

    // then
    const button = screen.getByRole('button', { name: '알림 받는 중' });
    expect(button).toHaveAttribute('aria-pressed', 'true');
  });

  it('미구독이면 새 에디션 알림 받기 라벨과 aria-pressed=false 를 보여준다', () => {
    // given & when
    renderButton({ watched: false });

    // then
    const button = screen.getByRole('button', { name: '새 에디션 알림 받기' });
    expect(button).toHaveAttribute('aria-pressed', 'false');
  });

  it('비로그인이라 watched 가 없으면 미구독으로 보여준다', () => {
    // given & when
    renderButton({ isLoggedIn: false });

    // then
    const button = screen.getByRole('button', { name: '새 에디션 알림 받기' });
    expect(button).toHaveAttribute('aria-pressed', 'false');
  });

  it('비로그인 상태에서 클릭하면 로그인 페이지로 이동한다', async () => {
    // given
    const user = userEvent.setup();
    renderButton({ isLoggedIn: false });

    // when
    await user.click(screen.getByRole('button', { name: '새 에디션 알림 받기' }));

    // then
    expect(screen.getByText('로그인 페이지')).toBeInTheDocument();
  });
});
