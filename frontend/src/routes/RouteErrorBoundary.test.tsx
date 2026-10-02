import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { RouteErrorBoundary } from '@/routes/RouteErrorBoundary';
import { CHUNK_RELOAD_GUARD_MS, CHUNK_RELOAD_KEY, reloadPage } from '@/utils/chunkReload';

vi.mock('@/utils/chunkReload', async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  reloadPage: vi.fn(),
}));

const CHUNK_ERROR = new TypeError(
  'Failed to fetch dynamically imported module: https://x/assets/a.js',
);

const renderThrowing = (error: Error) => {
  function Thrower(): never {
    throw error;
  }
  const router = createMemoryRouter([
    { path: '/', element: <Thrower />, errorElement: <RouteErrorBoundary /> },
  ]);
  return render(<RouterProvider router={router} />);
};

beforeEach(() => {
  sessionStorage.clear();
  vi.mocked(reloadPage).mockClear();
  vi.spyOn(console, 'error').mockImplementation(() => {});
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('RouteErrorBoundary', () => {
  it('청크 로드 실패면 시각을 기록하고 자동으로 새로고침한다', () => {
    // when
    renderThrowing(CHUNK_ERROR);

    // then
    expect(reloadPage).toHaveBeenCalledTimes(1);
    expect(sessionStorage.getItem(CHUNK_RELOAD_KEY)).not.toBeNull();
  });

  it('직전에 자동 새로고침했으면 다시 새로고침하지 않고 수동 안내를 보여준다', () => {
    // given
    sessionStorage.setItem(CHUNK_RELOAD_KEY, String(Date.now() - 1_000));

    // when
    renderThrowing(CHUNK_ERROR);

    // then
    expect(reloadPage).not.toHaveBeenCalled();
    expect(screen.getByText(/새로고침 후 다시 시도해주세요/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '새로고침' })).toBeInTheDocument();
  });

  it('자동 새로고침 기록이 가드 시간보다 오래됐으면 다시 자동 새로고침한다', () => {
    // given
    sessionStorage.setItem(CHUNK_RELOAD_KEY, String(Date.now() - CHUNK_RELOAD_GUARD_MS - 1_000));

    // when
    renderThrowing(CHUNK_ERROR);

    // then
    expect(reloadPage).toHaveBeenCalledTimes(1);
  });

  it('청크 로드 실패가 아니면 자동 새로고침하지 않는다', () => {
    // when
    renderThrowing(new Error('boom'));

    // then
    expect(reloadPage).not.toHaveBeenCalled();
    expect(screen.getByText('잠시 후 다시 시도해주세요.')).toBeInTheDocument();
  });
});
