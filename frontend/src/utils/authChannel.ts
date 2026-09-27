import { AUTH_CHANNEL_NAME, type LoginReason } from '@/constants/authSession';

/** 탭 사이에 오가는 메시지. 토큰은 절대 싣지 않는다. */
export type AuthMessage =
  { type: 'activity'; at: number } | { type: 'logout'; reason?: LoginReason };

let channel: BroadcastChannel | null = null;

/* BroadcastChannel 이 없는 환경(구형 브라우저·jsdom)에서는 전파 없이 동작한다. */
const getChannel = () => {
  if (typeof BroadcastChannel === 'undefined') {
    return null;
  }
  channel ??= new BroadcastChannel(AUTH_CHANNEL_NAME);
  return channel;
};

/** 같은 인스턴스는 자기가 보낸 메시지를 받지 않으므로 보낸 탭에 되돌아오지 않는다. */
export const postAuthMessage = (message: AuthMessage) => {
  getChannel()?.postMessage(message);
};

export const subscribeAuthMessage = (handler: (message: AuthMessage) => void) => {
  const target = getChannel();
  if (!target) {
    return () => {};
  }
  const listener = (event: MessageEvent<AuthMessage>) => handler(event.data);
  target.addEventListener('message', listener);
  return () => target.removeEventListener('message', listener);
};

/** 테스트에서 가짜 BroadcastChannel 을 갈아 끼울 때만 쓴다. */
export const resetAuthChannelForTest = () => {
  channel?.close();
  channel = null;
};
