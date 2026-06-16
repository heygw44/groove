import { useRoute, useRouter } from 'vue-router'

/** route.query 에 patch 를 병합해 push. 값이 빈 문자열/undefined/null 이면 해당 키 제거. */
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
