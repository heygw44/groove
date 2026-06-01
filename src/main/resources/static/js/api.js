// REST API fetch 래퍼 (#102)
//
// - 모든 path 는 API 상대경로('/albums')로 전달하고 래퍼가 '/api/v1' prefix 를 붙인다.
// - 인증 요청에는 store 의 액세스 토큰을 Bearer 로 자동 첨부한다.
// - 401 응답 시 refresh 1회 → 원요청 1회 재시도(루프 안전). 실패하면 로그아웃.
// - 에러 응답(ProblemDetail, RFC 7807)은 ApiError 로 구조화해 throw 한다.
// - idempotent:true 면 Idempotency-Key 헤더(crypto.randomUUID)를 첨부한다(#110 결제용).

import * as store from './store.js';

const BASE = '/api/v1';
const REFRESH_PATH = '/auth/refresh';

/** ProblemDetail(code/title/detail/violations)을 담는 구조화된 에러. */
export class ApiError extends Error {
  constructor({ status, code, title, detail, violations, traceId }) {
    super(detail || title || `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status; // 예: 401, 409, 422
    this.code = code; // 예: "AUTH_001"
    this.title = title;
    this.detail = detail;
    this.violations = violations || []; // [{field, message}]
    this.traceId = traceId;
  }
}

async function toApiError(res) {
  let problem = {};
  try {
    problem = await res.json();
  } catch {
    // 비-JSON 에러 본문 방어
  }
  return new ApiError({
    status: res.status,
    code: problem.code,
    title: problem.title,
    detail: problem.detail,
    violations: problem.violations,
    traceId: problem.traceId,
  });
}

// 동시 401 들이 refresh 를 중복 호출하지 않도록 in-flight 프로미스를 공유한다.
let refreshInFlight = null;

/**
 * 저장된 refresh 토큰으로 토큰을 1회 갱신한다. 성공 true / 실패 false.
 * request() 를 거치지 않는 raw fetch 라 refresh 가 다시 refresh 를 부르는 재귀가 없다.
 */
function tryRefresh() {
  if (refreshInFlight) return refreshInFlight;
  const refreshToken = store.getRefreshToken();
  if (!refreshToken) return Promise.resolve(false);

  refreshInFlight = (async () => {
    try {
      const res = await fetch(BASE + REFRESH_PATH, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
      if (!res.ok) return false;
      const tokens = await res.json(); // {accessToken, refreshToken, tokenType, expiresIn}
      store.updateTokens(tokens); // 회전: 구 refresh 토큰은 서버가 폐기
      return true;
    } catch {
      return false;
    } finally {
      refreshInFlight = null;
    }
  })();
  return refreshInFlight;
}

/**
 * @param {string} method
 * @param {string} path  API 상대경로(예: '/albums')
 * @param {{body?: any, idempotent?: boolean, auth?: boolean, query?: object}} [opts]
 * @param {boolean} [_retried]  내부용: refresh 후 재시도 여부 플래그
 */
async function request(method, path, opts = {}, _retried = false) {
  const { body, idempotent = false, auth = true, query } = opts;
  const qs = query ? '?' + new URLSearchParams(query).toString() : '';
  const url = BASE + path + qs;

  const headers = {};
  const init = { method, headers };

  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }
  if (idempotent) {
    headers['Idempotency-Key'] = crypto.randomUUID();
  }
  if (auth) {
    const token = store.getAccessToken();
    if (token) headers['Authorization'] = `Bearer ${token}`;
  }

  const res = await fetch(url, init);

  // 401 → refresh 1회 + 재시도 1회. 가드:
  //  - 인증 요청(auth)이고, 아직 재시도 안 했고(!_retried),
  //  - refresh 호출 자신은 제외(path !== REFRESH_PATH),
  //  - refresh 토큰을 실제로 보유한 경우에만 발화 → 무한 루프 불가.
  if (
    res.status === 401 &&
    auth &&
    !_retried &&
    path !== REFRESH_PATH &&
    store.getRefreshToken()
  ) {
    const refreshed = await tryRefresh();
    if (refreshed) {
      return request(method, path, opts, true);
    }
    store.logout();
    throw await toApiError(res);
  }

  if (!res.ok) {
    throw await toApiError(res);
  }

  // 204 / 빈 본문
  if (res.status === 204 || res.headers.get('Content-Length') === '0') {
    return null;
  }
  const text = await res.text();
  if (!text) return null;
  return JSON.parse(text);
}

export function get(path, opts) {
  return request('GET', path, opts);
}
export function post(path, opts) {
  return request('POST', path, opts);
}
export function put(path, opts) {
  return request('PUT', path, opts);
}
export function del(path, opts) {
  return request('DELETE', path, opts);
}

/**
 * 로그인: 토큰 발급 후 store 에 저장. auth 엔드포인트라 Bearer 미첨부(auth:false).
 * @returns 발급된 토큰 응답
 */
export async function loginFlow(email, password) {
  const tokens = await post('/auth/login', {
    auth: false,
    body: { email, password },
  });
  store.login(tokens, email);
  return tokens;
}

/**
 * 로그아웃: 서버에 refresh 토큰 폐기를 best-effort 요청한 뒤 클라이언트 상태를 clear.
 * 서버 호출이 실패해도 로컬 상태는 반드시 비운다.
 */
export async function logoutFlow() {
  const refreshToken = store.getRefreshToken();
  if (refreshToken) {
    try {
      await post('/auth/logout', { auth: false, body: { refreshToken } });
    } catch {
      // 폐기 실패는 무시 — 로컬 로그아웃은 진행
    }
  }
  store.logout();
}
