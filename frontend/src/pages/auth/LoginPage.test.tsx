import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { useLogin } from '@/hooks/mutations/useAuthMutations';
import LoginPage from '@/pages/auth/LoginPage';

vi.mock('@/hooks/mutations/useAuthMutations', () => ({
  useLogin: vi.fn(),
}));

const renderPage = (search: string) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/login${search}`]}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

beforeEach(() => {
  vi.mocked(useLogin).mockReturnValue({ mutate: vi.fn() } as unknown as ReturnType<typeof useLogin>);
});

afterEach(() => {
  vi.clearAllMocks();
});

describe('LoginPage', () => {
  it('reason=password-changed 면 재로그인 안내를 보여준다', () => {
    // when
    renderPage('?reason=password-changed');

    // then
    expect(screen.getByRole('status')).toHaveTextContent(
      '비밀번호가 변경되어 다시 로그인해 주세요.',
    );
  });

  it('reason 이 없으면 안내를 보여주지 않는다', () => {
    // when
    renderPage('');

    // then
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('알 수 없는 reason 이면 안내를 보여주지 않는다', () => {
    // when
    renderPage('?reason=unknown');

    // then
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });
});
