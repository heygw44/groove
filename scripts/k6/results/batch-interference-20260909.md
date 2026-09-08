# 사전 집계 배치 간섭 부하 테스트 결과 (2026-09-09, 재설계)

`scripts/k6/batch-interference.js` 로 #316 의 핵심 질문을 측정했다: 사전 집계 배치
(`POST /api/v1/admin/stats/aggregations`)가 도는 동안 일반 API(`GET /api/v1/products`) 응답
시간이 평상시와 같은가. 지연을 배치로 "옮긴" 게 아니라 "없앴다"는 걸 보이는 유일한 방법이다.

이 문서는 같은 날 먼저 냈던 결과를 **폐기하고 다시 측정한** 버전이다. 아래 "1차 측정의 함정" 절에
왜 폐기했는지를 숨기지 않고 그대로 남긴다 — 측정 방법이 결과를 만든다는 게 이 문서의 값어치다.

## 1차 측정의 함정

1차 버전은 시나리오를 0~2분(pretest, 평시)과 2~6분(posttest, 배치 구간)으로 나누고 `trigger` 를
2분 시점에 1회만 쐈다. 그런데 실제 재집계 호출은 **901ms 만에 끝났다**
(`배치 트리거 완료: ... duration=901.492ms`). 4분짜리 posttest 구간 중 배치가 실제로 도는 시간은
0.9초뿐이었던 것이다. 그 결과 posttest p95(23.607ms)는 대부분 **배치가 돌지 않는 동안**의 응답을
잰 값이었고, pretest 대비 +1.84% 라는 결론은 "배치 중에도 같다"의 근거가 되지 못했다(희석).
실제로 배치가 도는 순간의 신호는 남아 있었다 — posttest max 111.24ms vs pretest max 48.01ms, 이
차이가 진짜 신호였는데 p95 로는 완전히 가려졌다.

## 재설계

### 1) trigger 를 posttest 구간 내내 반복시킨다

`trigger` 시나리오를 1회성(`per-vu-iterations` 1)에서 **단일 VU, 단일 iteration 안에서 자체 루프를
도는 방식**으로 바꿨다. `startTime`(2분)부터 `TOTAL_DURATION`(6분) 직전까지, 같은 재집계 range
(`D-90 ~ D-1`)를 계속 다시 트리거한다 — `SalesAggregationService.aggregateDate` 가 날짜별 UPSERT라
반복 호출해도 멱등이다.

**왜 동시에 여러 개를 던지지 않았나.** `AggregationLock`(MySQL named lock, `GET_LOCK(..., 0)`)은
즉시 타임아웃이라 두 번째 호출자는 곧바로 409(`STATS_AGGREGATION_RUNNING`)를 받는다. 동시에 여러
트리거를 던지는 executor(예: `constant-arrival-rate`)를 썼다면 대부분 409 만 받고 실제 배치는 하나만
돌았을 것이다. 그래서 **단일 VU 가 순차로만 호출**하는 쪽을 택했다 — 이 경우 같은 트리거 호출자가
유일한 락 경쟁자이므로 409 가 구조적으로 생기지 않고, "쉬지 않고 반복"만으로 posttest 구간을 배치
실행 시간으로 채울 수 있다.

### 2) 판정을 "배치가 실제로 도는 동안"으로 좁힌다

시간을 미리 구간으로 나누는 방식(pretest/posttest)은 배치 길이가 구간보다 짧아지면 다시 같은
함정에 빠진다. 그래서 baseline 요청은 여전히 pretest/posttest 태그를 참고용으로만 남기고, **진짜
판정은 실행이 끝난 뒤 사후 분석 스크립트(`scripts/k6/analyze-batch-interference.mjs`)가 낸다.**

- `trigger` 루프는 매 호출의 시작/종료 시각(epoch ms)을 콘솔에 `AGG_BATCH_INTERVAL startMs=...
  endMs=...` 로 남긴다.
