// UUID v4 생성. crypto.randomUUID → getRandomValues → Math.random 순으로 폴백.
export function randomUuid() {
  const c = globalThis.crypto
  if (c?.randomUUID) return c.randomUUID()

  const bytes = new Uint8Array(16)
  if (c?.getRandomValues) {
    c.getRandomValues(bytes)
  } else {
    for (let i = 0; i < 16; i++) bytes[i] = Math.floor(Math.random() * 256)
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x40 // version 4
  bytes[8] = (bytes[8] & 0x3f) | 0x80 // variant 10xx

  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0'))
  return (
    hex.slice(0, 4).join('') +
    '-' +
    hex.slice(4, 6).join('') +
    '-' +
    hex.slice(6, 8).join('') +
    '-' +
    hex.slice(8, 10).join('') +
    '-' +
    hex.slice(10, 16).join('')
  )
}

// scope(예: 주문번호) 단위로 안정적인 멱등 키를 반환한다. sessionStorage 에 캐시해 같은 논리적 요청
// (재마운트·HMR·새로고침)이 같은 키를 재사용하도록 한다 — 토스 멱등키 권장(같은 요청은 키 재사용).
// sessionStorage 사용 불가(프라이빗 모드 등)면 매번 새 UUID 로 폴백한다.
export function idempotencyKeyFor(scope) {
  const storageKey = `idem:${scope}`
  try {
    const cached = sessionStorage.getItem(storageKey)
    if (cached) return cached
    const key = randomUuid()
    sessionStorage.setItem(storageKey, key)
    return key
  } catch {
    return randomUuid()
  }
}

// idempotencyKeyFor 로 저장한 키를 비운다 — 성공 후 호출해 같은 세션의 다음 제출이 replay 되지 않고 새 키를 쓰게 한다.
export function clearIdempotencyKey(scope) {
  try {
    sessionStorage.removeItem(`idem:${scope}`)
  } catch {
    /* sessionStorage 사용 불가 환경: 무시 */
  }
}
