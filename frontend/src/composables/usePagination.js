import { computed, unref } from 'vue'

/**
 * PageResponse(content/page/size/totalElements/totalPages/first/last) 기반 페이지네이션 표시 헬퍼.
 *
 * URL 쿼리 구동 방식이라 페이지 이동 자체는 호출부(라우터 push)가 담당하고, 이 composable 은
 * 0-based page 를 경계·번호 윈도우 같은 표시용 계산값으로 환산한다.
 *
 * @param {import('vue').Ref<object>|(() => object)} pageSource PageResponse 를 반환하는 ref/getter
 * @param {{window?: number}} [opts] window: 현재 페이지 좌우로 보여줄 번호 개수(기본 2)
 */
export function usePagination(pageSource, opts = {}) {
  const windowSize = opts.window ?? 2
  const page = computed(() => unref(pageSource) || {})

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
    // current 가 범위를 벗어나도(직접 URL ?page=99 등) 윈도우가 비지 않도록 클램프.
    const cur = Math.min(Math.max(0, current.value), total - 1)
    const start = Math.max(0, cur - windowSize)
    const end = Math.min(total - 1, cur + windowSize)
    const list = []
    for (let i = start; i <= end; i += 1) list.push(i)
    return list
  })

  return { current, totalPages, totalElements, isFirst, isLast, hasPages, pages }
}
