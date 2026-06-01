// 인증 상태 스토어 (#102)
//
// localStorage 에 토큰을 보관하는 순수 상태 컨테이너다. 네트워크는 전혀 모른다 —
// 실제 login/refresh/logout HTTP 호출은 api.js 가 담당하고, 이 모듈의 상태 변경자
// (login/updateTokens/logout)만 콜백한다. 따라서 store.js 는 api.js 를 import 하지
// 않으며, 의존은 api.js → store.js 단방향이다(순환 없음).
//
// "현재 유저"는 액세스 토큰 JWT payload 를 디코드해 얻는다(추가 API 호출 없음).
// 서버가 진짜 권한을 검증하므로 이 값은 네비바 표시·Admin 링크 노출용일 뿐이다.

const K_ACCESS = 'groove.accessToken';
const K_REFRESH = 'groove.refreshToken';
const K_EMAIL = 'groove.email';

const subscribers = new Set();

/** 인증 상태 변화 구독. 호출 즉시 현재 유저로 1회 통지하지 않으며, 변경 시에만 통지한다. */
export function subscribe(fn) {
  subscribers.add(fn);
  return () => subscribers.delete(fn);
}

function notify() {
  const user = getUser();
  for (const fn of subscribers) fn(user);
}

/** base64url 로 인코딩된 JWT payload 를 UTF-8 안전하게 디코드. 실패 시 null. */
function decodeJwt(token) {
  try {
    const payload = token.split('.')[1];
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    // base64url 은 보통 '=' 패딩이 생략되어 있다. atob() 가 엄격 모드에서 던지지 않도록 길이를 4의 배수로 보정.
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    const binary = atob(padded);
    const json = decodeURIComponent(
      Array.from(binary)
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    );
    return JSON.parse(json);
  } catch {
    return null;
  }
}

export function getAccessToken() {
  return localStorage.getItem(K_ACCESS);
}

export function getRefreshToken() {
  return localStorage.getItem(K_REFRESH);
}

export function isAuthenticated() {
  return !!getAccessToken();
}

/**
 * 현재 유저. 토큰이 없거나 디코드 실패 시 null.
 * @returns {{memberId: number|null, role: string, email: string|null, isAdmin: boolean}|null}
 */
export function getUser() {
  const token = getAccessToken();
  if (!token) return null;
  const claims = decodeJwt(token);
  if (!claims) return null;
  const role = claims.role || 'USER'; // "USER" | "ADMIN" (평문, ROLE_ 접두사 없음)
  const memberId = claims.sub == null ? null : Number(claims.sub);
  return {
    memberId: Number.isFinite(memberId) ? memberId : null, // 비정상 토큰의 NaN 방어
    role,
    email: localStorage.getItem(K_EMAIL) || null,
    isAdmin: role === 'ADMIN',
  };
}

/** 로그인 성공 시 토큰 + 입력 email 저장. tokens = 로그인/refresh 응답 형태. */
export function login(tokens, email) {
  localStorage.setItem(K_ACCESS, tokens.accessToken);
  localStorage.setItem(K_REFRESH, tokens.refreshToken);
  if (email) localStorage.setItem(K_EMAIL, email);
  notify();
}

/** refresh 후 토큰만 교체(회전). email 은 유지한다. */
export function updateTokens(tokens) {
  localStorage.setItem(K_ACCESS, tokens.accessToken);
  localStorage.setItem(K_REFRESH, tokens.refreshToken);
  notify();
}

/** 클라이언트 상태 전체 clear. 서버측 refresh 토큰 폐기는 api.logoutFlow() 가 담당. */
export function logout() {
  localStorage.removeItem(K_ACCESS);
  localStorage.removeItem(K_REFRESH);
  localStorage.removeItem(K_EMAIL);
  notify();
}
