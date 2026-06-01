# Coupon Issuance Concurrency — 선착순 쿠폰 발급의 초과발급과 개선 (확장)

> **상태**: 완료 (#93) — §3 인프로세스 3종 실측 · §5 k6 HTTP 실측 · §6 Before/After 채움.
> 본 문서는 [재고 오버셀 baseline](./overselling-baseline.md) 의 **쿠폰판 자매 문서**다. 같은 동시성 결함이 다른 도메인에서 어떻게 재현되고 어떤 제어로 해소되는지를 한 쌍으로 보여준다.
> **결정 근거**: [decisions/coupon-concurrency.md](../decisions/coupon-concurrency.md) · **설계**: [plans/coupon-system.md](../plans/coupon-system.md) · **측정**: 2026-06-01, 로컬 1머신(Apple Silicon) · Testcontainers MySQL 8.4 / k6 v2.0.0

---

## 1. 문제 정의

선착순 쿠폰 발급은 단일 행 `coupon.issued_count` 를 다수 요청이 동시에 증가시킨다. 베이스라인 구현은:

1. `SELECT issued_count, total_quantity FROM coupon WHERE id=?`
2. 인메모리 `issued_count < total_quantity` 검사
3. `INSERT member_coupon`
4. `UPDATE coupon SET issued_count = issued_count + 1`

이 구간에 락이 없으면, 두 트랜잭션이 같은 `issued_count` 스냅샷을 읽고 각자 증가분을 last-writer-wins 로 덮어써 **초과발급(over-issuance)** 이 발생한다 — 재고 오버셀과 동형(同型)의 lost-update 다.

## 2. 재현 절차

| 항목 | 값 | 설명 |
|---|---|---|
| 한정수량 | 100 | `coupon.total_quantity` |
| 동시 요청 수 | 300 | 서로 다른 회원 (UNIQUE 회피 위해 회원 분산) |
| 스레드 풀 | 32 | `THREAD_POOL_SIZE` |
| 진입점 | `CouponIssueService` 전략별 메서드 직접 호출 | HTTP 계층 배제, race 만 노출 |
| 게이트 | `CountDownLatch` ready/done | 동시 출발 보장 |
| DB | Testcontainers MySQL `mysql:8.4` | 격리수준 기본 `REPEATABLE READ` |

> 회원당 1장 `UNIQUE(coupon_id, member_id)` 때문에 동일 회원 반복은 한 장으로 수렴하므로, **초과발급을 노출하려면 서로 다른 `member_id` 300명**으로 `issued_count` 글로벌 카운터를 때려야 한다.

### 통과 조건 (= 초과발급 증거)
- `발급된 member_coupon 수 > total_quantity(100)` — 한정수량 초과 발급
- 또는 `issued_count` 최종값이 실제 INSERT 수와 불일치 (lost-update 직접 증거)

## 3. 실측 결과 — 인프로세스 3종 (`CouponIssuanceConcurrencyTest`, 2026-06-01)

세 전략을 같은 시나리오(한정 100 / 동시 300 / 풀 32)로 측정했다. TPS·p95 는 격리 실행(전략별 새 JVM) 2회 범위다.

**베이스라인 (락 없음) — `issueWithoutLock`** (시연용 `@Disabled`, 실측 시 임시 활성화)
```
[#93 baseline] success=48, soldOut=0, other=252, issuedCount=24, persisted=48 (limit=100) | elapsedMs=292, tps=1027, p95Ms=78
```
→ **초과발급(lost-update) 증거**: `persisted(48) > issuedCount(24)` — 커밋된 48건 중 카운터엔 24만 반영(24건 유실). 한정 100 을 못 넘긴 건 결함이 약해서가 아니라, `other=252` 가 전부 `CannotAcquireLockException` 이라서다 — 무조정 동시 쓰기가 같은 `coupon` 행 락을 두고 thrash 해 84%(252/300)가 롤백됐다. **정확성(lost-update)·안정성(락 thrash) 동시 붕괴.**

**비관적 락 — `issueWithPessimisticLock`** (`SELECT ... FOR UPDATE` 행 락)
```
[#93 비관적] success=100, soldOut=200, other=0, issuedCount=100, persisted=100 (limit=100) | elapsedMs≈611–657, tps≈457–491, p95Ms≈170–180
```
→ 정확히 100, 초과발급 0. 행 락으로 직렬화 — 정확하지만 락을 트랜잭션 전 구간(INSERT·flush 포함) 동안 보유한다.

**원자적 조건부 UPDATE — `issue`(프로덕션)** `UPDATE coupon SET issued_count=issued_count+1 WHERE id=? AND status='ACTIVE' AND issued_count<total_quantity`
```
[#93 원자적] success=100, soldOut=200, other=0, issuedCount=100, persisted=100 (limit=100) | elapsedMs≈587–801, tps≈375–511, p95Ms≈166–302
```
→ 정확히 100, 초과발급 0. 소진 검사+증가를 단일 문장으로 원자 처리해 행 락 보유가 짧다.

> 두 제어(비관적·원자적)의 인프로세스 처리량은 300요청·로컬 단일 행 규모에서는 노이즈 범위로 **사실상 동률**이다 — 원자적 UPDATE 의 짧은 락 보유 이점은 HTTP 계층·더 높은 동시성에서 드러난다(§5). **헤드라인은 정확성**: 베이스라인만 lost-update·락 thrash, 두 제어는 초과발급 0.

## 4. 개선 단계

[decisions/coupon-concurrency.md](../decisions/coupon-concurrency.md) 의 단계적 전략을 동일 시나리오로 재측정했다(§3).

1. **비관적 락**: `@Lock(PESSIMISTIC_WRITE)` 로 coupon 행 잠금 → 직렬화. 초과발급 0.
2. **원자적 조건부 UPDATE (최종)**: `WHERE issued_count<total_quantity` 조건부 UPDATE (affected=1 성공). 초과발급 0 + 락 보유 최소화. 프로덕션 발급 경로가 이 전략을 쓴다.

## 5. k6 부하 — 프로덕션 원자적 경로 HTTP 스파이크 (DoD #4)

`POST /api/v1/coupons/{id}/issue` 에 회원 토큰 풀(600명)로 250 VU 스파이크를 가해 현실적 처리량·정확성을 측정했다. 스크립트·재현 절차: [loadtest/](../../loadtest/). 3종 전략 비교는 §3(인프로세스)에서 다루고, k6 는 **인증·멱등성·rate limit 을 포함한 프로덕션 경로**를 박제한다.

| run | 발급(201) | 거절(409) | issued_count(DB) | http_reqs/s | issue p95 | http_req_failed |
|---|---|---|---|---|---|---|
| 1 | **100** | 6,436 | **100** | 127.5 | 469 ms | 0% |
| 2 | **100** | 6,291 | **100** | 123.8 | 503 ms | 0% |

→ HTTP 계층에서도 **초과발급 0** (`issued_count` == 발급 `member_coupon` == 100). 인증 필터·멱등성(요청당 DB 기록)·rate limit 비용으로 인프로세스 직접 호출(~450 TPS)보다 처리량이 낮은 것은 예상된 결과다 — k6 의 역할은 "프로덕션 경로가 쇄도에도 정확히 100 장만 내준다"의 증명이다.

## 6. Before/After 비교

| 지표 | Before (베이스라인) | After (비관적 락) | After (원자적 UPDATE, 프로덕션) |
|---|---|---|---|
| 초과발급 (issued > total) | 발생 (lost-update) | 미발생 | 미발생 |
| finalIssuedCount == persisted | 불일치 (24 ≠ 48) | 일치 (100 = 100) | 일치 (100 = 100) |
| 락 경합 실패 (CannotAcquireLock) | 252/300 (84%) | 0 | 0 |
| 인프로세스 처리량 (TPS) | — (대부분 락 실패) | ~457–491 | ~375–511 |
| 인프로세스 p95 | — | ~170–180 ms | ~166–302 ms |
| HTTP 처리량 / p95 (k6) | — | — | ~124–128 req/s · ~469–503 ms |

---

> 본 문서는 [overselling-baseline.md](./overselling-baseline.md) 와 함께 **"같은 lost-update, 두 도메인, 두 제어"** 를 보여주는 한 쌍이다 — 재고는 비관적 락, 쿠폰은 원자적 조건부 UPDATE 로 각각 해소해 제어 기법 선택의 트레이드오프를 대비시킨다.
