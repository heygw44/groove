# k6 부하 테스트

로컬 docker 환경을 대상으로 하는 k6 스크립트 모음. 한정반 선착순 구매의 초과판매 방지, 상품 목록/관리자 통계 응답 시간, 배포 중 무중단, 장애 주입 복구를 각각 확인한다.

## 스크립트

| 파일 | 대상 | 확인하는 것 |
|---|---|---|
| `product-list.js` | `GET /api/v1/products` | p95 300ms |
| `limited-purchase.js` | `POST /api/v1/limited-drops/{id}/purchase` | 초과판매 0건, p95 1초 |
| `admin-dashboard.js` | 관리자 통계 4종(`daily-sales`/`popular-products`/`limited-drops`/`summary`) | 대시보드 진입처럼 동시 호출했을 때 응답 시간 |
| `batch-interference.js` | 사전 집계 배치(`POST /api/v1/admin/stats/aggregations`) + 일반 API 동시 부하 | 배치가 도는 동안 일반 API 응답 시간이 평상시와 같은지 |
| `deploy-drain.js` + `toss-mock.mjs` | 배포(백엔드 컨테이너 재생성) | 진행 중 요청 처리 여부, 재기동 공백의 거부/끊김 건수 |
| `chaos/run.sh`, `chaos/limited-chaos.js`, `chaos/analyze-chaos.mjs` | 한정반 러시 중 Redis/앱 장애 주입 | 대사·재적재·서킷 폴백이 원상복구를 시키는지 |
| `verify-oversell.sh` | 한정반 드롭 | 초과판매 여부 자동 판정(`--local`, `--chaos` 옵션) |
| `prod-run.sh`, `monitor-remote.sh`, `cleanup-prod-loadtest.sh` | 운영 서버 | 운영 대상 실행/모니터링/정리 스크립트. SSH 접속과 운영 자격증명이 필요해 실행 절차는 비공개 런북에 둔다 |
| `analyze-batch-interference.mjs` | `batch-interference.js` 결과 | raw JSON 을 pretest/duringBatch/posttestIdle 구간으로 나눠 p95 비교 |

## 사전 준비

