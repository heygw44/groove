import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import { toApiError } from '@/lib/problem-detail'
import { randomUuid } from '@/lib/uuid'

// 같은 오리진 prefix. dev 는 Vite 프록시(5173→8080), prod 는 same-origin(8080).
const BASE_URL = '/api/v1'
const REFRESH_URL = '/auth/refresh'

// withCredentials: refresh 토큰 HttpOnly 쿠키를 송수신하기 위함(#163). 실제 배포는 same-origin
// (dev 는 Vite 프록시, prod 는 Spring 이 SPA 를 같은 오리진에서 서빙)이라 쿠키가 CORS 없이 동작한다.
// (cross-origin 배포로 가려면 서버 CORS 의 allow-credentials=true + 명시적 origin 화이트리스트가 추가로 필요 — 현재 형상엔 미적용.)
// refresh 쿠키 Path 가 /api/v1/auth 라 일반 API 호출엔 쿠키가 붙지 않는다.
// 공통 요청 인스턴스. 모든 응답 에러는 ApiError 로 정규화된다.
const client = axios.create({ baseURL: BASE_URL, withCredentials: true })

// refresh 호출은 인터셉터 재귀를 타지 않도록 별도 raw 인스턴스를 쓴다(refresh 가 refresh 를 부르는 루프 차단).
const rawClient = axios.create({ baseURL: BASE_URL, withCredentials: true })

// 동시 401 들이 refresh 를 중복 호출하지 않도록 in-flight 프로미스를 공유한다. (M14 핵심 자산)
let refreshInFlight = null

/**
 * refresh 쿠키(HttpOnly)로 1회 갱신. 성공 true / 실패 false.
 * 토큰은 쿠키가 운반하므로 body 가 비어 있다. JS 는 쿠키 존재 여부를 볼 수 없으므로,
 * "이전에 로그인함" 신호(auth.hadSession)로 게이트한다 — 한 번도 로그인한 적 없는 게스트가
 * 매 401 마다 무의미한 refresh 왕복을 하지 않도록 막는다(#163, 코드리뷰 #3).
 */
export function tryRefresh() {
  if (refreshInFlight) return refreshInFlight
  const auth = useAuthStore()
  if (!auth.hadSession) return Promise.resolve(false)

  refreshInFlight = rawClient
    .post(REFRESH_URL)
    .then((res) => {
      auth.updateTokens(res.data) // 회전: 구 refresh 쿠키는 서버가 새 쿠키로 교체
      return true
    })
    .catch(() => false)
    .finally(() => {
      refreshInFlight = null
    })
  return refreshInFlight
}

// 요청 인터셉터: Bearer 자동 첨부 + idempotent 옵션 시 Idempotency-Key.
// 커스텀 옵션은 호출부에서 config 로 전달한다: client.post(url, body, { auth:false, idempotent:true })
client.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (config.auth !== false && auth.accessToken) {
    config.headers.Authorization = `Bearer ${auth.accessToken}`
  }
  // 재시도(client(config)) 시 동일 config 가 재사용되므로, 키가 이미 있으면 재생성하지 않는다
  // — 401 refresh 재시도에서 멱등 키가 바뀌어 서버 dedup 이 깨지는 것을 막는다.
  if (config.idempotent && !config.headers['Idempotency-Key']) {
    config.headers['Idempotency-Key'] = randomUuid()
  }
  return config
})

// 응답 인터셉터: 401 → refresh 1회 + 원요청 1회 재시도(루프 안전). 그 외 에러는 ApiError 로 변환.
// 가드: 인증 요청이고, 아직 재시도 안 했고, refresh 호출 자신이 아니며, 이전에 로그인한 적이 있는 경우에만 발화한다.
// refresh 토큰은 HttpOnly 쿠키라 JS 가 존재를 못 보므로, auth.hadSession 으로 게스트의 불필요한 refresh 를 막는다(#163).
client.interceptors.response.use(
  (res) => {
    // 204/빈 본문을 일관되게 null 로 정규화한다. axios 는 빈 본문을 ''(빈 문자열)로 두지만,
    // 호출부는 M14 fetch 래퍼처럼 null 을 기대한다(예: DELETE 후 `== null` 분기·구조분해).
    if (res.data === '') res.data = null
    return res
  },
  async (error) => {
    const config = error.config || {}
    const auth = useAuthStore()
    const canRetry =
      error.response?.status === 401 &&
      config.auth !== false &&
      !config._retried &&
      config.url !== REFRESH_URL &&
      auth.hadSession

    if (canRetry) {
      const refreshed = await tryRefresh()
      if (refreshed) {
        config._retried = true
        return client(config)
      }
      auth.logout()
    }
    return Promise.reject(toApiError(error))
  },
)

export default client
