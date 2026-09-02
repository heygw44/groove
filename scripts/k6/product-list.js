// 상품 목록 API(GET /api/v1/products) 부하 테스트. 목표: p95 300ms (NFR-03).
// 실행: k6 run scripts/k6/product-list.js
// 환경변수: BASE_URL(기본 http://localhost:8080)
// local 프로파일 시드 데이터가 필요하다.
// 결과 저장: k6 run --summary-export scripts/k6/results/product-list.json scripts/k6/product-list.js

import http from 'k6/http';
import { check, fail, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const VARIANT_NAMES = [
  'list-default',
  'list-page',
  'list-keyword',
  'list-genre',
  'list-artist',
  'list-price',
  'list-sort',
  'list-combined',
];

// 태그별 threshold 를 걸어야 k6 가 변형별 서브메트릭을 요약에 남긴다
const variantThresholds = Object.fromEntries(
  VARIANT_NAMES.map((name) => [`http_req_duration{name:${name}}`, ['p(95)<300']]),
);

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    product_list: {
      executor: 'constant-vus',
      vus: 50,
      duration: '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<300'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    ...variantThresholds,
  },
};

function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

export function setup() {
  const genreRes = http.get(`${BASE_URL}/api/v1/genres`);
  const artistRes = http.get(`${BASE_URL}/api/v1/artists`);
  const productRes = http.get(`${BASE_URL}/api/v1/products`);

  if (genreRes.status !== 200 || artistRes.status !== 200 || productRes.status !== 200) {
    fail('시드 데이터가 없습니다. local 프로파일로 서버를 띄우세요.');
  }

  const genreBody = genreRes.json();
  const artistBody = artistRes.json();
  const productBody = productRes.json();

  if (!productBody.data || productBody.data.totalElements === 0) {
    fail('시드 데이터가 없습니다. local 프로파일로 서버를 띄우세요.');
  }

  const genreIds = genreBody.data.map((genre) => genre.id);
  const artistIds = artistBody.data.map((artist) => artist.id);

  // 상품 제목 첫 단어(2글자 이상)를 검색어 후보로 사용, 중복 제거
  const keywordSet = new Set();
  productBody.data.content.forEach((product) => {
    const firstWord = product.title.split(' ')[0];
    if (firstWord && firstWord.length >= 2) {
      keywordSet.add(firstWord);
    }
  });

  return { genreIds, artistIds, keywords: Array.from(keywordSet) };
}

const SORT_OPTIONS = ['priceAsc', 'priceDesc', 'rating', 'popular'];

export default function (data) {
  const variants = [
    { name: 'list-default', query: () => '' },
    { name: 'list-page', query: () => 'page=1&size=20' },
    { name: 'list-keyword', query: () => `keyword=${encodeURIComponent(pick(data.keywords))}` },
    { name: 'list-genre', query: () => `genreId=${pick(data.genreIds)}` },
    { name: 'list-artist', query: () => `artistId=${pick(data.artistIds)}` },
    { name: 'list-price', query: () => 'minPrice=20000&maxPrice=50000' },
    { name: 'list-sort', query: () => `sort=${pick(SORT_OPTIONS)}` },
    {
      name: 'list-combined',
      query: () =>
        `keyword=${encodeURIComponent(pick(data.keywords))}&genreId=${pick(data.genreIds)}&sort=priceAsc`,
    },
  ];

  const variant = pick(variants);
  const query = variant.query();
  const url = query ? `${BASE_URL}/api/v1/products?${query}` : `${BASE_URL}/api/v1/products`;

  const res = http.get(url, { tags: { name: variant.name } });

  check(res, {
    'status is 200': (r) => r.status === 200,
    'success is true': (r) => r.json('success') === true,
    'content is array': (r) => Array.isArray(r.json('data.content')),
  });

  sleep(0.1);
}
