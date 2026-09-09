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
| `MEMBER_EMAIL_PREFIX` | `lt-` | 생성하는 회원 이메일 접두사(`prod-run.sh` 는 `k6lt-`) |
| `PRODUCT_TITLE_PREFIX` | `LIMITED-LOADTEST-` | 생성하는 상품/앨범 타이틀 접두사 |
| `RUN_LABEL` | (빈 문자열) | 결과 JSON 파일명에 붙는 라벨(`limited-<RUN_LABEL>-<timestamp>.json`). `prod-run.sh` 는 단계별로 `vu-050` 처럼 채운다 |
| `SETUP_BATCH_SIZE` | `20` | 회원 준비 단계의 BCrypt 동시성. 요청 동시성(`options.batch`, 항상 20 이상)과 분리되어 있다 |
| `P95_MS` | `1000` | `http_req_duration{name:purchase}` 임계값(ms) |
| `CHECK_RATE` | `0.99` | `checks` 임계값(통과율) |

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

## 운영 도메인 측정

**경고: 이 측정은 운영 DB 를 직접 건드린다.** 측정용 회원·상품·주문이 실제 운영 데이터에 섞여 들어가고, 끝나면 반드시 정리해야 한다. 아래 스크립트로 돌린다.

- `prod-run.sh` — VU 를 단계적으로 올리며(기본 50 → 200 → 500 → 1000) 운영 도메인에 대고 `limited-purchase.js` 를 반복 실행한다.
- `monitor-remote.sh` — 부하가 도는 동안 EC2 자원(컨테이너 CPU/메모리, free, cpu steal, TCP 연결 수)을 CSV 로 수집한다.
- `verify-oversell.sh` — 각 단계가 끝나면 즉시 초과판매 여부를 판정한다.
- `cleanup-prod-loadtest.sh` — 측정이 남긴 데이터를 접두사 기준으로 지운다(기본 dry-run).

### 왜 다시 재나

기존 수치(`limited-*.md`)는 로컬(Apple M4/16GB)에서 컨테이너 백엔드를 직접 때린 값이다. "코드가 초과판매를 막는가"는 증명하지만 "이 서비스가 얼마나 버티는가"는 증명하지 못한다. 실제 서비스는 t3.micro(2 vCPU / 1GB) 한 대에 MySQL·Redis·JVM 이 같이 떠 있고, 앞에 Nginx·TLS·인터넷 구간이 붙는다.

### 측정 전에 알아야 할 제약

아래는 실측치다.

| 항목 | 값 |
|---|---|
| EC2 스펙 | t3.micro, 2 vCPU / 총 911MB |
| 측정 전 available 메모리 | 약 120MB |
| swap | 2048MB 중 약 440MB 이미 사용 중 |
| 컨테이너 RSS | backend 약 355MB(`-Xmx384m` + SerialGC), mysql 약 74MB, redis 약 2MB |
| `/api/v1/health` 왕복(로컬 → 운영) | conn 약 55ms, TLS 약 78ms, TTFB 약 100ms |
| Nginx | `worker_connections 768` × worker 2 |
| `net.ipv4.tcp_max_syn_backlog` | 128 |
| Redis | `maxmemory 64mb` / `allkeys-lru`, 측정 전 사용량 1.3MB |
| rate limit | 애플리케이션·Nginx 어디에도 없음 |

특히 챙길 점:

- 네트워크 왕복만으로 80ms대 고정 바닥이 깔린다. 로컬 측정에는 없던 항목이므로 로컬 p95 와 운영 p95 를 그냥 나란히 놓으면 안 되고, `http_req_waiting` 을 같이 봐야 한다.
- Nginx 프록시 요청 1건이 클라이언트+업스트림 2슬롯을 쓰므로 **동시 요청 약 768 이 Nginx 한계**다. 1000 VU 는 JVM 이 아니라 여기서 먼저 막힐 수 있다.
- Redis 축출 여지는 낮지만(1.3MB/64MB) `evicted_keys` 는 `post-check.txt` 에서 확인한다.

