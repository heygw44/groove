import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import { toApiError } from '@/lib/problem-detail'
import { randomUuid } from '@/lib/uuid'

// 같은 오리진 prefix. dev 는 Vite 프록시(5173→8080), prod 는 same-origin(8080).
const BASE_URL = '/api/v1'
const REFRESH_URL = '/auth/refresh'

// 공통 요청 인스턴스. 모든 응답 에러는 ApiError 로 정규화된다.
const client = axios.create({ baseURL: BASE_URL })

// refresh 호출은 인터셉터 재귀를 타지 않도록 별도 raw 인스턴스를 쓴다(refresh 가 refresh 를 부르는 루프 차단).
const rawClient = axios.create({ baseURL: BASE_URL })

// 동시 401 들이 refresh 를 중복 호출하지 않도록 in-flight 프로미스를 공유한다. (M14 핵심 자산)
let refreshInFlight = null

/** 저장된 refresh 토큰으로 1회 갱신. 성공 true / 실패 false. */
function tryRefresh() {
  if (refreshInFlight) return refreshInFlight
  const auth = useAuthStore()
  if (!auth.refreshToken) return Promise.resolve(false)

  refreshInFlight = rawClient
    .post(REFRESH_URL, { refreshToken: auth.refreshToken })
    .then((res) => {
      auth.updateTokens(res.data) // 회전: 구 refresh 토큰은 서버가 폐기
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
// 가드: 인증 요청이고, 아직 재시도 안 했고, refresh 호출 자신이 아니며, refresh 토큰을 보유한 경우에만 발화.
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
      !!auth.refreshToken

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
