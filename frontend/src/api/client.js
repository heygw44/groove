import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'
import { toApiError } from '@/lib/problem-detail'
import { randomUuid } from '@/lib/uuid'

// API prefix (dev=Vite 프록시, prod=same-origin)
const BASE_URL = '/api/v1'
const REFRESH_URL = '/auth/refresh'

// refresh 3-상태 결과 — 호출자가 logout/redirect 여부를 결정한다.
//  ok       : 갱신 성공(토큰 회전)
//  invalid  : 세션 무효(401·비-401 4xx, 또는 hadSession 없음) → 진짜 logout
//  transient: 일시 장애(네트워크 status 0·5xx) → logout 보류, 나중에 재시도 허용
export const REFRESH_OK = 'ok'
export const REFRESH_INVALID = 'invalid'
export const REFRESH_TRANSIENT = 'transient'

// 일시 장애 1회 재시도 전 대기(ms).
const RETRY_BACKOFF_MS = 500

// withCredentials: same-origin 에서 HttpOnly refresh 쿠키 송수신
// 공통 요청 인스턴스. 응답 에러는 ApiError 로 정규화된다.
const client = axios.create({ baseURL: BASE_URL, withCredentials: true })

// refresh 전용 raw 인스턴스(인터셉터 재귀 차단)
const rawClient = axios.create({ baseURL: BASE_URL, withCredentials: true })

// 동시 401 의 중복 refresh 를 막는 in-flight 프로미스 공유(재시도도 이 안에 포함)
let refreshInFlight = null

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

// 일시 장애 판별 — 네트워크 끊김(status 0) 또는 5xx 만 transient.
// 그 외 응답(401 포함 4xx)은 세션/요청 자체가 거부된 것이므로 transient 아님.
function isTransientStatus(status) {
  return status === 0 || status >= 500
}

/** /auth/refresh 1회 POST. 결과를 REFRESH_OK | REFRESH_INVALID | REFRESH_TRANSIENT 로 분류. */
async function attemptRefresh() {
  try {
    const res = await rawClient.post(REFRESH_URL)
    const auth = useAuthStore()
    auth.updateTokens(res.data) // 회전
    return REFRESH_OK
  } catch (error) {
    const status = error.response ? error.response.status : 0
    // 네트워크/5xx → 일시 장애, 401(및 비-401 4xx) → 무효
    return isTransientStatus(status) ? REFRESH_TRANSIENT : REFRESH_INVALID
  }
}

/**
 * refresh 쿠키로 1회 갱신. REFRESH_OK | REFRESH_INVALID | REFRESH_TRANSIENT 반환.
 * hadSession 없으면 invalid(복원할 세션 없음). transient 면 backoff 후 1회만 재시도.
 * in-flight 프로미스를 공유해 동시 401 의 중복 refresh(재시도 포함)를 막는다.
 */
export function tryRefresh() {
  if (refreshInFlight) return refreshInFlight
  const auth = useAuthStore()
  if (!auth.hadSession) return Promise.resolve(REFRESH_INVALID)

  refreshInFlight = (async () => {
    let status = await attemptRefresh()
    // 일시 장애만 backoff 후 1회 재시도(401/invalid 는 재시도하지 않는다).
    if (status === REFRESH_TRANSIENT) {
      await sleep(RETRY_BACKOFF_MS)
      status = await attemptRefresh()
    }
    return status
  })().finally(() => {
    refreshInFlight = null
  })
  return refreshInFlight
}

/** 무효 세션 처리 — 보호 라우트에 머물고 있으면 로그인으로 보낸다(redirect 보존, 중복 방지). */
function redirectToLoginIfProtected() {
  const route = router.currentRoute.value
  // 이미 로그인 화면이면 재이동하지 않는다(중복 navigation·동시 401 가드).
  if (route.name === 'login') return
  // 현재 라우트가 인증 필수일 때만 강제 이동. 공개 페이지면 그대로 둔다.
  if (!route.meta?.requiresAuth) return
  // fullPath 는 same-origin 절대경로 → 가드와 동일하게 redirect 로 보존.
  router.replace({ name: 'login', query: { redirect: route.fullPath } }).catch(() => {})
}

// 요청 인터셉터: Bearer 자동 첨부 + idempotent 옵션 시 Idempotency-Key
client.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (config.auth !== false && auth.accessToken) {
    config.headers.Authorization = `Bearer ${auth.accessToken}`
  }
  // 키가 이미 있으면 재생성하지 않는다(재시도 시 동일 키 보존)
  if (config.idempotent && !config.headers['Idempotency-Key']) {
    config.headers['Idempotency-Key'] = randomUuid()
  }
  return config
})

// 응답 인터셉터: 401 → refresh 후 분기. ok=재시도 / invalid=logout+redirect / transient=세션 유지하고 에러 반환.
client.interceptors.response.use(
  (res) => {
    // 빈 본문을 null 로 정규화
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
      const status = await tryRefresh()
      if (status === REFRESH_OK) {
        config._retried = true
        return client(config) // 회전된 토큰으로 원요청 1회 재시도(요청 인터셉터가 Bearer 재첨부)
      }
      if (status === REFRESH_INVALID) {
        // 세션 무효 → 진짜 logout + 보호 라우트면 로그인으로.
        auth.logout()
        redirectToLoginIfProtected()
      }
      // transient: logout 하지 않는다(세션 보존 → 나중에 재-refresh 가능). 아래에서 에러만 반환.
    }
    return Promise.reject(toApiError(error))
  },
)

export default client
