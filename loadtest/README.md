# 부하 테스트 (k6)

선착순 쿠폰 발급(`POST /api/v1/coupons/{id}/issue`)의 **프로덕션 원자적 조건부 UPDATE** 경로를 HTTP 계층에서
스파이크 측정한다. 3종 전략(베이스라인/비관적/원자적)의 **정확성·처리량 비교**는 인프로세스 JUnit
`CouponIssuanceConcurrencyTest` 가 담당하고(아래 §결과), 본 스크립트는 인증·멱등성·rate limit 을 포함한
현실적 처리량(TPS·p95)과 소진 시점·정확성을 박제한다.

> 측정·서사: [docs/troubleshooting/coupon-issuance-concurrency.md](../docs/troubleshooting/coupon-issuance-concurrency.md)

## 파일
- `coupon-issuance.js` — k6 스파이크 스크립트 (토큰 풀 → ramping-vus 쇄도 → `issue_latency`/`coupon_issued` 집계)
- `seed-coupon-loadtest.sql` — 한정 100장 ACTIVE 쿠폰 1건 + 로그인 가능한 회원 600명 시드 (재실행 가능)
- `summary.json` — k6 end-of-test 요약(최근 실행 캡처)

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
