import { onUnmounted } from 'vue'

/**
 * setTimeout 재귀 폴링 — 언마운트/재시작 시 **진행 중이던 콜백이 새 타이머를 걸지 못하도록** 세대(generation)
 * 가드로 취소한다. 손으로 짠 timer/attempts/clear/onUnmounted 보일러플레이트(결제·배송·운송장 폴링 3곳)를
 * 한 곳으로 모아, "await 뒤 stop 된 컴포넌트가 계속 폴링하는" 좀비 폴링 버그를 구조적으로 막는다.
 *
 * @returns {{ start: (fn: (attempt:number)=>Promise<boolean>, opts?: { intervalMs?:number, maxAttempts?:number, immediate?:boolean, onExhausted?:()=>void }) => void, stop: () => void, active: () => boolean }}
 *   - `fn(attempt)`: 매 틱 실행. `true` 반환 시 폴링 종료(="완료"). throw 하면 미완료로 간주하고 계속한다.
 *   - `intervalMs`: 틱 간격(기본 1500). `maxAttempts`: 최대 시도(기본 Infinity). `immediate`: 첫 틱 즉시 실행(기본 false).
 *   - `onExhausted`: maxAttempts 도달까지 완료 못 했을 때 1회 호출.
 */
export function usePolling() {
  let timer = null
  let generation = 0 // start/stop 마다 증가 — 자신의 세대가 아닌 진행 중 콜백은 재스케줄을 포기한다.

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
        done = false // 일시 오류는 미완료로 보고 계속(중단 가드는 maxAttempts)
      }
      if (gen !== generation) return // await 동안 stop/restart/unmount → 폐기(좀비 방지)
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
