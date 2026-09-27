import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  postAuthMessage,
  resetAuthChannelForTest,
  subscribeAuthMessage,
} from '@/utils/authChannel';

/** 실제 BroadcastChannel 은 jsdom 에 없어, 같은 이름끼리 메시지를 주고받는 최소 구현으로 대체한다. */
class FakeBroadcastChannel {
  private static registry = new Map<string, Set<FakeBroadcastChannel>>();
  private listeners = new Set<(event: MessageEvent) => void>();
  private readonly name: string;

  constructor(name: string) {
    this.name = name;
    const instances = FakeBroadcastChannel.registry.get(name) ?? new Set();
    instances.add(this);
    FakeBroadcastChannel.registry.set(name, instances);
  }

  postMessage(data: unknown) {
    const instances = FakeBroadcastChannel.registry.get(this.name) ?? new Set();
    instances.forEach((instance) => {
      if (instance === this) {
        return;
      }
      instance.listeners.forEach((listener) => listener({ data } as MessageEvent));
    });
  }

  addEventListener(_type: 'message', listener: (event: MessageEvent) => void) {
    this.listeners.add(listener);
  }

  removeEventListener(_type: 'message', listener: (event: MessageEvent) => void) {
    this.listeners.delete(listener);
  }

  close() {
    FakeBroadcastChannel.registry.get(this.name)?.delete(this);
  }
}

beforeEach(() => {
  vi.stubGlobal('BroadcastChannel', FakeBroadcastChannel);
  resetAuthChannelForTest();
});

afterEach(() => {
  resetAuthChannelForTest();
  vi.unstubAllGlobals();
});

describe('postAuthMessage() / subscribeAuthMessage()', () => {
  it('보낸 메시지가 다른 인스턴스(탭)에 도달한다', () => {
    // given
    const received: unknown[] = [];
    new FakeBroadcastChannel('groove-auth').addEventListener('message', (event) => {
      received.push(event.data);
    });

    // when
    postAuthMessage({ type: 'logout', reason: 'idle' });

    // then
    expect(received).toEqual([{ type: 'logout', reason: 'idle' }]);
  });

  it('subscribe 한 핸들러가 다른 탭에서 온 메시지를 받는다', () => {
    // given
    const handler = vi.fn();
    subscribeAuthMessage(handler);
    const otherTab = new FakeBroadcastChannel('groove-auth');

    // when
    otherTab.postMessage({ type: 'activity', at: 1 });

    // then
    expect(handler).toHaveBeenCalledWith({ type: 'activity', at: 1 });
  });

  it('구독을 해지하면 더 이상 메시지를 받지 않는다', () => {
    // given
    const handler = vi.fn();
    const unsubscribe = subscribeAuthMessage(handler);
    const otherTab = new FakeBroadcastChannel('groove-auth');

    // when
    unsubscribe();
    otherTab.postMessage({ type: 'logout' });

    // then
    expect(handler).not.toHaveBeenCalled();
  });

  it('BroadcastChannel 이 없는 환경에서는 post·subscribe 가 예외 없이 아무 일도 하지 않는다', () => {
    // given
    vi.stubGlobal('BroadcastChannel', undefined);
    resetAuthChannelForTest();
    const handler = vi.fn();

    // when
    const unsubscribe = subscribeAuthMessage(handler);

    // then
    expect(() => postAuthMessage({ type: 'logout' })).not.toThrow();
    expect(() => unsubscribe()).not.toThrow();
    expect(handler).not.toHaveBeenCalled();
  });
});