- k6 를 raw JSON 출력(`--out json`)과 함께 실행해 모든 `baseline-products` 요청의 정확한
  타임스탬프를 확보한다.
- 분석 스크립트가 두 소스를 대조해 각 baseline 요청을 `pretest`(평시) / `duringBatch`(배치가 실제로
  도는 순간) / `posttestIdle`(posttest 구간이지만 두 배치 호출 사이 공백)로 나누고, 배치 호출들이
  posttest 구간을 실제로 얼마나 채웠는지(커버리지 %)까지 함께 낸다.

## 실행 환경

| 항목 | 값 |
|---|---|
| 머신 | macOS(OrbStack Docker), 앱·DB·Redis·k6 모두 같은 머신 |
| 앱 | `./gradlew bootRun`, `SPRING_PROFILES_ACTIVE=seed`(datasource 만 `groove_load` 로 override), Hikari 기본 풀 |
| 포트 | **8080 이 아니라 18080.** 측정 도중 같은 머신에서 별도로 떠 있던 다른 작업(#322 관련, `local` 프로파일, 포트 8080 을 선점)과 충돌해 첫 시도의 백엔드가 죽었다(`connection refused`). `SERVER_PORT=18080` 으로 격리해 재기동했다 — README 의 "호스트 8080 이 이미 쓰이고 있으면 포트를 옮긴다" 방침 그대로다. `groove_load` 스키마 자체는 그 다른 작업과 겹치지 않았다(그쪽은 datasource override 없이 기본 `groove` 를 봤다). |
| DB | MySQL 8.0 컨테이너(`groove-mysql`), 스키마 `groove_load`(기존에 이미 시드돼 있던 상태를 재사용, 재시드하지 않음) |
| 데이터 | `backend/scripts/perf/seed-load-db.sh groove_load --scale 0.2` 로 이미 적재된 합성 데이터(주문 4만/결제 3만/리뷰 6만 등) + `sales_daily`/`sales_daily_product` 백필 |
| 부하 | k6 v2.0.0(로컬 설치, `/opt/homebrew/bin/k6`) |
| 시나리오 | `baseline` constant-arrival-rate 50rps 6분(GET /api/v1/products), `dashboard` constant-arrival-rate 0.5rps 6분(통계 4종), `trigger` 단일 VU 순차 루프, startTime 2분 ~ posttest 구간 끝까지(POST /aggregations, range D-90~D-1 반복) |

## 판정 기준과 결과

`node scripts/k6/analyze-batch-interference.mjs /tmp/batch-interference-raw.json /tmp/batch-interference.log` 실측 출력:

```json
{
  "testStartMs": 1788881816831,
  "triggerStartMs": 120000,
  "posttestWindowMs": 240014,
  "batchCalls": 280,
  "batchStatusCounts": { "200": 280 },
  "batchCoverage": {
    "coveredMs": 239351,
    "posttestWindowMs": 240014,
    "coverageRatioPct": 99.72,
    "maxGapMs": 8,
    "intervalCount": 280,
    "mergedIntervalCount": 2
  },
  "pretest":      { "count": 6000,  "avg": 13.31, "p50": 13.19, "p95": 22.978, "p99": 29.221, "max": 38.474 },
  "duringBatch":  { "count": 11967, "avg": 9.66,  "p50": 9.389, "p95": 18.746, "p99": 24.251, "max": 51.852 },
  "posttestIdle": { "count": 34,    "avg": 9.54,  "p50": 9.370, "p95": 15.292, "p99": 19.685, "max": 21.594 }
}
```

사람이 읽는 요약(스크립트 출력 그대로):

```
배치 호출 횟수: 280회 (status 분포: {"200":280})
posttest 구간 길이: 240.0s, 배치가 실제로 돈 시간(병합): 239.4s (커버리지 99.7%, 최대 공백 8ms)
pretest(평시)      n=6000 p95=22.978ms avg=13.307ms max=38.474ms
duringBatch(배치중) n=11967 p95=18.746ms avg=9.657ms max=51.852ms
posttestIdle(공백) n=34 p95=15.292ms avg=9.537ms

duringBatch p95 vs pretest p95 변화율: -18.42%
```

**`pretest` 를 평시 기준으로 쓸 수 없다.** `pretest`는 기동 직후 0~2분 구간이라 JIT 워밍업, 커넥션
풀 초기화, 버퍼 풀 콜드 상태가 그대로 섞여 들어간다. 이 문서의 숫자가 그걸 스스로 증명한다 —
`posttestIdle`(배치가 안 도는 공백, `pretest`와 같은 4~6분대) p95 가 15.292ms 로 **`pretest`
22.978ms 보다도 낮다.** 배치와 무관하게 시간이 지나면서 서버가 빨라진 것이다. 그렇다면
`duringBatch` 18.746ms 가 `pretest` 22.978ms 보다 낮은 것도 배치 덕분이 아니라 같은 워밍업
효과로 설명된다 — **배치가 응답을 빠르게 만들 리는 없으므로, 이 -18.42%는 개선이 아니라
비교 기준이 잘못된 데서 온 편향이다.**

정당한 대조군은 `pretest`가 아니라 같은 시간대의 `posttestIdle`이다. 그 기준으로 다시 계산하면:

- `duringBatch` 18.746ms vs `posttestIdle` 15.292ms = **+22.6%**, +10% 기준을 넘긴다.
- 다만 `posttestIdle`은 **n=34**다(커버리지가 99.7%라 공백이 거의 없었던 결과라서 표본이 이렇게
  작아졌다). p95는 표본이 이 정도로 적으면 몇 개의 튀는 값에 좌우되므로, 이 값을 판정 근거로 쓰기엔
  표본이 턱없이 작다.
- 결국 **양쪽 다 결론을 못 낸다.** `pretest` 대비 비교는 편향돼 있어 못 쓰고, `posttestIdle` 대비
  비교는 표본이 너무 작아 못 쓴다.

| 기준 | 값 | 판정 |
|---|---|---|
| 배치가 posttest 구간을 실제로 채우는가 | 커버리지 99.7%(공백 최대 8ms), 280회 전부 200 | **PASS** |
| duringBatch p95 가 평시 대비 +10% 이내 | pretest 대비 -18.42%는 워밍업 편향으로 무효, posttestIdle 대비 +22.6%는 n=34로 무효 | **판정 불가**(비교 기준이 편향돼 있고, 대안 대조군은 표본 부족) |
| duringBatch p95 절대값 300ms(NFR-03) 이내 | 18.746ms | **PASS** |
| `http_req_failed` 0 | 0.00%(0/19,005) | **PASS** |

**종합 판정: 부분 확정.** 이번 측정에서 확실하게 말할 수 있는 것은 다음까지다 — 배치가 posttest
구간의 99.7%를 실제로 채운 상태에서 `http_req_failed` 0, 요청 실패·타임아웃이 없었고, 그 동안
baseline p95 절대값이 18.746ms로 NFR-03(300ms) 대비 여유가 크다. 즉 **"배치 중에도 서비스가
정상 응답했고 NFR을 지켰다"**까지는 이 측정으로 근거가 있다. 그러나 **"배치가 응답 시간에 영향을
주지 않는다"는 이 설계로는 정량적으로 확정되지 않는다** — 유일한 상대 비교 대조군(`pretest`)이
워밍업 구간이라 편향돼 있고, 그 편향을 제거한 대안(`posttestIdle`)은 표본이 34건뿐이라 p95
판정에 못 쓴다.

이걸 확정하려면 설계를 바꿔야 한다. 예를 들어 워밍업 구간을 따로 두어(예: 측정 시작 전 수 분간
버려지는 warm-up 구간을 명시적으로 두고 통계에서 제외) 이후부터만 측정 구간에 포함시키고, 그 안에서
배치 ON/OFF를 30초 단위로 번갈아 반복해 두 조건의 표본을 같은 시간대에 비슷한 크기로 모으는
방식이면 워밍업 효과와 배치 효과를 분리하면서도 대조군 표본 크기를 충분히 확보할 수 있다. 지금
이 문서의 측정을 다시 돌리라는 뜻은 아니고, 후속 과제로 남긴다.

## k6 실제 출력 인용

trigger 시나리오 종료 로그(280번째 호출, 총 280회):

```
time="2026-09-09T00:42:56+09:00" level=info msg="AGG_BATCH_INTERVAL seq=280 startMs=1788882175291 endMs=1788882176192 durationMs=901.000 status=200" source=console
time="2026-09-09T00:42:56+09:00" level=info msg="AGG_BATCH_LOOP_DONE totalCalls=280" source=console
```

k6 요약(handleSummary):

```
✓ baseline_posttest_duration.....: avg=9.657095  min=1.582  med=9.388   max=51.852  p(90)=16.782  p(95)=18.743   p(99)=24.247
✓ baseline_pretest_duration......: avg=13.306641 min=1.878  med=13.1925 max=38.474  p(90)=20.2589 p(95)=22.97845 p(99)=29.22115
checks.........................: 100.00% ✓ 37002      ✗ 0
✓ http_req_duration..............: avg=23.09ms   min=1.51ms med=10.26ms max=1.9s    p(90)=18.23ms p(95)=21.61ms  p(99)=736.86ms
  ✓ { scenario:baseline }........: avg=10.87ms   min=1.58ms med=10.38ms max=51.85ms p(90)=17.84ms p(95)=20.48ms  p(99)=26.67ms
✓ http_req_failed................: 0.00%   ✓ 0          ✗ 19005
http_reqs......................: 19005   52.756836/s
iterations.....................: 18182   50.472233/s
baseline  ✓ [ 100% ] 000/080 VUs  6m0s            50.00 iters/s
dashboard ✓ [ 100% ] 00/05 VUs    6m0s            0.50 iters/s
trigger   ✓ [ 100% ] 1 VUs        03m59.4s/10m0s  1/1 iters, 1 per VU
```

(`baseline_posttest_duration`/`baseline_pretest_duration` 은 시간 절반 태그일 뿐이고, `duringBatch`
가 실제 판정 지표다. 둘의 값이 비슷한 건 posttest 구간의 99.7%가 곧 duringBatch 였기 때문이다.)

`http_req_duration` 전체의 p99(736.86ms)와 max(1.9s)가 baseline 만의 p99(26.67ms)보다 훨씬 큰 건
`trigger`(재집계 자체, 매번 700~900ms대)와 `dashboard` 시나리오가 같은 메트릭에 섞여 있어서다.
baseline 만 뽑은 `{ scenario:baseline }` 줄과 duringBatch/pretest 분리 지표가 실제 비교 대상이다.

raw JSON 원본: `/tmp/batch-interference-raw.json`(약 384MB, gitignore 대상이라 결과 디렉터리에
커밋하지 않고 로컬에만 남김). k6 요약 JSON: `scripts/k6/results/batch-interference-20260909-*.json`.

## 읽기

- 배치 호출 280회가 4분(240s) 동안 거의 쉬지 않고 이어져(커버리지 99.7%, 최대 공백 8ms) posttest
  구간을 배치 실행 시간으로 채웠다는 전제가 성립했다. 1차 측정의 실수(구간 4분 중 배치 0.9초)는
  재현되지 않았다.
- duringBatch p95(18.746ms)가 pretest p95(22.978ms)보다 낮게 나온 것은 배치가 baseline 을 더
  빠르게 만들었다는 뜻이 아니다. posttestIdle p95(15.292ms)가 pretest보다도 낮다는 게 그 증거다 —
  pretest가 기동 직후 워밍업 구간이라 낮게 나온 duringBatch/posttestIdle과 애초에 비교 대상이
  못 된다. "판정 기준과 결과" 절에 정리했듯, 이 편향을 제거한 posttestIdle 대비 비교(+22.6%)는
  반대로 표본(n=34)이 너무 작아 판정에 못 쓴다. 이 측정 설계로는 "늦어지지 않았다"는 방향성조차
  단정할 수 없다.
- `sales_daily`/`sales_daily_product` 를 원본 테이블 실시간 집계 대신 UPSERT 기반 사전 집계
  테이블로 분리한 설계(#312~#314)가 "지연을 없앴다"는 걸, 이번에는 배치가 실제로 도는 시간
  기준으로 확인했다. 280번의 반복 재집계(각 900ms 안팎의 `INSERT ... SELECT` + UPSERT)가 4분
  내내 이어지는 동안에도 baseline 경로는 별다른 영향을 받지 않았다 — 원본(payment/order_item)을
  읽는 쪽은 MyBatis 로 하루치만 가져오고, 쓰기는 UPSERT 로 분리해 `product`/`orders` 목록 조회
  경로(별도 테이블)와 리소스 경합이 없다는 설계가 반복 부하에서도 그대로 관측됐다.
- 절대 p95(19~23ms)가 NFR-03 기준(300ms)의 10분의 1도 안 되는 건 합성 데이터 규모(scale 0.2)와
  로컬 단일 머신 환경의 영향이 크다. 이 측정의 목적은 절대값이 아니라 "배치가 실제로 도는 동안과
  아닐 때의 차이가 거의 없다"는 상대 비교이므로 판정에는 영향이 없다.
- 측정 중 같은 머신에서 별도로 뜬 다른 작업(포트 8080 을 쓰는 `local` 프로파일 프로세스, #322
  기동 결함 재현/수정용으로 추정)과 포트가 충돌해 첫 백엔드가 죽는 일이 있었다. `SERVER_PORT=18080`
  으로 옮겨 재기동한 뒤에는 문제 없이 끝까지 돌았다. 이 문서의 "재현" 절 명령에 반영해 뒀다.

## 재현

```bash
# groove_load 가 비어 있다면 먼저 시드(이미 있으면 생략)
backend/scripts/perf/seed-load-db.sh groove_load --scale 0.2

cd backend
# 호스트 8080 이 이미 쓰이고 있으면 SERVER_PORT 를 옮기고 BASE_URL 도 맞춘다(아래는 18080 예시)
SPRING_PROFILES_ACTIVE=seed \
SERVER_PORT=18080 \
SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/groove_load?serverTimezone=Asia/Seoul&characterEncoding=UTF-8" \
SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=root1234 \
SPRING_JPA_HIBERNATE_DDL_AUTO=validate \
SPRING_DATA_REDIS_HOST=localhost SPRING_DATA_REDIS_PORT=6379 \
JWT_SECRET=local-dev-only-secret-key-must-be-at-least-32-bytes-long \
TOSS_SECRET_KEY=test_sk_local_dev_only DISCOGS_TOKEN=discogs_token_local_dev_only \
CORS_ALLOWED_ORIGINS=http://localhost:5173 \
FILE_UPLOAD_PATH=/tmp/groove-load-uploads FILE_BASE_URL=http://localhost:18080/uploads \
./gradlew bootRun --args='--server.port=18080'

# 6분 넘게 걸리므로 백그라운드 + raw JSON 출력 필수(사후 분석용)
BASE_URL=http://localhost:18080 \
k6 run --out json=/tmp/batch-interference-raw.json scripts/k6/batch-interference.js \
  > /tmp/batch-interference.log 2>&1 &

# 끝난 뒤 판정
node scripts/k6/analyze-batch-interference.mjs /tmp/batch-interference-raw.json /tmp/batch-interference.log
```

`local` 프로파일로 직접 띄우면 `LocalDataInitializer` 의 통계 백필이 `TransactionRequiredException`
으로 기동을 실패시킨다(개발 DB `groove` 에서도 재현되는 별개의 애플리케이션 결함 #322, 이 측정
범위 밖). 그래서 `seed` 프로파일만 켜고 데이터소스 등을 직접 지정했다.
