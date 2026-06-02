// ProblemDetail(RFC 7807) → ApiError 구조화.
// (M14 src/main/resources/static/js/api.js 의 ApiError 를 axios 기반으로 이식)

/** 서버 ProblemDetail(code/title/detail/violations)을 담는 구조화된 에러. */
export class ApiError extends Error {
  constructor({ status, code, title, detail, violations, traceId }) {
    super(detail || title || `HTTP ${status}`)
    this.name = 'ApiError'
    this.status = status // 예: 401, 409, 422
    this.code = code // 예: "AUTH_001"
    this.title = title
    this.detail = detail
    this.violations = violations || [] // [{field, message}]
    this.traceId = traceId
  }

  /** 필드별 검증 메시지 맵 {field: message} — 폼 검증 표시용(#115 useForm 에서 활용). */
  fieldErrors() {
    const map = {}
    for (const v of this.violations) {
      if (v && v.field) map[v.field] = v.message
    }
    return map
  }
}

/**
 * axios 에러를 ApiError 로 변환한다. 서버 ProblemDetail 본문이 있으면 그 필드를,
 * 없으면(네트워크/타임아웃 등) 상태·메시지로 폴백한다.
 */
export function toApiError(error) {
  const res = error.response
  const problem = (res && res.data) || {}
  return new ApiError({
    status: res ? res.status : 0,
    code: problem.code,
    title: problem.title,
    detail: problem.detail || (res ? undefined : error.message),
    violations: problem.violations,
    traceId: problem.traceId,
  })
}

/**
 * 사용자 표시용 에러 메시지 추출 — ApiError 면 detail→title 우선, 그 외(또는 둘 다 빈 값)면 fallback.
 * 뷰들이 동일한 폴백 규칙을 공유하도록 단일 헬퍼로 제공한다.
 */
export function errorMessage(error, fallback) {
  return (error instanceof ApiError && (error.detail || error.title)) || fallback
}
