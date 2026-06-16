// route.query 값 헬퍼 — 중복 키(string[])·undefined 를 안전하게 정규화한다.

/** route.query 값을 단일 문자열로 정규화. 배열이면 첫 값, 없으면 ''. */
export function firstStr(v) {
  if (Array.isArray(v)) return v[0] ?? ''
  return v ?? ''
}

/** route.query.page → 0-based 정수 page 파라미터. 없거나 0·음수·비정수면 undefined(=0페이지). */
export function pageParam(q) {
  const p = Number(firstStr(q.page))
  return Number.isInteger(p) && p > 0 ? p : undefined
}
