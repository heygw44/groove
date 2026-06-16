import { onUnmounted } from 'vue'

/**
 * setTimeout 재귀 폴링 — 세대 가드로 언마운트/재시작 시 진행 중 콜백 취소.
 *  - fn(attempt): 매 틱 실행. true 반환 시 종료, throw 하면 미완료로 계속.
 *  - intervalMs: 틱 간격(기본 1500). maxAttempts: 최대 시도(기본 Infinity). immediate: 첫 틱 즉시 실행(기본 false).
 *  - onExhausted: maxAttempts 도달까지 완료 못 했을 때 1회 호출.
 */
export function usePolling() {
  let timer = null
  let generation = 0 // start/stop 마다 증가

  function stop() {
    generation += 1
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }

  function start(fn, { intervalMs = 1500, maxAttempts = Infinity, immediate = false, onExhausted } = {}) {
    stop() // 이전 루프 취소
    const gen = generation
    let attempts = 0

    const tick = async () => {
      if (gen !== generation) return
      attempts += 1
      let done = false
      try {
        done = await fn(attempts)
      } catch {
        done = false // 오류는 미완료로 보고 계속
      }
      if (gen !== generation) return // await 동안 stop/restart/unmount → 폐기
      if (done) return
      if (attempts >= maxAttempts) {
        onExhausted?.()
        return
      }
      timer = setTimeout(tick, intervalMs)
    }

    if (immediate) tick()
    else timer = setTimeout(tick, intervalMs)
  }

  onUnmounted(stop)

  return { start, stop, active: () => timer != null }
}
