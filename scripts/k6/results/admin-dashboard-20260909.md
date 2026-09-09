# 관리자 대시보드 부하 테스트 결과 (2026-09-09)

`scripts/k6/admin-dashboard.js` 로 관리자 통계 4종(`daily-sales`/`popular-products`/
`limited-drops`/`summary`)을 대시보드 진입처럼 동시 호출했다. #312~#314 로 원본 테이블 실시간
집계에서 사전 집계 테이블(`sales_daily`/`sales_daily_product`) 조회로 옮긴 뒤 상태를 확인하는
용도다.

## 실행 환경

| 항목 | 값 |
|---|---|
| 머신 | macOS(OrbStack Docker), 앱·DB·Redis·k6 모두 같은 머신 |
| 앱 | `./gradlew bootRun`, `SPRING_PROFILES_ACTIVE=seed`(datasource 만 `groove_load` 로 override) |
| DB | MySQL 8.0 컨테이너(`groove-mysql`), 스키마 `groove_load` |
| 데이터 | `backend/scripts/perf/seed-load-db.sh groove_load --scale 0.2`(사전 집계 테이블 백필 포함) |
| 부하 | constant-vus 5, 30초, 매 반복마다 통계 4종을 `http.batch` 로 동시 호출 |

## 판정 기준과 결과

| 통계 | p95 | 300ms(NFR-03 기준선) 판정 |
|---|---|---|
| `daily-sales` | 6.23ms | PASS |
| `popular-products` | 7.42ms | PASS |
| `limited-drops` | 5.61ms | PASS |
| `summary` | 6.28ms | PASS |
| 전체 `http_req_duration` | 6.61ms | PASS |
| `http_req_failed` | 0.00%(0/102,685) | PASS |

**종합 판정: PASS.** 4종 모두 p95 10ms 이내로, 사전 집계 테이블 조회 전환 뒤 대시보드 API 가
가볍게 응답한다.

## k6 출력 인용

```
✓ http_req_duration..............: avg=4.44ms  min=1.33ms med=4.34ms max=93.38ms p(90)=6.06ms p(95)=6.61ms p(99)=7.88ms
  ✓ { name:daily-sales }.........: avg=4.36ms  min=1.51ms med=4.3ms  max=31.6ms  p(90)=5.76ms p(95)=6.23ms p(99)=7.31ms
  ✓ { name:limited-drops }.......: avg=3.79ms  min=1.33ms med=3.7ms  max=27.93ms p(90)=5.14ms p(95)=5.6ms  p(99)=6.66ms
  ✓ { name:popular-products }....: avg=5.3ms   min=2.05ms med=5.22ms max=29.35ms p(90)=6.85ms p(95)=7.42ms p(99)=8.65ms
  ✓ { name:summary }.............: avg=4.29ms  min=1.54ms med=4.2ms  max=26.17ms p(90)=5.74ms p(95)=6.27ms p(99)=7.39ms
✓ http_req_failed................: 0.00%   ✓ 0            ✗ 102685
http_reqs......................: 102685  3411.655952/s
checks.........................: 100.00% ✓ 205368       ✗ 0
admin_dashboard ✓ [ 100% ] 5 VUs  30s
```

k6 요약 JSON 원본: `scripts/k6/results/admin-dashboard-20260909-001824.json`(gitignore 대상,
로컬 보관용).

## 읽기

- `max` 값이 93ms/31ms 등으로 p95 대비 크게 튀는 요청이 소수 있는데, JIT/커넥션 풀 워밍업 구간(첫
  1~2초)에 몰려 있다. 30초 constant-vus 구간 전체로는 p95/p99 가 안정적이라 판정에 영향 없음.
- 4종 중 `popular-products` 가 가장 느리다(p95 7.42ms). `sales_daily_product` 파생 테이블
  LIMIT 조회 하나로 끝나지만 정렬 컬럼(`sold_quantity`/`sales_amount`)이 둘이라 `daily-sales`
  단일 range 조회보다 근소하게 무겁다 — 여전히 절대값은 무시할 수준.
- 이 측정은 배치 간섭 여부가 아니라 API 자체의 절대 성능만 본다. 배치가 도는 동안의 영향은
  `batch-interference-20260909.md` 참고.

## 재현

```bash
backend/scripts/perf/seed-load-db.sh groove_load --scale 0.2
# 백엔드 기동은 batch-interference-20260909.md 의 "재현" 절과 동일
k6 run scripts/k6/admin-dashboard.js
```
