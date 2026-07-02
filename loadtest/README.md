# 부하 테스트 (k6)

> 선착순 쿠폰 발급의 **동시성·부하 검증 공식 수단**이다. 실 운영 사이트처럼 검증은 인앱 데모가 아니라
> 부하 테스트 도구(이 k6 스파이크) + 동시성 통합 테스트(`CouponIssuanceConcurrencyTest`)로 한다.

선착순 쿠폰 발급(`POST /api/v1/coupons/{id}/issue`)의 **프로덕션 원자적 조건부 UPDATE** 경로를 HTTP 계층에서
스파이크 측정한다. 3종 전략(베이스라인/비관적/원자적)의 **정확성·처리량 비교**는 인프로세스 JUnit
`CouponIssuanceConcurrencyTest` 가 담당하고(아래 §결과), 본 스크립트는 인증·멱등성·rate limit 을 포함한
현실적 처리량(TPS·p95)과 소진 시점·정확성을 측정해 남긴다.

## 파일
- `coupon-issuance.js` — k6 스파이크 스크립트 (토큰 풀 → ramping-vus 쇄도 → `issue_latency`/`coupon_issued` 집계)
- `seed-coupon-loadtest.sql` — 한정 100장 ACTIVE 쿠폰 1건 + 로그인 가능한 회원 600명 시드 (재실행 가능)
- `search.js` — 앨범 검색 부하 (#192): 토큰 풀 → ramping-vus 지속 탐색 → `search_latency` 집계. N+1·슬로우 쿼리 Before 측정
- `order.js` — 주문 생성 부하 (#193): 토큰 풀 → ramping-vus 다중 상품 주문 → `order_created`/`order_latency` 집계
- `payment.js` — 결제·멱등성 부하 (#193): 주문 1건 생성 → 동일 멱등키 결제 2회 → 중복 결제 없음(`payment_duplicated`=0) 검증
- `flash-sale.js` — 한정반 오버셀 재현 (#194): admin 으로 재고 100 리셋 → ramping-vus 로 단일 한정반에 동시 쇄도 → `order_created`(201) vs 최종 재고로 오버셀 판정(Before)
- `stock-restore.js` — 재고 복원 lost-update (#234, M16): 선행 PENDING 주문 시드 → 같은 앨범에 place(−1)·cancel(+1) 동시 인터리브 → `expected = stockAfterSeed − place + cancel` vs `finalStock` 으로 lost-update 자동 판정(Before/After)
- `deep-page.js` — 커서(keyset) vs offset 깊은 페이지 지연 비교 (#235, M16): setup 에서 깊은 커서 1개 확보 → `/albums?page=N`(offset)과 `/albums/scroll?cursor=…`(keyset)을 번갈아 측정 → `offset_deep_latency`/`keyset_deep_latency` 로 p95 격차 기록(`handleSummary` 가 개선율 로그). 시드가 클수록(ALBUM_COUNT=50000 기본) 격차가 두드러진다. 실행: `k6 run -e DEEP_ROW=10000 loadtest/deep-page.js`
  - **정렬키별 측정 (#244)**: `SORT` 를 바꿔 정렬 화이트리스트(`createdAt`/`price`/`releaseYear`) 전 경로의 keyset 이점을 잰다 — `-e SORT=price,desc` / `-e SORT=releaseYear,desc`. V25 커버링 인덱스(`idx_album_status_price`·`idx_album_status_year`) Before/After: price keyset p95 910→173 ms 로 붕괴됐던 깊은 페이지 이점 회복.
- `catalog-cache.js` — 카탈로그 조회 캐시 Before/After (#236, M16): 상세(`/albums/{id}`)·랜딩(`/albums`) 핫경로를 ramping-vus 로 측정 → `detail_latency`/`landing_latency` p95 기록. teardown 이 `/actuator/metrics/cache.gets` 로 적중률 로깅(local 프로파일). 캐시 OFF(SPRING_CACHE_TYPE=none) vs ON 앱을 상대로 같은 스크립트 실행.
- `cache-consistency.sh` — 멀티노드 캐시 일관성 Before/After (#366): scale=2 + 노드 직접 타깃팅(`docker compose exec --index`)으로 노드1 admin write(`adjustStock`) 후 노드2 read 의 stock 일치 여부 판정(k6 아닌 bash — nginx LB 라운드로빈이라 catalog-cache.js 로는 노드 고정 불가). Before(`SPRING_CACHE_TYPE=caffeine`, 노드 로컬): 노드2 최대 60s stale. After(`redis`, 공유): 노드2 즉시 일관.
- `rate-limit-distributed.sh` — 멀티노드 rate-limit 분산 Before/After (#367): nginx LB 로 로그인을 한도 초과 쇄도시켜 429 통과/차단 수를 센다(k6 아닌 bash). Before(`RATE_LIMIT_STORE=caffeine`, 노드 로컬 버킷): 통과 ≈ 2×CAP 로 실효 한도가 인스턴스 수에 비례. After(`redis`, 공유 버킷): 통과 ≈ CAP 로 노드 수와 무관하게 한도 유지. 실행: `AUTH_RATE_LIMIT_LOGIN_CAPACITY=5 docker compose -f docker-compose.yml -f docker-compose.scale.yml up -d --build` 후 `CAP=5 bash loadtest/rate-limit-distributed.sh`.
- `lib/auth.js` — 부하 시나리오 공통 하네스(`buildTokenPool`/`seedEmail`). 시드 회원 로그인 → access token 풀
- `lib/orders.js` — 주문 페이로드 공통 헬퍼(`buildOrderBody` 분산 / `buildSingleAlbumOrder` 단일고정). order/payment/flash-sale 가 재사용

> k6 end-of-test 요약 JSON(`*summary*.json`)은 실행마다 갱신되는 로컬 산출물이라 커밋하지 않는다(`.gitignore`).
> 대표 수치는 아래 결과 표에 정리해 둔다.

## 실행 절차

```bash
# 1) MySQL 기동 (호스트에서 접속하도록 3307 공개)
cat > /tmp/lt-override.yml <<'YML'
services:
  mysql:
    ports: ["3307:3306"]
YML
docker compose -f docker-compose.yml -f /tmp/lt-override.yml up -d mysql

# 2) 앱 기동 — rate limit 을 사실상 무제한으로 올려 측정 간섭 제거
DB_PORT=3307 DB_PASSWORD=changeme \
COUPON_RATE_LIMIT_ISSUE_CAPACITY=1000000 AUTH_RATE_LIMIT_LOGIN_CAPACITY=1000000 \
./backend/gradlew -p backend bootRun   # 백엔드는 backend/ 하위(#131). 헬스: curl http://localhost:8080/actuator/health

# 3) 시드 적용 (Flyway 마이그레이션 이후) — 매 실행 전 재적용해 issued_count 를 0 으로 리셋
docker exec -i groove-mysql-1 mysql -uroot -pchangeme-root groove < loadtest/seed-coupon-loadtest.sql

# 4) 쿠폰 id 를 읽어 k6 실행 (쿠폰 재삽입마다 auto-increment id 가 바뀌므로 매번 조회)
CID=$(docker exec groove-mysql-1 mysql -uroot -pchangeme-root -N \
  -e "SELECT id FROM groove.coupon WHERE name LIKE 'LOADTEST%' ORDER BY id DESC LIMIT 1;")
k6 run -e COUPON_ID=$CID loadtest/coupon-issuance.js

# 5) 정확성 검증 — issued_count 와 발급된 member_coupon 이 정확히 100, 초과발급 0
docker exec groove-mysql-1 mysql -uroot -pchangeme-root groove \
  -e "SELECT issued_count FROM coupon WHERE id=$CID;"
```

env 오버라이드: `BASE_URL`(기본 `http://localhost:8080`), `COUPON_ID`, `MEMBER_COUNT`(기본 600),
`PASSWORD`(기본 `Loadtest123!`).

## 결과 (실측)

로컬 1머신(Apple Silicon, Docker MySQL 8.4). 절대 수치는 머신 종속이며 **추세·정확성**이 핵심이다.

**k6 HTTP 스파이크 — 원자적 UPDATE 프로덕션 경로** (한정 100, 회원 600, 250 VU spike, 2회):

| run | 발급(201) | 거절(409) | issued_count(DB) | http_reqs/s | issue p95 | http_req_failed |
|-----|-----------|-----------|------------------|-------------|-----------|-----------------|
| 1 | **100** | 6,436 | **100** | 127.5 | 469 ms | 0% |
| 2 | **100** | 6,291 | **100** | 123.8 | 503 ms | 0% |

→ HTTP 계층에서도 **초과발급 0** (issued_count == 발급 member_coupon == 100). 인증·멱등성(요청당 DB 기록)·
rate limit 계층 때문에 인프로세스 직접 호출(~450 TPS)보다 처리량이 낮은 것은 예상된 비용이다.

---

# 검색 부하 (search.js)

앨범 검색(`GET /api/v1/albums`)의 필터 조합·키워드 검색을 부하 측정한다. 검색 경로는 V21 에서
**FULLTEXT(`ft_album_keyword`, ngram BOOLEAN MODE) + `@EntityGraph`** 로 전환돼 풀스캔·N+1 이 해소됐고, 본 부하는
그 **개선 후 경로**의 SLO(p95)·정확성을 측정한다(V21 이전 Before 베이스라인은 아래 결과 표에 기록). 토큰 풀 로그인은
공통 하네스 `lib/auth.js` 로 추출했고 후속 시나리오(order/payment 등)가 재사용한다.

> 검색은 public 이지만 현실적 '로그인 유저 탐색'(JWT 검증 비용 포함)을 측정하고자 Bearer 토큰을 부착한다.

## 실행 절차

```bash
# 1) MySQL 기동 (호스트에서 접속하도록 3307 공개) — 쿠폰 절차와 동일
cat > /tmp/lt-override.yml <<'YML'
services:
  mysql:
    ports: ["3307:3306"]
YML
docker compose -f docker-compose.yml -f /tmp/lt-override.yml up -d mysql

# 2) 앱 기동 — 로그인 rate limit 을 크게 올려 setup 의 로그인 버스트(80건) 간섭 제거
DB_PORT=3307 DB_PASSWORD=changeme \
AUTH_RATE_LIMIT_LOGIN_CAPACITY=1000000 \
./backend/gradlew -p backend bootRun

# 3) 메인 시드 적용 (Flyway 마이그레이션 이후) — 앨범 + loadtest001..080@groove.test / Test1234! 생성
#    ※ 쿠폰의 seed-coupon-loadtest.sql 이 아니라 측정용 메인 시드(#140)다.
#    --docker 로 컨테이너 내부 mysql 을 쓴다(호스트에 mysql 클라이언트가 없어도 동작; 비-docker 경로는 호스트 mysql 필요).
DB_PASSWORD=changeme ./scripts/seed.sh --docker --yes

# 4) k6 실행 (COUPON_ID 같은 사전 조회 불필요 — 검색은 시드 데이터에 바로 의존)
k6 run loadtest/search.js

# 5) (선택) 검색 실행계획 가시화 — FULLTEXT(type=fulltext, key=ft_album_keyword) 확인 (groove 유저, .env DB_PASSWORD)
docker exec groove-mysql-1 mysql -ugroove -pchangeme groove \
  -e "EXPLAIN SELECT * FROM album WHERE status='SELLING' AND MATCH(title, artist_name) AGAINST('\"love\"' IN BOOLEAN MODE) > 0 ORDER BY created_at DESC LIMIT 20;"
```

env 오버라이드: `BASE_URL`(기본 `http://localhost:8080`), `MEMBER_COUNT`(기본 80), `PASSWORD`(기본 `Test1234!`),
`EMAIL_PREFIX`(기본 `loadtest`), `EMAIL_DOMAIN`(기본 `@groove.test`).

결과 해석: `checks` rate(토큰 발급·검색 200 검증) > 0.99, `search_latency` p95·p99(검색 요청만, setup 로그인 제외),
`http_req_failed{phase:search}`, 그리고 종료 시 저장되는 `loadtest/search-summary.json`.

> `search_latency` p95 threshold(800ms)는 검색 SLO **회귀 가드**다 — FULLTEXT 전환 후 breach 되면 회귀 신호로
> 보면 된다. breach 시 k6 는 비정상 종료 코드(99)로 끝나지만, `abortOnFail` 을 쓰지 않아 테스트는 끝까지 돌고
> 요약 JSON 도 항상 생성된다.

## 결과 (실측) — V21/#204 이전 Before 베이스라인

아래 표는 **V21 FULLTEXT 전환 이전**의 LIKE 풀스캔·N+1 경로를 기록한 Before 수치다.
로컬 1머신(Apple Silicon, Docker MySQL 8.4). 절대 수치는 머신 종속이며 **추세·정확성**이 핵심이다.

**k6 검색 부하 — 앨범 50,000건, 회원 80, 50 VU 60s 지속 탐색**:

| 지표 | 값 |
|------|-----|
| iterations / req·s | 3,997 / ~60 |
| checks (200·content) | **100%** (7,994/7,994) |
| http_req_failed{phase:search} | **0%** |
| search p50 / p95 / p99 / max | 674 ms / **930 ms** / 1.00 s / 1.36 s |

→ checks·실패율은 건전하지만 **search p95 930 ms** 로 SLO(800 ms)를 초과. 당시 원인은 **N+1·`title LIKE
'%kw%'` 풀스캔**(`EXPLAIN` 시 type=ALL)이었고, 본 수치가 인덱스 개선의 **Before 베이스라인**이다. 현 코드 경로는
FULLTEXT(`ft_album_keyword`)+`@EntityGraph` 로 개선됐다(ERD §5.2) — 개선 후 실측 갱신은 #363 에서 다룬다.

---

# 주문·결제 부하 (order.js / payment.js)

주문 생성(`POST /api/v1/orders`)과 결제(`POST /api/v1/payments`)의 처리량/지연 Before 베이스라인을 기록하고,
결제의 **멱등성**(동일 `Idempotency-Key` 재요청이 중복 결제를 만들지 않음)을 부하 상황에서 check 로 검증한다.
재고 차감은 락 없이 단순 구현된 초기 경로이며, **동시성 정합성은 별도 동시성 테스트에서 측정**한다 — 여기서는
처리량과 멱등성 정확성에 집중한다. 두 시나리오 모두 search 와 동일한 메인 시드(앨범 50,000건 + 회원 80명)를 공유한다.

- `order.js` — 매 iteration 마다 서로 다른 앨범 3종을 주문. 시드 앨범 ~8%(SOLD_OUT/HIDDEN/stock=1)는 409/422
  로 거절되는데 이는 정상 도메인 응답이라 `expectedStatuses` 로 실패 집계에서 제외하고 `order_rejected` 로 집계한다.
- `payment.js` — iteration 당 주문 1건 생성(측정 제외 phase) → 멱등키 1개로 결제 2회 → `paymentId` 동일성 검증.
  멱등 위반(`payment_duplicated`)에 `count==0` 하드 threshold 를 건다.

## 실행 절차

```bash
# 1~3) MySQL 기동·앱 기동·메인 시드 — search.js 절차와 동일(scripts/seed.sh --docker --yes).
#       앱 기동 시 AUTH_RATE_LIMIT_LOGIN_CAPACITY 를 크게 주입해 setup 로그인 버스트(80건) 간섭 제거.
#       payment.js 측정 시에는 PAYMENT_MOCK_DELAY_MIN/MAX 를 0 으로 무력화한다 — Mock PG 의 처리 지연
#       (기본 100~500ms)은 설정된 가짜 sleep 이라, 0 으로 두어야 payment_latency 가 실제 서버 비용
#       (멱등성 이중기록 + DB)을 잰다. (주문/결제에서 429 가 보이면 해당 도메인 rate-limit capacity 도 크게 주입)
DB_PORT=3307 DB_PASSWORD=changeme \
AUTH_RATE_LIMIT_LOGIN_CAPACITY=1000000 \
PAYMENT_MOCK_DELAY_MIN=0ms PAYMENT_MOCK_DELAY_MAX=0ms \
./backend/gradlew -p backend bootRun
DB_PASSWORD=changeme ./scripts/seed.sh --docker --yes

# 4) k6 실행 (사전 조회 불필요 — 시드 데이터에 바로 의존)
k6 run loadtest/order.js      # → loadtest/order-summary.json
k6 run loadtest/payment.js    # → loadtest/payment-summary.json

# 5) (선택) 멱등성 DB 교차검증 — 결제 row 수가 첫 결제 건수(payment_requested)와 일치(재요청 추가 row 없음)
docker exec groove-mysql-1 mysql -ugroove -pchangeme groove \
  -e "SELECT COUNT(*) FROM payment; SELECT COUNT(*) FROM idempotency_record;"
```

env 오버라이드: `BASE_URL`(기본 `http://localhost:8080`), `MEMBER_COUNT`(기본 80), `ALBUM_COUNT`(기본 50000),
`PASSWORD`(기본 `Test1234!`), `EMAIL_PREFIX`(기본 `loadtest`), `EMAIL_DOMAIN`(기본 `@groove.test`).

> `ALBUM_COUNT` 는 실제 시드된 앨범 수 **이하**여야 한다 — 더 크면 범위 밖 albumId 가 404 를 받아 order 경로가
> `http_req_failed`·checks breach(red)로 설정 불일치를 드러낸다. 또한 두 시나리오는 `order_created`/`payment_requested`
> 에 `count>0` 게이트가 있어, 시드 누락 등으로 주문/결제가 0건이면 진공 통과 없이 실패한다.

결과 해석:
- order — `checks` rate > 0.99, `order_latency` p95·p99(주문 요청만), `http_req_failed{phase:order}`, `order_created`/`order_rejected` Counter.
- payment — **`payment_duplicated` count == 0**(멱등 위반 0), 첫 결제·재요청 모두 202, `payment_latency` p95·p99(첫 결제만), `http_req_failed{phase:payment}`.

> 주문은 재고를 영구 차감하고 결제는 `payment`/`idempotency_record` row 를 쌓으므로, 반복 측정 전에는
> `scripts/seed.sh --docker --yes` 로 재시드해 stock·결제·멱등 기록을 리셋하는 것을 권장한다.

## 결과 비교 표 양식

로컬 1머신(Apple Silicon, Docker MySQL 8.4) 기준. 절대 수치는 머신 종속이며 **추세·정확성**이 핵심이다.
아래는 본 시나리오 작성 시 캡처한 Before 베이스라인이며, 후속 측정 시 같은 양식에 run 을 덧붙인다.
TPS·p50/p95/p99 는 `order-summary.json`/`payment-summary.json` 의 커스텀 Trend(`order_latency`/`payment_latency`)
와 `http_reqs` 에서, error rate 는 `http_req_failed{phase:order|payment}` 에서 읽는다.

| 시나리오 | run | TPS(req/s) | p50 | p95 | p99 | error rate | 비고 |
|---|---|---|---|---|---|---|---|
| order(주문생성, 50VU 60s) | 1 | 873 | 49 ms | 65 ms | 83 ms | 0% | created 44,723 / rejected 12,844 (409·422 정상) |
| payment(결제, 30VU 60s, Mock지연0) | 1 | 405 | 55 ms | 101 ms | 131 ms | 0% | **payment_duplicated=0** (DB: payment 13,377 = 첫결제 13,377) |

> 지연은 성공 응답(order 201·payment 202)만 집계한다. 멱등성 검증: 동일 `Idempotency-Key` 재요청 13,377건이
> 추가 결제 row 를 단 1건도 만들지 않았다(payment rows == 첫 결제 건수 == DISTINCT order_id).
> checks 100%(첫 결제·재요청 모두 202 + 동일 paymentId).

---

# 한정반 오버셀 부하 (flash-sale.js)

재고 100인 단일 **한정반** 앨범에 동시 주문을 몰아 **오버셀(oversell)을 부하로 재현**한다(동시성 개선의 Before).
현재 `OrderService.place()` 의 재고 차감은 락 없는 read-modify-write(SELECT → 인메모리 `adjustStock(-qty)` →
커밋 시 dirty-check flush)라 동시 주문 시 lost-update 가 발생한다(락 없는 baseline 보존, `OversellingBaselineTest` 가
단위테스트로 입증). 백엔드는 수정하지 않으며, 비관적 락 적용 후 동일 스크립트로 After 를 측정한다.

- **재고 리셋은 setup() 이 자동 수행**한다 — admin 으로 로그인해 `GET /api/v1/albums/{id}` 로 현재 재고를 읽고
  `PATCH /api/v1/admin/albums/{id}/stock`(delta 증감)으로 정확히 100 으로 맞춘다. 재실행마다 동일 조건이 된다.
- 워밍업 없이 곧장 PEAK 로 급상승(스파이크)한다 — 소량 워밍 VU 가 재고를 미리 소진하면 오버셀이 증폭되지 않기 때문.
- Before 측정이라 실패를 게이트하지 않는다 — 소진 후 409, 단일 행 경합으로 인한 5xx 는 기대되는 결과다.
  유일한 하드 게이트는 `order_created: count>0`(주문이 실제로 일어났는지).

> **재고 기반 시나리오는 캐시 OFF(`SPRING_CACHE_TYPE=none`)로 측정한다 (#369).** teardown 이 최종 재고를
> `GET /api/v1/albums/{id}`(= 캐시되는 `albumDetail`)로 읽어 `final_stock` 으로 emit 하는데, 캐시 ON 이면
> 주문이 무효화한 stale 재고를 읽어 오버셀/lost-update 자동 판정이 빗나간다. `stock-restore.js` 도 동일.

## 실행 절차

```bash
# 1~3) MySQL 기동·앱 기동·메인 시드 — search.js 절차와 동일(scripts/seed.sh --docker --yes).
#       앱 기동 시 AUTH_RATE_LIMIT_LOGIN_CAPACITY 를 크게 주입해 setup 로그인 버스트(회원 80 + admin 1) 간섭 제거.
#       주문 경로 자체에는 rate limit 이 없으므로 별도 capacity 주입은 불필요하다.
#       SPRING_CACHE_TYPE=none 필수 — teardown 의 재고 판정이 캐시된 stale 재고를 읽지 않게 한다(아래 경고).
DB_PORT=3307 DB_PASSWORD=changeme \
AUTH_RATE_LIMIT_LOGIN_CAPACITY=1000000 \
SPRING_CACHE_TYPE=none \
./backend/gradlew -p backend bootRun
DB_PASSWORD=changeme ./scripts/seed.sh --docker --yes

# 4) 타깃 한정반 id 조회 (판매중 한정반 1건) → TARGET_ALBUM_ID 로 전달(미지정 시 기본 1)
TID=$(docker exec groove-mysql-1 mysql -ugroove -pchangeme groove -N \
  -e "SELECT id FROM album WHERE is_limited=1 AND status='SELLING' ORDER BY id LIMIT 1;")

# 5) k6 실행 — 부하 레벨 100/500/1000 을 PEAK_VUS 로 바꿔 반복(요약은 매번 flash-sale-summary.json 덮어씀)
k6 run -e TARGET_ALBUM_ID=$TID -e PEAK_VUS=1000 loadtest/flash-sale.js

# 6) 오버셀 교차검증 — 최종 재고와 영속화된 주문 수 비교
docker exec groove-mysql-1 mysql -ugroove -pchangeme groove \
  -e "SELECT stock FROM album WHERE id=$TID; SELECT COUNT(*),SUM(quantity) FROM order_item WHERE album_id=$TID;"
```

env 오버라이드: `BASE_URL`(기본 `http://localhost:8080`), `TARGET_ALBUM_ID`(기본 1), `PEAK_VUS`(기본 1000, 부하 레벨),
`INITIAL_STOCK`(기본 100), `MEMBER_COUNT`(기본 80), `PASSWORD`(기본 `Test1234!`),
`ADMIN_EMAIL`(기본 `loadtest-admin@groove.test`), `ADMIN_PASSWORD`(기본 `PASSWORD`).

> 재고 리셋은 setup() 의 admin API 로 자동 수행된다. admin 경로가 막힌 환경이라면 수동 대안:
> `UPDATE album SET stock=100, status='SELLING' WHERE id=<id>;`

## 단일 재고(stock=1) 경계 검증 (#209)

오버셀의 가장 극적인 경계 — **1장짜리 희귀반에 동시 100 주문 → 정확히 1건만 성공**. 신규 스크립트 없이
`INITIAL_STOCK=1` 오버라이드만으로 재현한다(setup 이 타깃 재고를 1로 리셋). 시드의 `stock=1` 8건
(id 545·643·749·750·971·1037·1069·1841) 중 하나를 `TARGET_ALBUM_ID` 로 줘도 되고, 임의 한정반을 써도 된다.

```bash
# 타깃을 stock=1 로 리셋 후 동시 100 스파이크 → order_created 정확히 1, 오버셀 없음
k6 run -e TARGET_ALBUM_ID=$TID -e INITIAL_STOCK=1 -e PEAK_VUS=100 loadtest/flash-sale.js

# 교차검증 — 최종 재고 0, 영속 주문 정확히 1
docker exec groove-mysql-1 mysql -ugroove -pchangeme groove \
  -e "SELECT stock FROM album WHERE id=$TID; SELECT COUNT(*) FROM order_item WHERE album_id=$TID;"
```

기대: `order_created=1`, 자동 판정 `오버셀 없음`, `order_item` 정확히 1행. 결정론적 증명은 인프로세스
회귀 가드 `OversellingBaselineTest.concurrentOrders_singleStockRarity_exactlyOneSucceeds` 가 담당하며
(`success=1, insufficient=99, other=0, finalStock=0, persistedOrders=1`), 본 k6 는 HTTP 부하로 같은 결론을
재현하는 선택 시나리오다.

## 오버셀 판정

teardown 이 최종 재고를 `final_stock` Gauge 로 emit 하고, handleSummary 가 `order_created`(201) 와 합산해
**단일 이진 판정을 자동 출력**한다(`[#194 오버셀 baseline] ... → 오버셀 재현됨 / 없음`).

```
오버셀 판정(자동): order_created > 100        (재고보다 많이 팔림 — 이슈 DoD)
              OR  order_created > consumed   (lost-update — 주문 수 > 실제 재고 감소량, consumed = 100 − finalStock)
```

> 락 경합으로 성공 응답이 100 미만에 머물러도(베이스라인 단위테스트 success 27~34 < 100) lost-update 조건이
> 잡아낸다. teardown 재고 조회가 실패하면 `created > 100` 약식 판정으로 폴백한다.

결과 JSON 은 실행 시 `loadtest/flash-sale-summary.json`(최근 실행 = After, 1000 VU)에 로컬 저장된다. Before/After
대표 수치는 각각 #205 락 임시 해제(`placeWithoutLock` 경로, 1000 VU: created 224·lost-update 124·finalStock 0)와
#205 비관락(created 100·lost-update 0)이며, Before 는 `SPRING_CACHE_TYPE=none` 으로 띄운 동일 시드(앨범 50,000)에서 측정했다.

## 결과 (실측 — Before)

로컬 1머신(Apple Silicon, Docker MySQL 8.4, 앱 docker 프로파일). 타깃 한정반 1건(재고 100 리셋), 회원 80 라운드로빈.
절대 수치는 머신 종속이며 **오버셀 재현(추세)** 이 핵심이다.

| 부하(PEAK_VUS) | order_created(201) | finalStock | consumed | lost-update | 오버셀 | order p95 | soldout(409) / failed(5xx) |
|---|---|---|---|---|---|---|---|
| 100 | **221** | 0 | 100 | **121** | ✅ created>100 | 264 ms | 8,380 / 959(9.9%) |
| 500 | **222** | 0 | 100 | **122** | ✅ created>100 | 704 ms | 10,726 / 727(6.2%) |
| 1000 | **211** | 0 | 100 | **111** | ✅ created>100 | 1.25 s | 10,834 / 685(5.8%) |

→ 세 레벨 모두 **재고 100에 201 성공이 211~222건** = 재고보다 ~2.2배 과판매(`created > 100`)이고, 최종 재고는
0인데 영속화된 주문은 211~222건이라 **lost-update 111~122건**이 자동 판정됐다. 단일 행 경합으로 5xx(락 타임아웃·
데드락)가 6~10% 발생(`order_failed`)하는 것도 Before 의 실패 양상이다. 비관적 락/원자적 차감 후 동일
시나리오에서 `created ≤ 100` · `lost-update 0` 으로 수렴해야 한다.

---

# 재고 복원 lost-update 부하 (stock-restore.js)

`place`(차감, #205 비관락)와 **재고 복원 경로**(취소·환불·결제실패 보상·반품 재입고)를 같은 앨범 행에서 동시에
인터리브해 **place↔복원 lost-update** 를 부하 계층에서 측정한다(#234, M16). 복원이 락 없는 read-modify-write 면
place 와 복원이 같은 `album.stock` 을 두고 경합해 lost-update 가 누적된다(Before). #234 의 원자적 가산 UPDATE
(`AlbumRepository.restoreStock` — `UPDATE album SET stock=stock+:delta WHERE id=:id`)는 DB 한 문장에서 상대 증분을
적용하고 place 의 `SELECT … FOR UPDATE` 와 같은 행에서 직렬화되어 lost-update 0 이어야 한다(After).

- **setup() 자동 준비**: admin 으로 재고를 `INITIAL_STOCK`(기본 `max(300, 3·SEED_ORDERS)`, ≥2·SEED 라야 최악
  인터리빙에서도 재고부족 미발생)으로 리셋 → 토큰 풀 로그인 → 선행 PENDING 주문 `SEED_ORDERS` 건 생성(동시
  구간에서 취소할 대상, 취소는 본인만 가능하므로 소유 토큰을 함께 기록).
- **default()**: 전역 카운터 짝수=신규 `POST /orders`(−1), 홀수=선행 주문 `POST /orders/{n}/cancel`(+1). 두 작업이
  같은 album 행에서 동시 경합한다(총 `2·SEED_ORDERS` 회, `shared-iterations`).

## 실행 절차

```bash
# 1~3) MySQL·앱·메인 시드 — flash-sale 절차와 동일(AUTH_RATE_LIMIT_LOGIN_CAPACITY 크게 + SPRING_CACHE_TYPE=none).
#       SPRING_CACHE_TYPE=none 필수 — teardown 이 캐시된 stale 재고로 lost-update 를 오판하지 않게 한다(#369).
TID=$(docker exec groove-mysql-1 mysql -ugroove -pchangeme groove -N \
  -e "SELECT id FROM album WHERE status='SELLING' ORDER BY id LIMIT 1;")

# 4) Before(현행 main) → After(#234 적용 코드) 각각에서 동일 실행 후 자동 판정 비교
k6 run -e TARGET_ALBUM_ID=$TID -e SEED_ORDERS=100 -e PEAK_VUS=100 loadtest/stock-restore.js

# 5) 교차검증 — 최종 재고와 기대치(stockAfterSeed − place + cancel) 비교
docker exec groove-mysql-1 mysql -ugroove -pchangeme groove \
  -e "SELECT stock FROM album WHERE id=$TID;"
```

env 오버라이드: `TARGET_ALBUM_ID`(기본 1), `SEED_ORDERS`(기본 100, =복원 횟수), `PEAK_VUS`(기본 100),
`INITIAL_STOCK`(기본 `max(300, 3·SEED_ORDERS)`), 그 외 `flash-sale.js` 와 동일.

## lost-update 판정

teardown 이 `final_stock`, setup 이 `stock_after_seed` Gauge 를 emit 하고 handleSummary 가 자동 판정한다:

```
expected = stock_after_seed − place_created(201) + cancel_ok(200)
lost     = expected − final_stock        # 원자적이면 0, 락 없는 RMW 면 ≠ 0
```

결정론적 증명은 인프로세스 회귀 가드 `StockRestoreConcurrencyTest`
(`concurrentPlaceAndCancel_atomicRestore_noLostUpdate` = lost-update 0,
`@Disabled` baseline = RMW 복원 lost-update 재현)가 담당하며, 본 k6 는 HTTP 부하로 같은 결론을 재현한다.

---

# 멀티노드 캐시 일관성 (cache-consistency.sh)

카탈로그 read 캐시(`albumDetail`/`albumLandingList`)가 노드 로컬 Caffeine(#236)일 때, 노드 A 의 admin write
`@CacheEvict` 는 노드 B 의 캐시를 못 비워 **최대 TTL(60s) 동안 stale** 이다. #366 은 멀티노드(docker/prod)에서
캐시를 공유 Redis 로 옮겨 노드 간 무효화를 일관시킨다(`spring.cache.type=redis`, base 단일 인스턴스는 caffeine 유지).

nginx LB 는 매 요청 라운드로빈이라 어느 노드가 write/read 를 받는지 고정할 수 없으므로, `cache-consistency.sh` 가
app replica 를 `docker compose exec --index` 로 직접 타깃팅한다(app 은 호스트 포트 미발행 → 컨테이너 내부 curl).

## 실행 절차

```bash
# 1) scale=2 기동 (app 2 replica + redis + nginx). After 측정 = 기본 redis.
docker compose -f docker-compose.yml -f docker-compose.scale.yml up -d --build

# 2) 시드 — admin(loadtest-admin@groove.test / Test1234!) + SELLING 앨범. (메인 시드 scripts/seed.sh 또는 데모 시드)

# 3) After(redis) 재현 — 노드1 write 후 노드2 즉시 일관(✅)
loadtest/cache-consistency.sh

# 4) Before(caffeine) 재현 — 같은 scale=2 를 SPRING_CACHE_TYPE=caffeine 으로 재기동 후 실행 → 노드2 최대 60s stale(❌)
SPRING_CACHE_TYPE=caffeine docker compose -f docker-compose.yml -f docker-compose.scale.yml up -d --force-recreate
loadtest/cache-consistency.sh
```

env 오버라이드: `ALBUM_ID`(기본 첫 SELLING), `DELTA`(기본 7), `POLL_SECONDS`(기본 3), `ADMIN_EMAIL`/`ADMIN_PASSWORD`.

## 판정

스크립트가 노드1 에서 `adjustStock(+DELTA)` 한 뒤 노드2 GET 을 `POLL_SECONDS` 동안 반복해 stock 을 비교한다:

```
After (redis):    노드2 가 즉시 expected(=old+DELTA) 관측        → ✅ 일관
Before(caffeine): 노드2 가 POLL_SECONDS 내내 old 관측(≠expected) → ❌ stale (60s TTL 후 자가치유)
```

인프로세스 회귀 가드는 `AlbumRedisCacheTest`(Testcontainers Redis, `spring.cache.type=redis`)가 담당하며 —
`Page<AlbumSummaryResponse>`(PageImpl) JDK 직렬화 왕복 성공을 검증한다 — 단일 인스턴스 캐시 거동은
`AlbumCacheTest`(caffeine)가 회귀 가드한다.
