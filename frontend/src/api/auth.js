import client from './client'
import { useAuthStore } from '@/stores/auth'

/** 로그인: 토큰 발급 후 스토어 저장. auth 엔드포인트라 Bearer 미첨부. */
export async function loginFlow(email, password) {
  const res = await client.post('/auth/login', { email, password }, { auth: false })
  useAuthStore().login(res.data, email)
  return res.data
}

/** 회원가입 후 곧바로 로그인. (#115 에서 폼 UI 연결) */
export async function signupFlow({ email, password, name, phone }) {
  await client.post('/auth/signup', { email, password, name, phone }, { auth: false })
  return loginFlow(email, password)
}

/** 로그아웃: 서버에 refresh 폐기 best-effort 요청 후 로컬 상태 clear. 호출 실패해도 로컬은 비운다. */
export async function logoutFlow() {
  const auth = useAuthStore()
  const refreshToken = auth.refreshToken
  if (refreshToken) {
    try {
      await client.post('/auth/logout', { refreshToken }, { auth: false })
    } catch {
      // 폐기 실패는 무시 — 로컬 로그아웃은 진행
    }
  }
  auth.logout()
}
