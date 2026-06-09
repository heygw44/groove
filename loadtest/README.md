# 부하 테스트 (k6)

> 선착순 쿠폰 발급의 **동시성·부하 검증 공식 수단**이다. 실 운영 사이트처럼 검증은 인앱 데모가 아니라
> 부하 테스트 도구(이 k6 스파이크) + 동시성 통합 테스트(`CouponIssuanceConcurrencyTest`)로 한다.

선착순 쿠폰 발급(`POST /api/v1/coupons/{id}/issue`)의 **프로덕션 원자적 조건부 UPDATE** 경로를 HTTP 계층에서
스파이크 측정한다. 3종 전략(베이스라인/비관적/원자적)의 **정확성·처리량 비교**는 인프로세스 JUnit
`CouponIssuanceConcurrencyTest` 가 담당하고(아래 §결과), 본 스크립트는 인증·멱등성·rate limit 을 포함한
현실적 처리량(TPS·p95)과 소진 시점·정확성을 박제한다.

> 측정·서사: [docs/troubleshooting/coupon-issuance-concurrency.md](../docs/troubleshooting/coupon-issuance-concurrency.md)

## 파일
- `coupon-issuance.js` — k6 스파이크 스크립트 (토큰 풀 → ramping-vus 쇄도 → `issue_latency`/`coupon_issued` 집계)
- `seed-coupon-loadtest.sql` — 한정 100장 ACTIVE 쿠폰 1건 + 로그인 가능한 회원 600명 시드 (재실행 가능)
- `search.js` — 앨범 검색 부하 (#192, W9): 토큰 풀 → ramping-vus 지속 탐색 → `search_latency` 집계. N+1·슬로우 쿼리 Before 측정
- `lib/auth.js` — W9 시나리오 공통 하네스(`buildTokenPool`/`seedEmail`). 시드 회원 로그인 → access token 풀
- `summary.json` — 쿠폰 k6 end-of-test 요약(최근 실행 캡처)
- `search-summary.json` — 검색 k6 end-of-test 요약(최근 실행 캡처)

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

앨범 검색(`GET /api/v1/albums`)의 필터 조합·키워드 검색을 부하 측정한다. 검색 경로는 N+1·풀스캔 LIKE
슬로우 쿼리가 W10 시연용으로 **의도 보존**된 상태이며, 본 부하는 그 **Before(개선 전) 베이스라인**을 박제하는 것이
목적이다. 토큰 풀 로그인은 W9 공통 하네스 `lib/auth.js` 로 추출했고 후속 시나리오(order/payment 등)가 재사용한다.

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

# 5) (선택) 슬로우 쿼리 가시화 — 풀스캔(type=ALL) 확인 (groove 유저, .env DB_PASSWORD)
docker exec groove-mysql-1 mysql -ugroove -pchangeme groove \
  -e "EXPLAIN SELECT * FROM album WHERE title LIKE '%love%';"
```

env 오버라이드: `BASE_URL`(기본 `http://localhost:8080`), `MEMBER_COUNT`(기본 80), `PASSWORD`(기본 `Test1234!`),
`EMAIL_PREFIX`(기본 `loadtest`), `EMAIL_DOMAIN`(기본 `@groove.test`).

결과 해석: `checks` rate(토큰 발급·검색 200 검증) > 0.99, `search_latency` p95·p99(검색 요청만, setup 로그인 제외),
`http_req_failed{phase:search}`, 그리고 종료 시 저장되는 `loadtest/search-summary.json`.

> 슬로우 쿼리로 `search_latency` p95 threshold(800ms)가 breach 되면 k6 는 비정상 종료 코드(99)로 끝난다 —
> 이는 **의도된 Before 측정 결과**이며 `abortOnFail` 을 쓰지 않아 테스트는 끝까지 돌고 요약 JSON 도 항상 생성된다.

## 결과 (실측)

로컬 1머신(Apple Silicon, Docker MySQL 8.4). 절대 수치는 머신 종속이며 **추세·정확성**이 핵심이다.

**k6 검색 부하 — 앨범 50,000건, 회원 80, 50 VU 60s 지속 탐색**:

| 지표 | 값 |
|------|-----|
| iterations / req·s | 3,997 / ~60 |
| checks (200·content) | **100%** (7,994/7,994) |
| http_req_failed{phase:search} | **0%** |
| search p50 / p95 / p99 / max | 674 ms / **930 ms** / 1.00 s / 1.36 s |

→ checks·실패율은 건전하지만 **search p95 930 ms** 로 SLO(800 ms)를 초과. 원인은 의도 보존된 **N+1·`title LIKE
'%kw%'` 풀스캔**(`EXPLAIN` 시 type=ALL)이며, 본 수치가 W10 개선의 **Before 베이스라인**이다.
