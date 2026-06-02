import { defineStore } from 'pinia'

// localStorage 키 — M14 와 동일하게 유지해 기존 세션 호환.
const K_ACCESS = 'groove.accessToken'
const K_REFRESH = 'groove.refreshToken'
const K_EMAIL = 'groove.email'

/** base64url JWT payload 를 UTF-8 안전하게 디코드. 실패 시 null. (M14 store.js 이식) */
function decodeJwt(token) {
  try {
    const payload = token.split('.')[1]
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    // base64url 은 '=' 패딩이 생략되므로 atob 가 던지지 않도록 4의 배수로 보정.
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=')
    const binary = atob(padded)
    const json = decodeURIComponent(
      Array.from(binary)
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    )
    return JSON.parse(json)
  } catch {
    return null
  }
}

/**
 * 인증 상태 스토어. 토큰을 localStorage 에 보관하는 순수 상태 컨테이너다.
 * 실제 login/refresh/logout HTTP 호출은 api/ 가 담당하고 여기 액션을 콜백한다.
 *
 * "현재 유저"는 액세스 토큰 JWT payload 디코드로 얻는다(추가 API 호출 없음).
 * 서버가 진짜 권한을 검증하므로 이 값은 네비바 표시·Admin 링크 노출용일 뿐이다.
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: localStorage.getItem(K_ACCESS),
    refreshToken: localStorage.getItem(K_REFRESH),
    email: localStorage.getItem(K_EMAIL),
  }),
  getters: {
    isAuthenticated: (state) => !!state.accessToken,
    /** @returns {{memberId:number|null, role:string, email:string|null, isAdmin:boolean}|null} */
    user: (state) => {
      if (!state.accessToken) return null
      const claims = decodeJwt(state.accessToken)
      if (!claims) return null
      const role = claims.role || 'USER' // "USER" | "ADMIN" (평문, ROLE_ 접두사 없음)
      const memberId = claims.sub == null ? null : Number(claims.sub)
      return {
        memberId: Number.isFinite(memberId) ? memberId : null, // 비정상 토큰 NaN 방어
        role,
        email: state.email,
        isAdmin: role === 'ADMIN',
      }
    },
    isAdmin() {
      return this.user?.isAdmin === true
    },
  },
  actions: {
    /** 로그인 성공: 토큰 + 입력 email 저장. tokens = 로그인/refresh 응답 형태. */
    login(tokens, email) {
      this.accessToken = tokens.accessToken
      this.refreshToken = tokens.refreshToken
      localStorage.setItem(K_ACCESS, tokens.accessToken)
      localStorage.setItem(K_REFRESH, tokens.refreshToken)
      if (email) {
        this.email = email
        localStorage.setItem(K_EMAIL, email)
      }
    },
    /** refresh 후 토큰만 교체(회전). email 은 유지한다. */
    updateTokens(tokens) {
      this.accessToken = tokens.accessToken
      this.refreshToken = tokens.refreshToken
      localStorage.setItem(K_ACCESS, tokens.accessToken)
      localStorage.setItem(K_REFRESH, tokens.refreshToken)
    },
    /** 클라이언트 상태 전체 clear. 서버측 refresh 폐기는 api/auth.logoutFlow 가 담당. */
    logout() {
      this.accessToken = null
      this.refreshToken = null
      this.email = null
      localStorage.removeItem(K_ACCESS)
      localStorage.removeItem(K_REFRESH)
      localStorage.removeItem(K_EMAIL)
    },
  },
})
