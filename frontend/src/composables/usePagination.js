import { computed, toValue } from 'vue'

/** PageResponse 기반 페이지네이션 표시 계산값(경계·번호 윈도우). opts.window=좌우 번호 개수(기본 2). */
export function usePagination(pageSource, opts = {}) {
  const windowSize = opts.window ?? 2
  // toValue 로 ref·getter·plain 값을 모두 정규화.
  const page = computed(() => toValue(pageSource) || {})

  const current = computed(() => page.value.page ?? 0) // 0-based
  const totalPages = computed(() => page.value.totalPages ?? 0)
  const totalElements = computed(() => page.value.totalElements ?? 0)
  const isFirst = computed(() => page.value.first ?? current.value <= 0)
  const isLast = computed(() => page.value.last ?? current.value >= totalPages.value - 1)
  const hasPages = computed(() => totalPages.value > 1)

  /** 표시용 0-based 페이지 번호 윈도우 (현재 ±window, 경계 클램프). */
  const pages = computed(() => {
    const total = totalPages.value
    if (total <= 0) return []
    // current 를 0~total-1 로 클램프.
    const cur = Math.min(Math.max(0, current.value), total - 1)
    const start = Math.max(0, cur - windowSize)
    const end = Math.min(total - 1, cur + windowSize)
    const list = []
    for (let i = start; i <= end; i += 1) list.push(i)
    return list
  })

  return { current, totalPages, totalElements, isFirst, isLast, hasPages, pages }
}
