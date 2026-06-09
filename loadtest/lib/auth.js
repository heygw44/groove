// W9 측정 시나리오 공통 하네스 (#192) — 시드 회원 로그인 → access token 풀.
//
// coupon-issuance.js 의 setup 로직을 일반화해 추출한 모듈. search/order/payment 등 후속
// 시나리오가 import 해 재사용한다. (coupon-issuance.js 는 기존 동작 보존 위해 그대로 자체 포함.)

import http from 'k6/http';

// 시드 회원 count 명을 로그인시켜 access token 배열을 만든다 (setup 1회).
// VU 별로 서로 다른 회원 토큰을 쓰기 위함. 풀이 비면 시드/자격증명 문제이므로 즉시 중단한다.
export function buildTokenPool({ baseUrl, count, password, email }) {
  const tokens = [];
  for (let i = 1; i <= count; i++) {
    const res = http.post(
      `${baseUrl}/api/v1/auth/login`,
      JSON.stringify({ email: email(i), password }),
      { headers: { 'Content-Type': 'application/json' }, tags: { phase: 'setup-login' } },
    );
    if (res.status === 200 || res.status === 201) {
      const token = res.json('accessToken');
      if (token) tokens.push(token); // 2xx 인데 토큰 필드가 없으면 'Bearer undefined' 가 새지 않도록 방어
    }
  }
  if (tokens.length === 0) {
    throw new Error(
      '토큰 풀이 비었다 — 시드/자격증명을 확인하라 (scripts/seed.sh, loadtest001..NNN@groove.test / Test1234!)',
    );
  }
  if (tokens.length < count) {
    // 흔한 원인: 로그인 rate limit(per-IP, 기본 capacity 10). AUTH_RATE_LIMIT_LOGIN_CAPACITY 를 크게 주입해야 한다.
    console.warn(`토큰 풀 부족: ${tokens.length}/${count} 로그인 성공 — AUTH_RATE_LIMIT_LOGIN_CAPACITY 확인`);
  } else {
    console.log(`토큰 풀 준비: ${tokens.length}/${count} 로그인 성공`);
  }
  return tokens;
}

// 시드 이메일 생성기. pad 자리수로 0 패딩 — search 시드(generate_seed.py)는 pad=3 (loadtest001@groove.test).
export function seedEmail(prefix, pad, domain) {
  return (i) => `${prefix}${String(i).padStart(pad, '0')}${domain}`;
}
