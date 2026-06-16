import { defineStore } from 'pinia'

// email 은 navbar 표시 전용으로 localStorage 에 저장 (토큰 아님).
const K_EMAIL = 'groove.email'

// "이전에 로그인함" 신호 — 부팅 복원·401 refresh 시도를 게이트. login 에서 set, logout 에서 clear.
const K_HAD_SESSION = 'groove.hadSession'

// 이전 빌드가 localStorage 에 남긴 평문 토큰 키 제거.
export function clearLegacyTokenStorage() {
  localStorage.removeItem('groove.accessToken')
  localStorage.removeItem('groove.refreshToken')
}

/** base64url JWT payload 를 UTF-8 로 디코드. 실패 시 null. */
function decodeJwt(token) {
  try {
    const payload = token.split('.')[1]
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    // '=' 패딩을 4의 배수로 보정.
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

// 인증 상태 스토어 — accessToken 은 메모리, email 은 localStorage. user 는 JWT payload 디코드로 얻는다.
export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: null, // 메모리 전용 — persist 안 함
    email: localStorage.getItem(K_EMAIL),
    hadSession: localStorage.getItem(K_HAD_SESSION) === '1', // "이전에 로그인함" 신호
  }),
  getters: {
    isAuthenticated: (state) => !!state.accessToken,
    user: (state) => {
      if (!state.accessToken) return null
      const claims = decodeJwt(state.accessToken)
      if (!claims) return null
      const role = claims.role || 'USER' // "USER" | "ADMIN"
      const memberId = claims.sub == null ? null : Number(claims.sub)
      return {
        memberId: Number.isFinite(memberId) ? memberId : null,
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
    /** 로그인 성공: accessToken 은 메모리, email 은 localStorage 에 저장. tokens = { accessToken, tokenType, expiresIn }. */
    login(tokens, email) {
      this.accessToken = tokens.accessToken
      this.hadSession = true
      localStorage.setItem(K_HAD_SESSION, '1')
      if (email) {
        this.email = email
        localStorage.setItem(K_EMAIL, email)
      }
    },
    /** refresh 후 accessToken 만 교체(회전). */
    updateTokens(tokens) {
      this.accessToken = tokens.accessToken
    },
    /** 클라이언트 상태 전체 clear. */
    logout() {
      this.accessToken = null
      this.email = null
      this.hadSession = false
      localStorage.removeItem(K_EMAIL)
      localStorage.removeItem(K_HAD_SESSION)
    },
  },
})
