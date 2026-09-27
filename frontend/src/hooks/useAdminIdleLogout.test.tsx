import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { logout } from '@/api/auth';
import { useAdminIdleLogout } from '@/hooks/useAdminIdleLogout';
import { useAuthStore } from '@/store/authStore';
import type { Member } from '@/types/member';
import { postAuthMessage, subscribeAuthMessage, type AuthMessage } from '@/utils/authChannel';

vi.mock('@/api/auth', () => ({
  logout: vi.fn(),
}));

vi.mock('@/utils/authChannel', () => ({
  postAuthMessage: vi.fn(),
  subscribeAuthMessage: vi.fn(),
}));

const mockedLogout = vi.mocked(logout);
const mockedPostAuthMessage = vi.mocked(postAuthMessage);
const mockedSubscribeAuthMessage = vi.mocked(subscribeAuthMessage);

const MINUTE_MS = 60 * 1000;

const ADMIN: Member = {
  id: 1,
  email: 'admin@groove.shop',
  nickname: '관리자',
  role: 'ADMIN',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00',
};

/* fake timer + 등록된 프라미스(로그아웃 API 등)를 함께 흘려보낸다. */
const advance = async (ms: number) => {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
};

const activityMessagesOf = () =>
  mockedPostAuthMessage.mock.calls
    .map(([message]) => message)
    .filter((message) => message.type === 'activity');

describe('useAdminIdleLogout', () => {
  let receivedMessage: ((message: AuthMessage) => void) | undefined;
  let unsubscribe: ReturnType<typeof vi.fn>;
  let replaceSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.useFakeTimers();
    mockedLogout.mockResolvedValue(undefined);

    receivedMessage = undefined;
    unsubscribe = vi.fn();
    mockedSubscribeAuthMessage.mockImplementation((handler) => {
      receivedMessage = handler;
      return unsubscribe as () => void;
    });

    replaceSpy = vi.fn();
    vi.stubGlobal('location', {
      ...window.location,
      pathname: '/admin/orders',
      search: '',
      replace: replaceSpy,
    });

    useAuthStore.setState({ accessToken: 'access-token', member: ADMIN, isBootstrapping: false });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    useAuthStore.setState({ accessToken: null, member: null, isBootstrapping: true });
    vi.clearAllMocks();
    vi.useRealTimers();
  });

  it('14분 동안 활동이 없으면 경고 상태가 되고 남은 시간을 60초로 알려준다', async () => {
    // given
    const { result } = renderHook(() => useAdminIdleLogout());

    // when
    await advance(14 * MINUTE_MS);

    // then
    expect(result.current.warningOpen).toBe(true);
    expect(result.current.remainingSeconds).toBe(60);
  });

  it('15분 동안 활동이 없으면 로그아웃 처리 후 로그인 화면으로 이동한다', async () => {
    // given
    renderHook(() => useAdminIdleLogout());

    // when
    await advance(15 * MINUTE_MS);

    // then
    expect(mockedLogout).toHaveBeenCalledTimes(1);
    expect(mockedPostAuthMessage).toHaveBeenCalledWith({ type: 'logout', reason: 'idle' });
    expect(useAuthStore.getState().accessToken).toBeNull();
    expect(useAuthStore.getState().member).toBeNull();
    expect(replaceSpy).toHaveBeenCalledWith('/login?reason=idle&redirect=%2Fadmin%2Forders');
  });

  it('keydown 이 발생하면 만료 시점이 그만큼 미뤄진다', async () => {
    // given
    renderHook(() => useAdminIdleLogout());

    // when: 10분 시점에 활동이 발생한다
    await advance(10 * MINUTE_MS);
    await act(async () => {
      window.dispatchEvent(new KeyboardEvent('keydown'));
    });

    // then: 원래 만료 시점(15분)이 지나도 아직 로그아웃되지 않는다
    await advance(5 * MINUTE_MS);
    expect(mockedLogout).not.toHaveBeenCalled();

    // then: 활동 시점 기준 15분 뒤(25분)엔 로그아웃된다
    await advance(10 * MINUTE_MS);
    expect(mockedLogout).toHaveBeenCalledTimes(1);
  });

  it('1초 이내에 연속으로 일어난 활동은 activity 메시지를 한 번만 보낸다', async () => {
    // given
    renderHook(() => useAdminIdleLogout());
    await advance(2000); // 마운트 직후 스로틀 구간을 벗어난다

    // when
    await act(async () => {
      window.dispatchEvent(new KeyboardEvent('keydown'));
    });
    await advance(500);
    await act(async () => {
      window.dispatchEvent(new KeyboardEvent('keydown'));
    });

    // then
    expect(activityMessagesOf()).toHaveLength(1);
  });

  it('다른 탭에서 온 activity 메시지를 받으면 만료가 미뤄진다', async () => {
    // given
    const { result } = renderHook(() => useAdminIdleLogout());
    await advance(14 * MINUTE_MS);
    expect(result.current.warningOpen).toBe(true);

    // when
    act(() => {
      receivedMessage?.({ type: 'activity', at: Date.now() });
    });
    await advance(1000);

    // then: 경고가 풀리고, 원래 만료 시점(15분)을 지나도 로그아웃되지 않는다
    expect(result.current.warningOpen).toBe(false);
    await advance(1 * MINUTE_MS);
    expect(mockedLogout).not.toHaveBeenCalled();
  });

  it('절전 등으로 시계가 갑자기 튀면 연장 없이 즉시 만료된다', async () => {
    // given
    renderHook(() => useAdminIdleLogout());

    // when: 시스템 시계만 16분 뒤로 점프한 뒤(절전에서 깨어난 상황) 탭이 다시 보인다
    vi.setSystemTime(Date.now() + 16 * MINUTE_MS);
    Object.defineProperty(document, 'visibilityState', {
      value: 'visible',
      configurable: true,
    });
    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange'));
      await vi.advanceTimersByTimeAsync(0);
    });

    // then: 연장(activity) 없이 바로 만료 처리된다
    expect(activityMessagesOf()).toHaveLength(0);
    expect(mockedLogout).toHaveBeenCalledTimes(1);
    expect(mockedPostAuthMessage).toHaveBeenCalledWith({ type: 'logout', reason: 'idle' });
  });

  it('다른 탭에서 온 logout 메시지를 받으면 이 탭은 로그아웃 API 를 다시 호출하지 않는다', async () => {
    // given
    renderHook(() => useAdminIdleLogout());

    // when
    act(() => {
      receivedMessage?.({ type: 'logout', reason: 'expired' });
    });
    await advance(15 * MINUTE_MS);

    // then
    expect(mockedLogout).not.toHaveBeenCalled();
  });

  it('언마운트하면 인터벌과 구독이 정리되어 이후 로그아웃이 발생하지 않는다', async () => {
    // given
    const { unmount } = renderHook(() => useAdminIdleLogout());

    // when
    unmount();
    await advance(20 * MINUTE_MS);

    // then
    expect(unsubscribe).toHaveBeenCalledTimes(1);
    expect(mockedLogout).not.toHaveBeenCalled();
  });
});
