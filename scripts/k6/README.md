# k6 부하 테스트

## 목적

- `product-list.js` — 상품 목록 API(`GET /api/v1/products`) 부하 테스트. p95 300ms(NFR-03) 검증.
- `limited-purchase.js` — 한정반 선착순 구매 API(`POST /api/v1/limited-drops/{id}/purchase`) 부하 테스트. 초과 판매 0건(NFR-02)과 p95 1초 검증.
- `admin-dashboard.js` — 관리자 통계 4종(`daily-sales`/`popular-products`/`limited-drops`/`summary`)을 대시보드 진입처럼 동시 호출. #316 사전 집계(`sales_daily`/`sales_daily_product`) 도입 전후 p95 비교용.
- `batch-interference.js` — #316 핵심 증명. 사전 집계 배치(`POST /api/v1/admin/stats/aggregations`)가 도는 동안 일반 API(`GET /api/v1/products`) 응답 시간이 평상시와 같은지를 잰다. 지연을 배치로 "옮긴" 게 아니라 "없앴다"는 걸 보이는 유일한 방법.

## 사전 준비

1. k6 설치: `brew install k6` (또는 https://k6.io/docs/get-started/installation/)
2. 백엔드까지 컨테이너로 띄운다: `docker compose --profile full up --build -d`
3. 헬스체크로 뜰 때까지 기다린다: `curl http://localhost:8080/actuator/health`
4. local 프로파일이므로 관리자 계정(`admin@groove.com` / `admin1234!`)이 시드되어 있어야 한다.

`admin-dashboard.js`/`batch-interference.js` 는 합성 데이터가 필요하다 — 자세한 사전 준비는 아래
["집계/배치 간섭 시나리오"](#집계배치-간섭-시나리오-admin-dashboardjs-batch-interferencejs) 절 참고.

## 실행

웜업으로 소규모부터 확인한다.

```bash
VUS=50 STOCK=10 k6 run scripts/k6/limited-purchase.js
```

본 실행(기본값 VUS=1000, STOCK=100):

```bash
k6 run scripts/k6/limited-purchase.js
```

Redis ON/DB 락만 비교 실행. 백엔드 컨테이너를 `LIMITED_REDIS_ENABLED=false` 로 재기동한 뒤 같은 시나리오를 돌리고, 끝나면 원복한다.

```bash
LIMITED_REDIS_ENABLED=false docker compose --profile full up -d backend
k6 run scripts/k6/limited-purchase.js

# 원복
docker compose --profile full up -d backend
```

`docker compose up -d` 는 설정이 그대로면 컨테이너를 재사용한다. 콜드 상태에서 재측정하려면 `--force-recreate` 를 붙이고, 반대로 워밍업 뒤 본측정은 재기동 없이 이어서 돌린다. 호스트 8080 을 다른 프로세스가 쓰고 있으면 `BACKEND_PORT=18080 docker compose --profile full up -d backend` 로 포트를 옮기고 `BASE_URL=http://localhost:18080` 을 준다.

## 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | 대상 서버 |
| `VUS` | `1000` | 동시 구매를 시도할 가상 사용자 수(= 회원 수) |
| `STOCK` | `100` | 한정반 총 수량 |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `admin@groove.com` / `admin1234!` | local 시드 관리자 계정 |
| `OPEN_DELAY_SEC` | `8` | 회원 준비가 끝난 뒤 오픈 시각까지 두는 여유(초) |
| `MEMBER_PASSWORD` | `load1234!` | 테스트용으로 생성하는 회원 비밀번호 |
| `RESULT_DIR` | `scripts/k6/results` | 결과 JSON 저장 위치 |

## 판정 기준

- HTTP 201(성공) 개수 == `STOCK`
- 아래 쿼리로 실측 확인:

```bash
docker compose exec mysql mysql -ugroove -pgroove1234 groove -e "SELECT COUNT(*) FROM limited_purchase WHERE drop_id=<dropId>; SELECT quantity FROM stock WHERE product_id=<productId>; SELECT sold_count, status FROM limited_drop WHERE id=<dropId>;"
docker compose exec redis redis-cli GET limited:stock:<dropId>
docker compose exec redis redis-cli SCARD limited:buyers:<dropId>
```

- `limited_purchase` 행 수 == `STOCK`
- `stock.quantity` == 0
- Redis `limited:stock:{dropId}` == 0, `SCARD limited:buyers:{dropId}` == `STOCK`
- Redis OFF 모드로 돌린 실행에서는 위 두 Redis 키가 애초에 존재하지 않는다(정상).

## 결과 파일

- 실행마다 `results/limited-<YYYYMMDD-HHmmss>.json` 이 남는다(gitignore 대상, 로컬 보관용).
- 여러 번 실행해 비교했다면 `results/limited-YYYYMMDD.md` 로 정리한다. 이 마크다운만 예외적으로 커밋 추적 대상이다.

## 주의

- 컨테이너 백엔드는 `JAVA_TOOL_OPTIONS`(Dockerfile)로 `-Xmx384m` + SerialGC 제약을 받는다. VUS 를 크게 올리면 GC 압박으로 응답 지연이 커질 수 있다.
- `VUS=1000` 기준 setup 에서 로그인만 1000건이라 준비 단계가 수십 초 걸릴 수 있다. `setupTimeout: '10m'` 로 여유를 뒀다.
- 실행마다 새 상품/한정반을 만들고 지우지 않는다. 반복 실행하면 관리자 상품/한정반 목록에 `LIMITED-LOADTEST-*` 항목이 계속 쌓이므로, 필요하면 수동으로 정리한다.

## 집계/배치 간섭 시나리오 (admin-dashboard.js, batch-interference.js)

#312~#314 로 관리자 통계를 원본 테이블 실시간 집계에서 사전 집계 테이블(`sales_daily`/`sales_daily_product`)
조회로 옮겼다. 이 전환이 근거를 가지려면 두 가지를 실측해야 한다.

1. 관리자 통계 API 자체가 빨라졌는가 — `admin-dashboard.js`
2. **사전 집계 배치가 도는 동안 일반 API 응답 시간이 평상시와 같은가** — `batch-interference.js`.
   지연을 배치로 "옮긴" 게 아니라 "없앴다"는 걸 보이는 유일한 방법이다.

### 사전 준비

`docker compose` 의 `groove` DB는 수백 건뿐이라 두 시나리오 모두 무의미하다(집계 효과도 배치 간섭도 안
드러난다). 합성 데이터가 있는 별도 스키마가 필요하다.

1. 합성 데이터 스키마 생성. `backend/scripts/perf/index-explain.sh` 의 시드 함수를 재사용하되, 그 스크립트가
   건드리는 `groove_perf`/개발 DB(`groove`) 는 그대로 둔다. 스키마명은 인자로 명시해야 하고, `groove`를
   지정하면 스크립트가 거부한다.

   ```bash
   backend/scripts/perf/seed-load-db.sh groove_load --scale 0.2
   ```

   Flyway 마이그레이션(V1~V15)을 순서대로 적용하고, 합성 데이터(주문/결제/리뷰 등)를 넣고, 사전 집계
   테이블(`sales_daily`/`sales_daily_product`)까지 백필하고, 관리자 계정(`admin@groove.com` /
   `admin1234!`, 비밀번호는 `--admin-password` 로 변경 가능)을 만든다. `--scale` 은 `index-explain.sh` 와
   같은 계수(1.0 이 원본 규모, 기본 0.2). 이미 테이블이 있는 스키마를 다시 넣으려면 먼저 드롭한다.

2. 백엔드를 그 스키마에 붙여 띄운다. `application-local.yml` 의 데이터소스 URL 이 `groove` 로 고정돼
   있어서 `SPRING_DATASOURCE_URL` 로 직접 덮어써야 하고, `local` 프로파일은 데모/신호 시더가 붙어 있어
   기동 직후 `runStatsBackfill()` 이 `TransactionRequiredException` 으로 죽는 문제가 있다(재현: 개발
   DB `groove` 에서도 `SPRING_PROFILES_ACTIVE=local` 로 그대로 재현된다 — 애플리케이션 코드 결함이라 이
   문서 범위 밖). 그래서 `seed` 프로파일만 켜고 나머지 값을 직접 채운다.

   ```bash
   cd backend
   SPRING_PROFILES_ACTIVE=seed \
   SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/groove_load?serverTimezone=Asia/Seoul&characterEncoding=UTF-8" \
   SPRING_DATASOURCE_USERNAME=root \
   SPRING_DATASOURCE_PASSWORD=root1234 \
   SPRING_JPA_HIBERNATE_DDL_AUTO=validate \
   SPRING_DATA_REDIS_HOST=localhost SPRING_DATA_REDIS_PORT=6379 \
   JWT_SECRET=local-dev-only-secret-key-must-be-at-least-32-bytes-long \
   TOSS_SECRET_KEY=test_sk_local_dev_only \
   DISCOGS_TOKEN=discogs_token_local_dev_only \
   CORS_ALLOWED_ORIGINS=http://localhost:5173 \
   FILE_UPLOAD_PATH=/tmp/groove-load-uploads FILE_BASE_URL=http://localhost:8080/uploads \
   ./gradlew bootRun
   ```

   호스트 8080 이 이미 쓰이고 있으면 `SERVER_PORT` 를 바꾸고 두 스크립트의 `BASE_URL` 을 그 포트로 맞춘다.

3. 헬스체크: `curl http://localhost:8080/actuator/health`, 그다음 통계가 빈 값이 아닌지 확인.

   ```bash
   TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
     -d '{"email":"admin@groove.com","password":"admin1234!"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
   curl -s http://localhost:8080/api/v1/admin/stats/summary -H "Authorization: Bearer $TOKEN"
   ```

### 실행

```bash
k6 run scripts/k6/admin-dashboard.js
```

`batch-interference.js` 는 6분 넘게 걸린다. 반드시 백그라운드로 돌리고 폴링한다(포그라운드로 걸면
타임아웃으로 실패한다). **raw JSON 출력(`--out json`)이 있어야 사후 분석이 가능하다** — 아래 "판정
방법" 절 참고.

```bash
k6 run --out json=scripts/k6/results/batch-interference-raw.json \
  scripts/k6/batch-interference.js > /tmp/batch-interference.log 2>&1 &
# 진행 확인
tail -f /tmp/batch-interference.log
```

스모크로 짧게 먼저 돌려보려면 `TRIGGER_START`/`TOTAL_DURATION` 을 줄인다(예: `TRIGGER_START=8s
TOTAL_DURATION=20s`). 둘 다 `2m`/`6m` 이 기본값이고, `TRIGGER_START` 는 `trigger` 시나리오의
`startTime` 과 baseline 의 pretest/posttest 경계를 함께 옮긴다.

### 시나리오 구성 (batch-interference.js)

| 시나리오 | executor | 부하 | exec |
|---|---|---|---|
| `baseline` | constant-arrival-rate | 50rps, 6분 | `GET /api/v1/products` (product-list.js 와 같은 쿼리 변형) |
| `dashboard` | constant-arrival-rate | 0.5rps, 6분 | 관리자 통계 4종 동시 호출 |
| `trigger` | per-vu-iterations 1(단일 VU) | startTime 2분, 자체 루프 | `POST /api/v1/admin/stats/aggregations {from: D-90, to: D-1}` 를 posttest 구간이 끝날 때까지 순차 반복 |

**왜 trigger 가 반복인가.** 1차 버전은 트리거를 1회만 쐈다. 재집계 한 번이 901ms 만에 끝나버려서
4분짜리 posttest 구간 중 배치가 실제로 도는 시간은 0.9초뿐이었고, posttest p95 는 대부분 "배치가
돌지 않는 동안"의 응답을 잰 값이었다 — 결론이 희석됐다. 그래서 trigger 를 단일 VU 순차 루프로 바꿔
posttest 구간 내내 재집계를 다시 트리거한다(같은 range 를 반복해도 날짜별 UPSERT 라 멱등). 단일 VU
가 순차로만 호출하므로 named lock(`AggregationLock`)에 걸려 409 가 날 일 자체가 구조적으로 없다 —
동시에 여러 트리거를 던져 대부분 409 를 받는 방식(옵션 2) 대신 "겹치지 않게 쉬지 않고 반복"하는 쪽을
택했다.

### 판정 방법

`baseline` 요청은 이제 pretest/posttest 시간 절반 태그만 참고용으로 붙이고, 실제 판정은 실행이 끝난
뒤 사후 분석 스크립트가 낸다 — 시간을 미리 구간으로 나누는 방식은 배치 길이가 구간보다 짧아지면 다시
같은 함정(희석)에 빠지기 때문이다.

```bash
node scripts/k6/analyze-batch-interference.mjs \
  scripts/k6/results/batch-interference-raw.json /tmp/batch-interference.log
```

trigger 시나리오가 매 호출의 시작/종료 시각을 콘솔에 `AGG_BATCH_INTERVAL startMs=... endMs=...` 로
남기고, 이 스크립트가 그 구간들과 raw JSON 의 baseline 요청 타임스탬프를 직접 대조해 각 요청을
`pretest`(평시) / `duringBatch`(배치가 실제로 도는 순간) / `posttestIdle`(posttest 구간이지만 두
배치 호출 사이 공백)로 나눈 뒤 구간별 p95 를 낸다. 출력에 배치 호출이 posttest 구간을 실제로 얼마나
채웠는지(커버리지 %, 최대 공백)도 같이 나오므로, 그 값으로 "배치가 구간을 충분히 채웠는지"를 먼저
확인하고 나서 p95 비교를 읽어야 한다.

### 판정 기준

- `admin-dashboard.js`: 통계 4종 각각 p95 300ms(NFR-03과 같은 기준선), `http_req_failed` 0.
- `batch-interference.js` (핵심, `analyze-batch-interference.mjs` 출력 기준):
  - 배치 커버리지가 posttest 구간을 사실상 다 채우는지 먼저 확인(공백이 크면 판정 자체가 무의미)
  - `duringBatch` p95 가 `pretest` p95 대비 **+10% 이내** — 단, `pretest`(기동 직후 0~2분)는 JIT
    워밍업·커넥션 풀 초기화가 섞인 구간이라 그 자체로 평시 기준이 못 될 수 있다. `posttestIdle`
    (배치가 안 도는 공백, `pretest`와 같은 시간대) p95가 `pretest`보다 낮게 나오면 `pretest`가
    워밍업으로 오염됐다는 신호이니, 이 경우 `pretest` 대비 비교는 판정 근거로 쓰지 말 것. 그렇다고
    `posttestIdle`을 곧바로 대신 쓸 수 있는 것도 아니다 — 커버리지가 높을수록 공백(=posttestIdle
    표본)이 줄어들어 p95 판정에 못 쓸 만큼 표본이 작아질 수 있다(실측 사례: n=34). 이런 경우 이
    항목은 "PASS/FAIL"이 아니라 **"판정 불가"**로 정직하게 보고한다(`batch-interference-20260909.md`
    참고).
  - 절대값은 NFR-03 의 **300ms** 유지 (`duringBatch` p95) — 이건 대조군 문제와 무관하게 단정 가능.
  - `http_req_failed` **0** (트리거의 409 는 이 카운트에 들어가지 않는다 — 실패가 아니라 락 미획득이다)
  - 콜드 캐시 편차가 크므로 최종 판단은 같은 조건 3회 실행의 중앙값으로 한다.
  - 판정이 실패로 나와도, 또는 위처럼 판정 불가로 나와도 그대로 보고한다 — 배치가 실제로 응답을
    늦춘다면 그건 결함이지 숨길 대상이 아니고, 비교 기준이 편향돼 결론을 못 내는 것도 측정 설계의
    한계이지 감출 일이 아니다.

### 정리

측정이 끝나면 띄운 백엔드 프로세스를 종료한다. `groove_load` 스키마는 남겨도 되고, 다음에 다시 쓰거나
필요 없어지면 지운다.

```bash
docker exec -i -e MYSQL_PWD=root1234 groove-mysql mysql -uroot -e 'DROP DATABASE groove_load;'
```

측정 중에는 같은 MySQL 에 다른 무거운 작업(다른 부하 테스트, 대량 배치)을 같이 돌리지 않는다 — 측정이
오염된다.
