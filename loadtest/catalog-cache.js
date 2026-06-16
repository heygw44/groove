// 카탈로그 조회 캐시 부하 테스트 (#236, M16) — GET /api/v1/albums/{id}(상세)와 GET /api/v1/albums(공개
// 기본 랜딩 목록)를 ramping-vus 로 측정한다. 두 경로 모두 Caffeine 캐시(@Cacheable, key=id / 단일 랜딩
// 엔트리)로 서빙되므로, 캐시 ON/OFF 앱을 상대로 같은 스크립트를 돌려 Before/After p95 를 박제한다.
//
// Before(캐시 OFF): 앱을 -e CACHE_ALBUM_CAFFEINE_SPEC 미적용 + SPRING_CACHE_TYPE=none 로 기동(또는 캐시 도입 전 커밋).
// After(캐시 ON):   기본 기동(local 프로파일, spring.cache.type=caffeine, recordStats).
//
// 적중률: local 프로파일에서 management 가 caches/metrics 를 노출하면(application-local.yaml) teardown 이
//         /actuator/metrics/cache.gets 를 읽어 hit/miss 와 적중률을 로그로 남긴다(노출 안 되면 조용히 생략).
//
// 엔드포인트는 public 이라 인증이 필요 없다 — 순수 읽기 핫경로의 캐시 효과만 격리 측정한다.
// 실행: k6 run loadtest/catalog-cache.js   (핫 id 고정: k6 run -e HOT_ID=1 loadtest/catalog-cache.js)

import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Trend } from 'k6/metrics';
import { redactedSummary } from './lib/summary.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const detailLatency = new Trend('detail_latency', true); // GET /albums/{id} 지연(캐시 핫경로)
const landingLatency = new Trend('landing_latency', true); // GET /albums (기본 랜딩) 지연

export const options = {
  scenarios: {
    catalog_browse: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 50 }, // 워밍(첫 미스 후 캐시 적재)
        { duration: '40s', target: 50 }, // 지속 조회 부하(적중 서빙)
        { duration: '10s', target: 0 }, // 감쇠
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    'http_req_failed{phase:catalog}': ['rate<0.01'],
    // 캐시 ON 의 SLO 초깃값 — Before(OFF)에서는 DB 왕복으로 breach 될 수 있고, 그 격차가 본 측정의 핵심이다.
    detail_latency: ['p(95)<200', 'p(99)<400'],
    landing_latency: ['p(95)<200', 'p(99)<400'],
    checks: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// 핫 id 를 setup 에서 1회 확보한다(미지정 시 랜딩 첫 건의 id). 다회 조회가 한 키에 집중되어 적중률이 높아진다.
export function setup() {
  if (__ENV.HOT_ID) {
    return { hotId: Number(__ENV.HOT_ID) };
  }
  const res = http.get(`${BASE_URL}/api/v1/albums?size=1`);
  const id = res.json('content.0.id');
  if (!id) {
    throw new Error(`핫 id 를 찾지 못했습니다(시드 필요). status=${res.status} body=${res.body}`);
  }
  return { hotId: id };
}

export default function (data) {
  const n = exec.scenario.iterationInTest;

  // 짝수 반복은 상세(고정 핫 id), 홀수 반복은 랜딩 목록 — 두 캐시를 함께 데운다.
  if (n % 2 === 0) {
    const res = http.get(`${BASE_URL}/api/v1/albums/${data.hotId}`, { tags: { phase: 'catalog' } });
    detailLatency.add(res.timings.duration);
    check(res, { '상세 200': (r) => r.status === 200 });
  } else {
    // 파라미터 없는 GET /albums = 공개 기본 랜딩(page0, size20, createdAt desc) → 단일 랜딩 캐시 엔트리.
    const res = http.get(`${BASE_URL}/api/v1/albums`, { tags: { phase: 'catalog' } });
    landingLatency.add(res.timings.duration);
    check(res, {
      '랜딩 200': (r) => r.status === 200,
      'content 배열 존재': (r) => Array.isArray(r.json('content')),
    });
  }
}

// 캐시 적중률을 actuator 메트릭에서 읽어 로그로 남긴다(local 프로파일 + caches/metrics 노출 시).
export function teardown() {
  const hit = readCacheGets('hit');
  const miss = readCacheGets('miss');
  if (hit === null || miss === null) {
    console.log('[#236 cache] actuator cache.gets 미노출 — 적중률 로깅 생략(local 프로파일에서 실행 권장).');
    return;
  }
  const total = hit + miss;
  const ratio = total > 0 ? ((hit / total) * 100).toFixed(1) : '0.0';
  console.log(`[#236 cache] cache.gets hit=${hit} miss=${miss} hitRatio=${ratio}%`);
}

function readCacheGets(result) {
  const res = http.get(`${BASE_URL}/actuator/metrics/cache.gets?tag=result:${result}`);
  if (res.status !== 200) {
    return null;
  }
  const value = res.json('measurements.0.value');
  return typeof value === 'number' ? value : null;
}

export function handleSummary(data) {
  return redactedSummary(data, 'loadtest/catalog-cache-summary.json');
}
