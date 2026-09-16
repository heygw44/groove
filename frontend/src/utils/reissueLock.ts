/*
 * 탭 간에 재발급을 직렬화해 서버 grace 경로를 덜 타게 한다. Web Locks API 가 없는
 * 브라우저에서는 그냥 fn() 을 실행한다 - 서버 grace 가 있어 필수는 아니다.
 */
export const withReissueLock = <T>(fn: () => Promise<T>): Promise<T> => {
  if (typeof navigator === 'undefined' || !navigator.locks) {
    return fn();
  }
  return navigator.locks.request('groove-reissue', fn);
};
