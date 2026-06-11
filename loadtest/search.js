// 앨범 검색 부하 테스트 (#192, W9) — GET /api/v1/albums 의 필터 조합·키워드 검색을 ramping-vus 로 측정한다.
//
// 검색 경로는 N+1·풀스캔 LIKE 슬로우 쿼리가 W10 시연용으로 의도 보존된 상태 — 본 부하는 그 Before(개선 전)
// 베이스라인을 박제하는 것이 목적이다. 엔드포인트는 public 이지만, 현실적 '로그인 유저 탐색'(JWT 검증 비용 포함)
// 을 측정하고자 토큰 풀의 Bearer 를 부착한다.
//
// 선행: 앱 기동 + scripts/seed.sh 로 메인 시드 적용(앨범 + loadtest001..080@groove.test / Test1234!).
//       setup 의 로그인 버스트가 throttle 되지 않도록 AUTH_RATE_LIMIT_LOGIN_CAPACITY 를 크게 주입한다.
// 실행: k6 run loadtest/search.js

import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Trend } from 'k6/metrics';
import { buildTokenPool, seedEmail } from './lib/auth.js';
import { redactedSummary } from './lib/summary.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const MEMBER_COUNT = Number(__ENV.MEMBER_COUNT || 80);
const PASSWORD = __ENV.PASSWORD || 'Test1234!';
const EMAIL_PREFIX = __ENV.EMAIL_PREFIX || 'loadtest';
const EMAIL_DOMAIN = __ENV.EMAIL_DOMAIN || '@groove.test';

const searchLatency = new Trend('search_latency', true); // 검색 요청 지연(setup 로그인 제외)

export const options = {
  scenarios: {
    search_browse: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 50 }, // 워밍
        { duration: '40s', target: 50 }, // 지속 탐색 부하
        { duration: '10s', target: 0 }, // 감쇠
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    // setup 로그인 버스트(phase:setup-login, per-IP rate limit 으로 429 가능)가 섞이지 않도록 검색
    // 요청(phase:search)만 게이트한다 — 안 그러면 setup 의 429·로그인 지연이 글로벌 지표를 오염시킨다.
    'http_req_failed{phase:search}': ['rate<0.01'],
    // search_latency 는 default() 의 검색 요청만 집계(setup 제외). 목표 SLO 초깃값 — Before 측정에서
    // 슬로우 쿼리로 breach 될 수 있고, 그 breach 자체가 W9 발견 사항이다. (http_req_duration 과 동일분포라 중복 임계값 제거.)
    search_latency: ['p(95)<800', 'p(99)<1500'],
    checks: ['rate>0.99'],
  },
  // k6 기본 요약 통계는 p(99) 를 출력하지 않으므로 명시 — README 의 p95·p99 가 실제 산출물에 드러나도록.
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// 시드 앨범 제목 공통 토큰(generate_seed.py COMMON_TOKENS) — LIKE '%kw%' 가 실제로 매칭된다.
const KEYWORDS = ['Love', 'Night', 'Blue', 'Dream', 'Fire'];
const FORMATS = ['LP_12', 'LP_DOUBLE', 'EP', 'SINGLE_7']; // AlbumFormat enum
const SORTS = ['createdAt,desc', 'price,asc', 'releaseYear,desc']; // 정렬 화이트리스트 내
const GENRE_COUNT = 12; // 시드 장르 수(generate_seed.py 적재 기준 genre=12)

const email = seedEmail(EMAIL_PREFIX, 3, EMAIL_DOMAIN); // loadtest001@groove.test ...

// 반복 인덱스로 검색 쿼리 모양을 결정론적으로 분산 — 단일 필터 6종을 순환하고 정렬/페이지를 덧붙인다.
function buildQuery(n) {
  const params = [];
  switch (n % 6) {
    case 0:
      params.push(`keyword=${KEYWORDS[n % KEYWORDS.length]}`);
      break;
    case 1:
      params.push(`genreId=${(n % GENRE_COUNT) + 1}`);
      break;
    case 2:
      params.push('minPrice=20000', 'maxPrice=50000');
      break;
    case 3:
      params.push('minYear=2000', 'maxYear=2020');
      break;
    case 4:
      params.push(`format=${FORMATS[n % FORMATS.length]}`);
      break;
    case 5:
      params.push('isLimited=true');
      break;
  }
  params.push(`sort=${SORTS[n % SORTS.length]}`, `page=${n % 5}`, 'size=20');
  return params.join('&');
}

// 시드된 회원 N명을 로그인시켜 토큰 풀을 만든다 (1회).
export function setup() {
  const tokens = buildTokenPool({ baseUrl: BASE_URL, count: MEMBER_COUNT, password: PASSWORD, email });
  return { tokens };
}

export default function (data) {
  // 시나리오 전역 반복 카운터로 토큰을 인덱싱 — 풀 크기까지는 회원이 겹치지 않는다.
  const n = exec.scenario.iterationInTest;
  const token = data.tokens[n % data.tokens.length];
  const query = buildQuery(n);

  const res = http.get(`${BASE_URL}/api/v1/albums?${query}`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { phase: 'search' },
  });

  searchLatency.add(res.timings.duration);
  check(res, {
    '200 검색 성공': (r) => r.status === 200,
    'content 배열 존재': (r) => Array.isArray(r.json('content')),
  });
}

export function handleSummary(data) {
  // setup() 토큰 풀(실제 JWT)을 비식별화한 뒤 요약을 기록한다 — 평문 토큰 유출 방지(#219).
  return redactedSummary(data, 'loadtest/search-summary.json');
}
