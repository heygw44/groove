import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';

import { GuestRoute } from '@/routes/GuestRoute';
import { useAuthStore } from '@/store/authStore';
import type { Member } from '@/types/member';

const member: Member = {
  id: 1,
  email: 'user@groove.com',
  nickname: '레코드러버',
  role: 'USER',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00',
};

const renderAt = (entry: string) =>
  render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route
          path="/login"
          element={
            <GuestRoute>
              <p>로그인 폼</p>
            </GuestRoute>
          }
        />
        <Route path="/" element={<p>홈</p>} />
        <Route path="/limited-drops/:id" element={<p>드롭 상세</p>} />
      </Routes>
    </MemoryRouter>,
  );

beforeEach(() => {
  useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: false });
});

describe('GuestRoute', () => {
  it('세션 복구 중이면 화면을 그리지 않는다', () => {
    // given
    useAuthStore.setState({ isBootstrapping: true });

    // when
    renderAt('/login');

    // then
    expect(screen.queryByText('로그인 폼')).not.toBeInTheDocument();
  });

  it('비로그인이면 화면을 그린다', () => {
    // when
    renderAt('/login?redirect=%2Flimited-drops%2F118');

    // then
    expect(screen.getByText('로그인 폼')).toBeInTheDocument();
  });

  it('로그인 상태면 redirect 대상으로 보낸다', () => {
    // given
    useAuthStore.setState({ accessToken: 't', member });

    // when
    renderAt('/login?redirect=%2Flimited-drops%2F118');

    // then
    expect(screen.getByText('드롭 상세')).toBeInTheDocument();
  });

  it('로그인 상태인데 redirect 가 외부 주소면 홈으로 보낸다', () => {
    // given
    useAuthStore.setState({ accessToken: 't', member });

    // when
    renderAt('/login?redirect=%2F%2Fevil.com');

    // then
    expect(screen.getByText('홈')).toBeInTheDocument();
  });
});
