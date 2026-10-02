import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { RootLayout } from '@/components/layout/RootLayout';
import { useAuthStore } from '@/store/authStore';
import type { Member, MemberRole } from '@/types/member';

vi.mock('@/components/admin/AdminIdleGuard', () => ({
  AdminIdleGuard: () => <div data-testid="admin-idle-guard" />,
}));
vi.mock('@/components/layout/Header', () => ({ Header: () => null }));
vi.mock('@/components/layout/Footer', () => ({ Footer: () => null }));
vi.mock('@/components/recommend/TasteOnboardingModal', () => ({
  TasteOnboardingModal: () => null,
}));

const memberOf = (role: MemberRole): Member => ({
  id: 1,
  email: 'user@groove.com',
  nickname: '레코드러버',
  role,
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00',
});

const renderStorefront = (member: Member | null) => {
  useAuthStore.setState({ accessToken: member ? 'token' : null, member, isBootstrapping: false });
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route element={<RootLayout />}>
          <Route index element={<p>홈</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
};

describe('RootLayout', () => {
  afterEach(() => {
    useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: true });
  });

  it('관리자는 스토어 화면에서도 유휴 로그아웃 가드가 마운트된다', () => {
    // when
    renderStorefront(memberOf('ADMIN'));

    // then
    expect(screen.getAllByTestId('admin-idle-guard')).toHaveLength(1);
  });

  it('일반 회원이면 가드를 마운트하지 않는다', () => {
    // when
    renderStorefront(memberOf('USER'));

    // then
    expect(screen.queryByTestId('admin-idle-guard')).not.toBeInTheDocument();
  });

  it('비로그인이면 가드를 마운트하지 않는다', () => {
    // when
    renderStorefront(null);

    // then
    expect(screen.queryByTestId('admin-idle-guard')).not.toBeInTheDocument();
  });
});
