import { renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { useAuthChannelSync } from '@/hooks/useAuthChannelSync';
import { queryClient } from '@/lib/queryClient';
import { useAuthStore } from '@/store/authStore';
import type { AuthMessage } from '@/utils/authChannel';
import { subscribeAuthMessage } from '@/utils/authChannel';

vi.mock('@/utils/authChannel', () => ({
  subscribeAuthMessage: vi.fn(() => () => {}),
}));

const mockedSubscribe = vi.mocked(subscribeAuthMessage);

/** 핸들러를 호출해서 실제 메시지가 왔을 때의 동작을 검증한다. */
const emit = (message: AuthMessage) => {
  const handler = mockedSubscribe.mock.calls[0][0];
  handler(message);
};

const setLocation = (pathname: string) => {
  const replace = vi.fn();
  Object.defineProperty(window, 'location', {
    value: { pathname, search: '', replace },
    writable: true,
    configurable: true,
  });
  return replace;
};

afterEach(() => {
  vi.clearAllMocks();
  useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: true });
});

describe('useAuthChannelSync()', () => {
  it('로그인 상태 탭이 logout 메시지를 받으면 스토어와 캐시를 비우고 reason 을 실어 /login 으로 이동한다', () => {
    // given
    const replace = setLocation('/mypage');
    const clearSpy = vi.spyOn(queryClient, 'clear');
    useAuthStore.setState({ accessToken: 'token', member: null });
    renderHook(() => useAuthChannelSync());

    // when
    emit({ type: 'logout', reason: 'idle' });

    // then
    expect(useAuthStore.getState().accessToken).toBeNull();
    expect(clearSpy).toHaveBeenCalled();
    expect(replace).toHaveBeenCalledWith('/login?reason=idle&redirect=%2Fmypage');
  });

  it('로그인하지 않은 탭(게스트)은 logout 메시지를 무시한다', () => {
    // given
    const replace = setLocation('/mypage');
    useAuthStore.setState({ accessToken: null });
    renderHook(() => useAuthChannelSync());

    // when
    emit({ type: 'logout' });

    // then
    expect(replace).not.toHaveBeenCalled();
  });

  it('activity 메시지는 무시한다', () => {
    // given
    const replace = setLocation('/mypage');
    useAuthStore.setState({ accessToken: 'token' });
    renderHook(() => useAuthChannelSync());

    // when
    emit({ type: 'activity', at: Date.now() });

    // then
    expect(replace).not.toHaveBeenCalled();
    expect(useAuthStore.getState().accessToken).toBe('token');
  });
});