### 가장 중요한 제약: 판정 유효 시간 10분

한정반 구매는 PENDING 주문이고, `OrderExpirationScheduler` 가 60초마다 돌며 10분 지난 주문을 취소한다. 그때 `limited_purchase` 행이 **삭제되고** 재고가 복구된다. 초과판매 판정을 늦게 하면 증거가 스스로 사라진다는 뜻이다. `prod-run.sh` 가 k6 종료 직후, 쿨다운에 들어가기 전에 `verify-oversell.sh` 를 부르는 이유가 이것이다.

### 사전 준비

- SSH 접속이 가능해야 한다(`~/.ssh/groove-key.pem`, 보안그룹 22번이 현재 IP 로 열려 있어야 함). 네트워크가 바뀌었으면 AWS 콘솔에서 다시 열어야 한다.
- 운영 관리자 계정 자격증명을 `scripts/k6/.env.prod`(gitignore 대상, `chmod 600`)에 `ADMIN_EMAIL=` / `ADMIN_PASSWORD=` 로 둔다. `prod-run.sh` 가 `ADMIN_EMAIL`/`ADMIN_PASSWORD` 가 비어 있을 때 이 파일을 읽는다.
- **배포와 겹치지 않는 시간대에 한다.** CD 는 main push 마다 컨테이너를 재기동한다.
- 관리자로 API 로그인하면 refresh 토큰이 교체되어 브라우저에 열려 있던 관리자 세션이 로그아웃된다. 측정 중에는 관리자 화면을 쓰지 않는 게 좋다.

### 실행

스모크부터 돈다. 생성 → 구매 → 판정 → 정리 한 바퀴가 도는지 확인하고 본측정에 들어간다.

```bash
scripts/k6/prod-run.sh --smoke
```

`--smoke` 는 `--vus "10" --stock 3` 과 같다. 확인됐으면 본측정.

```bash
scripts/k6/prod-run.sh
```

기본 단계는 `--vus "50 200 500 1000"`, 단계별 재고는 `--stock 100`, 단계 사이 쿨다운은 `--cooldown 60`. 필요하면 옵션으로 바꾼다(`--vus`, `--stock`, `--base-url`, `--out`, `--cooldown`, `--no-monitor`, 자세한 건 `--help`).

결과는 `--out`(기본 `scripts/k6/results/prod-<YYYYMMDD-HHmmss>`) 밑에 남는다.

- `run.log` — 단계별 진행 로그.
- `summary.tsv` — 단계별 한 줄 요약(exit_code, status, p50/p95/p99, `http_req_waiting` p95, 총 요청 수, 실패율, rps, 성공/품절/서버에러 건수).
- `baseline-before.txt` / `baseline-after.txt` — 측정 전후 로컬 curl RTT.
- `drop-ids.txt` — 단계마다 생성된 dropId 누적(정리 시 사용).
- `vu-NNN/` (단계별 디렉토리):
  - `k6-stdout.log` — k6 실행 전체 출력.
  - `exit-code.txt` — k6 종료 코드.
  - `limited-vu-NNN-<timestamp>.json` — k6 JSON 요약(`limited-purchase.js` 의 `handleSummary`).
  - `resources.csv` — `monitor-remote.sh` 가 수집한 EC2 자원 시계열.
  - `pre-check.txt` / `post-check.txt` — `monitor-remote.sh` snapshot/stop 결과(컨테이너 상태, Nginx 경고, Redis 통계, dmesg, 백엔드 에러 로그).
  - `verify.txt` — `verify-oversell.sh` 판정 결과.

### 판정

