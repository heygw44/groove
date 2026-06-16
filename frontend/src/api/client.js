import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import { toApiError } from '@/lib/problem-detail'
import { randomUuid } from '@/lib/uuid'

// API prefix (dev=Vite 프록시, prod=same-origin)
const BASE_URL = '/api/v1'
const REFRESH_URL = '/auth/refresh'

// withCredentials: same-origin 에서 HttpOnly refresh 쿠키 송수신
// 공통 요청 인스턴스. 응답 에러는 ApiError 로 정규화된다.
const client = axios.create({ baseURL: BASE_URL, withCredentials: true })

// refresh 전용 raw 인스턴스(인터셉터 재귀 차단)
const rawClient = axios.create({ baseURL: BASE_URL, withCredentials: true })

// 동시 401 의 중복 refresh 를 막는 in-flight 프로미스 공유
let refreshInFlight = null

/** refresh 쿠키로 1회 갱신. 성공 true / 실패 false. auth.hadSession 으로 게이트. */
export function tryRefresh() {
  if (refreshInFlight) return refreshInFlight
  const auth = useAuthStore()
  if (!auth.hadSession) return Promise.resolve(false)

  refreshInFlight = rawClient
    .post(REFRESH_URL)
    .then((res) => {
      auth.updateTokens(res.data) // 회전
      return true
    })
    .catch(() => false)
    .finally(() => {
      refreshInFlight = null
    })
  return refreshInFlight
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

// 응답 인터셉터: 401 → refresh 1회 + 원요청 1회 재시도. 그 외 에러는 ApiError 로 변환.
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
