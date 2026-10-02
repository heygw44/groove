/** 마지막 자동 새로고침 시각(ms). 같은 탭에서 새로고침 루프를 막는 가드. */
export const CHUNK_RELOAD_KEY = 'groove:chunk-reload-at';

/** 이 시간 안에 다시 청크 로드가 실패하면 자동 새로고침하지 않고 수동 안내로 넘긴다. */
export const CHUNK_RELOAD_GUARD_MS = 10_000;

/** 배포 직후 오래된 청크를 참조하는 동적 import 실패는 새로고침으로만 해결된다. */
export function isChunkLoadError(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error);
  return (
    message.includes('Failed to fetch dynamically imported module') ||
    message.includes('Importing a module script failed')
  );
}

export function reloadPage(): void {
  window.location.reload();
}

/**
 * 최근 자동 새로고침 기록이 없거나 가드 시간이 지났으면 자동 새로고침해도 된다.
 * sessionStorage 를 쓸 수 없으면 가드도 없으므로 루프를 피하려고 false.
 */
export function canAutoReloadForChunkError(now = Date.now()): boolean {
  try {
    const last = Number(sessionStorage.getItem(CHUNK_RELOAD_KEY));
    return !Number.isFinite(last) || last <= 0 || now - last >= CHUNK_RELOAD_GUARD_MS;
  } catch {
    return false;
  }
}

/**
 * 새로고침 직전에 시각을 기록한다. 새로고침 후에도 청크가 없으면 가드에 걸려 수동 안내로 넘어간다.
 * 기록에 실패하면 루프를 막을 수 없으므로 false.
 */
export function markChunkReload(now = Date.now()): boolean {
  try {
    sessionStorage.setItem(CHUNK_RELOAD_KEY, String(now));
    return true;
  } catch {
    return false;
  }
}