기존 [판정 기준](#판정-기준) 절의 3종(성공 201 수 == 재고, `limited_purchase` 행 수 == 재고, `stock.quantity` == 0)에 Redis `limited:buyers` 카운트를 더한 4종을 `verify-oversell.sh` 가 자동으로 낸다. exit code:

| exit code | 의미 |
|---|---|
| 0 | 4종 모두 PASS |
| 1 | 하나 이상 FAIL |
| 2 | 대상 드롭 없음 또는 원격 조회 실패 |
| 3 | 최초 구매로부터 10분 초과 — 만료 스케줄러가 이미 데이터를 지웠을 수 있어 판정 자체가 무효일 수 있음 |

**임계값 실패(p95)는 사고가 아니라 결과다.** k6 exit 99 는 threshold 위반이고 `summary.tsv` 에 `THRESHOLD_FAIL` 로 남는다. 운영에서 p95 1초가 깨지면 그 사실을 그대로 기록한다. 반면 초과판매 판정은 어떤 단계에서도 타협하지 않는다.

### 정리 (필수)

dry-run 으로 먼저 확인하고, 문제없으면 `--apply`.

```bash
scripts/k6/cleanup-prod-loadtest.sh --drop-ids "<drop-ids.txt 내용>"
scripts/k6/cleanup-prod-loadtest.sh --drop-ids "<drop-ids.txt 내용>" --apply
```

`prod-run.sh` 가 종료 시 `drop-ids.txt` 를 바탕으로 위 두 명령을 그대로 출력해준다.

안전장치:

- `MEMBER_EMAIL_PREFIX`/`PRODUCT_TITLE_PREFIX` 는 4자 미만이거나 와일드카드(`%`, `_`)만으로 이루어지면 거부한다(전체 테이블 스캔 방지).
- 대상 회원 수가 `MAX_MEMBERS`(기본 2000)를 넘으면 거부한다.
- 삭제 대상에 `ADMIN` role 이 하나라도 섞여 있으면 거부한다.
- 전체 삭제가 트랜잭션 하나로 묶여 있다(부분 삭제로 끝나지 않는다).
- 삭제 후 같은 접두사로 재조회해 0건인지 사후 검증한다.

지우는 순서는 자식 → 부모(한정반 통계/구매 → 한정반 → 재고 이력 → 주문 관련 → 재고 → 상품 부속 → 회원 부속 → 상품/앨범 → 회원), 마지막에 Redis 의 `limited:stock:*`/`limited:buyers:*`/`limited:attempts:*`/`refresh:*` 키를 수집한 id 기준으로만 `DEL` 한다(`KEYS` 스캔 없음).

`sales_daily`/`sales_daily_product` 는 손대지 않는다. 집계 소스가 `payment.approved_at` 인데, 한정반 부하테스트 주문은 PENDING 상태라 애초에 payment 행이 생기지 않아 집계에 잡히지 않는다.

### 서비스가 죽었을 때

`prod-run.sh` 는 헬스체크가 실패하면 자동으로 재기동하지 않고 그 자리에서 멈춘다 — 죽은 지점이 결과이기 때문이다. 복구는 수동으로 한다.

```bash
ssh -i ~/.ssh/groove-key.pem ubuntu@52.78.95.139 'cd /opt/groove && docker compose -f docker-compose.prod.yml up -d'
```

원인은 `post-check.txt` 로 가른다.

- 컨테이너 `OOMKilled=true` 또는 `RestartCount` 증가 — 메모리 부족으로 죽었다는 뜻.
- Nginx `worker_connections are not enough` 경고 건수 — Nginx 슬롯 고갈.
- access.log 5xx 증가분 — 애플리케이션 레벨 실패.
- `resources.csv` 의 `cpu_steal_pct`(CPU 스틸) / `swap_used_mb`(스왑 사용량) 추이 — t3.micro 크레딧 고갈이나 메모리 압박 여부.

### 이번 범위 밖

운영에서 Redis ON/OFF 비교는 하지 않는다. compose 수정 + 재기동 2회 + 원복 누락 리스크에 비해 얻는 게 없다. 로컬에서 ON≈OFF 를 이미 확인했고 `results/limited-20260904.md` 에 있다.

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