1. k6 설치: `brew install k6` (또는 https://k6.io/docs/get-started/installation/)
2. 백엔드까지 컨테이너로 띄운다: `docker compose --profile full up --build -d`
3. 헬스체크로 뜰 때까지 기다린다: `curl http://localhost:8080/actuator/health`
4. local 프로파일이므로 관리자 계정(`admin@groove.com` / `admin1234!`)이 시드되어 있어야 한다.

`admin-dashboard.js`/`batch-interference.js` 는 합성 데이터가 있는 별도 스키마가 필요하다.

```bash
backend/scripts/perf/seed-load-db.sh groove_load --scale 0.2
```

Flyway 마이그레이션을 순서대로 적용하고, 합성 데이터(주문/결제/리뷰 등)를 넣고, 사전 집계 테이블까지 백필하고, 관리자 계정을 만든다. `--scale` 은 규모 계수(1.0 이 원본 규모, 기본 0.2). 백엔드를 이 스키마에 붙여 띄우려면 `SPRING_PROFILES_ACTIVE=seed`와 `SPRING_DATASOURCE_URL`을 해당 스키마로 지정해 `./gradlew bootRun`을 실행한다(`local` 프로파일은 이 용도로 쓰지 않는다 — 데모/신호 시더가 붙어 있어 기동이 실패한다).

## 실행

웜업으로 소규모부터 확인한다.

```bash
VUS=50 STOCK=10 k6 run infra/k6/limited-purchase.js
```

본 실행(기본값 VUS=1000, STOCK=100):

```bash
k6 run infra/k6/limited-purchase.js
```

Redis ON/DB 락만 비교 실행. 백엔드 컨테이너를 `LIMITED_REDIS_ENABLED=false` 로 재기동한 뒤 같은 시나리오를 돌리고, 끝나면 원복한다.

```bash
LIMITED_REDIS_ENABLED=false docker compose --profile full up -d backend
k6 run infra/k6/limited-purchase.js

# 원복
docker compose --profile full up -d backend
```

`docker compose up -d` 는 설정이 그대로면 컨테이너를 재사용한다. 콜드 상태에서 재측정하려면 `--force-recreate` 를 붙이고, 워밍업 뒤 본측정은 재기동 없이 이어서 돌린다. 호스트 8080 을 다른 프로세스가 쓰고 있으면 `BACKEND_PORT=18080 docker compose --profile full up -d backend` 로 포트를 옮기고 `BASE_URL=http://localhost:18080` 을 준다.

집계/배치 간섭 시나리오는 위 사전 준비로 `groove_load` 스키마를 먼저 채운 뒤 돌린다.

```bash
k6 run infra/k6/admin-dashboard.js
```

`batch-interference.js` 는 6분 넘게 걸린다. 백그라운드로 돌리고 raw JSON 출력을 남긴다(사후 분석에 필요).

```bash
k6 run --out json=infra/k6/results/batch-interference-raw.json \
  infra/k6/batch-interference.js > /tmp/batch-interference.log 2>&1 &
tail -f /tmp/batch-interference.log
```

배포 드레인 측정은 토스 목 서버를 먼저 띄운 뒤 백엔드가 그 목 서버를 보도록 기동한다.

```bash
PORT=18080 CONFIRM_DELAY_MS=200 node infra/k6/toss-mock.mjs
TOSS_BASE_URL=http://host.docker.internal:18080 docker compose --profile full up -d --build
k6 run infra/k6/deploy-drain.js
# 도중에 배포를 흉내낸다
docker compose --profile full up -d --force-recreate backend
```

카오스 부하 테스트는 시나리오 인자로 실행한다.

```bash
infra/k6/chaos/run.sh <redis-restart|redis-key-loss|app-kill> [--label NAME] [--out DIR]
```

`docker compose restart redis` 는 로컬에서 1초도 안 걸려 서킷이 열릴 틈이 없다. 폴백까지 보려면 정지 시간을 늘린다.

```bash
REDIS_DOWN_SEC=10 infra/k6/chaos/run.sh redis-restart --label down10
```

## 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | 대상 서버 |
| `VUS` | `1000` | 동시 구매를 시도할 가상 사용자 수(= 회원 수, `limited-purchase.js`) |
| `STOCK` | `100`(`limited-purchase.js`) / `500`(`chaos/limited-chaos.js`) | 한정반 총 수량 |
| `MEMBERS` | `1000` | 카오스 러시 도중 순환할 회원 수(`chaos/limited-chaos.js`) |
| `RATE` | `150`(카오스) / `20`(`deploy-drain.js`) | 초당 요청 수 |
| `RUSH_DURATION` / `TAIL_RATE` / `TAIL_DURATION` / `PRE_VUS` / `MAX_VUS` | `20s` / `5` / `120s` / `100` / `400` | 카오스 러시·테일 구간 설정(`chaos/limited-chaos.js`) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `admin@groove.com` / `admin1234!` | local 시드 관리자 계정 |
| `MEMBER_PASSWORD` | `load1234!` | 생성하는 회원 비밀번호 |
| `MEMBER_EMAIL_PREFIX` | `lt-`(`limited-purchase.js`) / `chaos-` / `drain-` | 생성하는 회원 이메일 접두사(스크립트별로 겹치지 않게 분리) |
| `PRODUCT_TITLE_PREFIX` | `LIMITED-LOADTEST-` / `LIMITED-CHAOS-` / `DEPLOY-DRAIN-` | 생성하는 상품/앨범 타이틀 접두사 |
| `OPEN_DELAY_SEC` | `8` | 회원 준비가 끝난 뒤 드롭 오픈 시각까지 두는 여유(초) |
| `SETUP_BATCH_SIZE` | `20` | 회원 준비 단계의 BCrypt 동시성 |
| `RESULT_DIR` | `infra/k6/results` | 결과 JSON 저장 위치 |
| `RUN_LABEL` | (빈 문자열) | 결과 파일명에 붙는 라벨 |
| `P95_MS` | `1000` | `http_req_duration{name:purchase}` 임계값(ms) |
| `CHECK_RATE` | `0.99` | `checks` 임계값(통과율) |
| `INJECT_DELAY_SEC` | `2` | 카오스: 러시 시작부터 장애 주입까지 지연(초) |
| `REDIS_DOWN_SEC` | `0` | 카오스: `redis-restart` 전용. `0` 이면 `restart`, 그 이상이면 `stop`→`sleep N`→`start` |
| `VERIFY_DELAY_SEC` | `70` | 카오스: k6 종료 후 판정 전 대기(초) |
| `BACKEND_CONTAINER` / `REDIS_CONTAINER` | `groove-backend` / `groove-redis` | 카오스 장애 주입 대상 컨테이너 |
| `HEALTH_TIMEOUT_SEC` | `120` | 카오스: `app-kill` 복구 헬스체크 대기 상한(초) |
| `DURATION` | `120s` | `deploy-drain.js` 두 시나리오 실행 시간 |
| `CONFIRM_ORDERS` | `30` | `deploy-drain.js`: 미리 만들어 둘 PENDING 주문 수(= VU 수) |
| `PORT` / `CONFIRM_DELAY_MS` / `LOOKUP_DELAY_MS` / `LOOKUP_UNKNOWN_AS_DONE` | `18080` / `0` / `0` / `0` | `toss-mock.mjs`: 포트와 승인/조회 응답 지연, 조회 미상 응답을 DONE 으로 답할지 |
| `TRIGGER_START` / `TOTAL_DURATION` | `2m` / `6m` | `batch-interference.js` 트리거 시작 시각 / 전체 실행 시간 |
| `SSH_HOST` / `SSH_KEY` | 필수(기본값 없음) | 운영 대상 스크립트(`prod-run.sh`, `monitor-remote.sh`, `cleanup-prod-loadtest.sh`, `verify-oversell.sh` non-local)가 요구하는 접속 정보 |

## 판정 기준

- `limited-purchase.js`: HTTP 201(성공) 개수 == `STOCK`, `limited_purchase` 행 수 == `STOCK`, `stock.quantity` == 0, Redis `limited:stock:{dropId}` == 0 이고 `SCARD limited:buyers:{dropId}` == `STOCK`(Redis OFF 모드에서는 두 키가 애초에 없는 게 정상).
- `admin-dashboard.js`: 통계 4종 각각 p95 300ms, `http_req_failed` 0.
- `batch-interference.js`: `duringBatch` p95 절대값 300ms 이내. 상대 비교(`pretest` 대비 +10% 이내)는 `analyze-batch-interference.mjs` 출력을 함께 봐야 판단할 수 있다. `http_req_failed` 0(트리거 409 는 실패로 세지 않는다).
- `chaos/`: `verify-oversell.sh --local --chaos` 7항목(초과판매 없음, 완판 여부, Redis pending 잔여 0, Redis stock/buyers 정합성 등) — exit code 0 이 전부 PASS.
- `deploy-drain.js`: 재기동 공백 동안의 거부+끊김 건수, `payment_confirm_success` 가 `CONFIRM_ORDERS` 와 같으면 in-flight 요청이 끝까지 처리됐다는 뜻.

## 결과 파일

각 스크립트는 실행마다 `results/<시나리오>-<YYYYMMDD-HHmmss>.json` 형식으로 결과를 남긴다. `results/` 는 gitignore 대상이며 로컬 보관용이다.
