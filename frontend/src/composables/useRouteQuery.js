import { useRoute, useRouter } from 'vue-router'

/**
 * URL 쿼리 동기화 단일 진입점 — 기존 route.query 에 patch 를 병합해 push 한다.
 *
 * patch 의 값이 빈 문자열/undefined/null 이면 해당 키를 제거해 URL 을 깔끔히 유지한다.
 * 필터(CatalogFilters)·정렬·페이지(Pagination)가 모두 같은 병합 규칙을 공유하도록 해,
 * 각 컴포넌트가 router.push({query:{...route.query}}) 를 따로 손으로 짜던 중복을 없앤다.
 */
export function useRouteQuery() {
  const route = useRoute()
  const router = useRouter()

  function patchQuery(patch) {
    const next = { ...route.query }
    for (const [k, v] of Object.entries(patch)) {
      if (v === '' || v == null) delete next[k]
      else next[k] = v
    }
    router.push({ query: next })
  }

  return { route, router, patchQuery }
}
