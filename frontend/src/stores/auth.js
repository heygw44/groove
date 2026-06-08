import { defineStore } from 'pinia'

// #163: 토큰은 더 이상 localStorage 에 저장하지 않는다.
//  - accessToken 은 메모리(Pinia state)에만 둔다 — 새로고침 시 부팅 silent refresh 로 복원.
//  - refreshToken 은 백엔드가 HttpOnly 쿠키로 관리해 JS 가 접근할 수 없다.
// email 은 navbar 표시 전용으로만 localStorage 에 남긴다(토큰 아님).
const K_EMAIL = 'groove.email'

// "이전에 로그인함" 세션 신호. refresh 쿠키는 HttpOnly 라 JS 가 볼 수 없으므로, 부팅 복원 시도와
// 401 인터셉터의 refresh 시도를 이 플래그로 게이트한다(표시용 email 과 분리 — email 이 다른 사유로
// 지워져도 세션 판단이 흔들리지 않게). login 에서 set, logout 에서 clear.
const K_HAD_SESSION = 'groove.hadSession'

// M14/이전 빌드가 localStorage 에 남긴 평문 토큰 키. 부팅 시 1회 제거한다(XSS 가 읽을 잔존 토큰 제거, #163).
// import 시점 사이드이펙트를 피하려고 main.js boot() 에서 호출한다(비-브라우저 import 안전).
export function clearLegacyTokenStorage() {
  localStorage.removeItem('groove.accessToken')
  localStorage.removeItem('groove.refreshToken')
}

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
 * 인증 상태 스토어. accessToken 은 메모리에만, email 만 localStorage 에 두는 순수 상태 컨테이너다(#163).
 * refresh 토큰은 HttpOnly 쿠키라 JS 에서 보이지 않으므로 스토어가 다루지 않는다.
 * 실제 login/refresh/logout HTTP 호출은 api/ 가 담당하고 여기 액션을 콜백한다.
 *
 * "현재 유저"는 액세스 토큰 JWT payload 디코드로 얻는다(추가 API 호출 없음).
 * 서버가 진짜 권한을 검증하므로 이 값은 네비바 표시·Admin 링크 노출용일 뿐이다.
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: null, // 메모리 전용 — persist 안 함. 새로고침 시 부팅 silent refresh 로 복원.
    email: localStorage.getItem(K_EMAIL),
    hadSession: localStorage.getItem(K_HAD_SESSION) === '1', // "이전에 로그인함" 신호(쿠키 비가시성 보완)
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
    /**
     * 로그인 성공: accessToken 을 메모리에 두고 email 만 localStorage 에 저장한다.
     * refresh 토큰은 응답 body 에 없고 백엔드가 Set-Cookie(HttpOnly)로 내려준다(#163).
     * tokens = 로그인 응답 형태({ accessToken, tokenType, expiresIn }).
     */
    login(tokens, email) {
      this.accessToken = tokens.accessToken
      this.hadSession = true
      localStorage.setItem(K_HAD_SESSION, '1')
      if (email) {
        this.email = email
        localStorage.setItem(K_EMAIL, email)
      }
    },
    /** refresh 후 accessToken 만 교체(회전). 새 refresh 쿠키는 백엔드가 재설정하고 email 은 유지한다. */
    updateTokens(tokens) {
      this.accessToken = tokens.accessToken
    },
    /** 클라이언트 상태 전체 clear. 서버측 refresh 쿠키 폐기는 api/auth.logoutFlow 가 담당. */
    logout() {
      this.accessToken = null
      this.email = null
      this.hadSession = false
      localStorage.removeItem(K_EMAIL)
      localStorage.removeItem(K_HAD_SESSION)
    },
  },
})
