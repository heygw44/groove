import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';

import { Header } from '@/components/layout/Header';
import { cartKeys } from '@/hooks/queries/queryKeys';
import { useAuthStore } from '@/store/authStore';
import type { Member, MemberRole } from '@/types/member';

const memberOf = (role: MemberRole): Member => ({
  id: 1,
  email: 'user@groove.com',
  nickname: '레코드러버',
  role,
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00',
});

interface RenderOptions {
  member?: Member;
  cartItemCount?: number;
}

const renderHeader = ({ member, cartItemCount = 0 }: RenderOptions = {}) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  if (member) {
    useAuthStore.setState({ accessToken: 'token', member });
    queryClient.setQueryData(cartKeys.all, {
      items: Array.from({ length: cartItemCount }, (_, index) => ({ id: index })),
    });
  }
  useAuthStore.setState({ isBootstrapping: false });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/']}>
        <Header />
        <Routes>
          <Route path="/" element={<p>홈</p>} />
          <Route path="/products" element={<p>상품 목록</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

const openMenu = async (user: ReturnType<typeof userEvent.setup>) => {
  await user.click(screen.getByRole('button', { name: '메뉴 열기' }));
  return screen.getByRole('dialog');
};

afterEach(() => {
  useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: true });
});

describe('Header', () => {
  describe('모바일 메뉴', () => {
    it('열기 전에는 드로어가 없다', () => {
      // given & when
      renderHeader();

      // then
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      expect(screen.getByRole('button', { name: '메뉴 열기' })).toHaveAttribute(
        'aria-expanded',
        'false',
      );
    });

    it('비로그인이면 로그인·회원가입을 보여준다', async () => {
      // given
      const user = userEvent.setup();
      renderHeader();

      // when
      const drawer = await openMenu(user);

      // then
      expect(within(drawer).getByRole('link', { name: '로그인' })).toBeInTheDocument();
      expect(within(drawer).getByRole('link', { name: '회원가입' })).toBeInTheDocument();
      expect(within(drawer).queryByRole('button', { name: '로그아웃' })).not.toBeInTheDocument();
    });

    it('로그인 상태면 닉네임과 로그아웃을 보여준다', async () => {
      // given
      const user = userEvent.setup();
      renderHeader({ member: memberOf('USER') });

      // when
      const drawer = await openMenu(user);

      // then
      expect(within(drawer).getByText('레코드러버')).toBeInTheDocument();
      expect(within(drawer).getByRole('button', { name: '로그아웃' })).toBeInTheDocument();
      expect(within(drawer).queryByRole('link', { name: '로그인' })).not.toBeInTheDocument();
    });

    it('관리자에게만 관리자 링크를 보여준다', async () => {
      // given
      const user = userEvent.setup();
      renderHeader({ member: memberOf('ADMIN') });

      // when
      const drawer = await openMenu(user);

      // then
      expect(within(drawer).getByRole('link', { name: '관리자' })).toBeInTheDocument();
    });

    it('장바구니에 담긴 수를 라벨과 배지로 알린다', async () => {
      // given
      const user = userEvent.setup();
      renderHeader({ member: memberOf('USER'), cartItemCount: 3 });

      // when
      const drawer = await openMenu(user);

      // then
      const cartLink = within(drawer).getByRole('link', { name: '장바구니 3개' });
      expect(within(cartLink).getByText('3')).toBeInTheDocument();
    });

    it('메뉴에서 링크를 누르면 이동하면서 드로어가 닫힌다', async () => {
      // given
      const user = userEvent.setup();
      renderHeader();
      const drawer = await openMenu(user);

      // when
      await user.click(within(drawer).getByRole('link', { name: '상품' }));

      // then
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
  });
});
