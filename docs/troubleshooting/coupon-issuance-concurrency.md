# Coupon Issuance Concurrency — 선착순 쿠폰 발급의 초과발급과 개선 (확장)

> **상태**: 계획 (Skeleton) — 구현(쿠폰 P2) 후 §3 실측치·§6 Before/After 표를 채운다.
> 본 문서는 [재고 오버셀 baseline](./overselling-baseline.md) 의 **쿠폰판 자매 문서**다. 같은 동시성 결함이 다른 도메인에서 어떻게 재현되고 어떤 제어로 해소되는지를 한 쌍으로 보여준다.
> **결정 근거**: [decisions/coupon-concurrency.md](../decisions/coupon-concurrency.md) · **설계**: [plans/coupon-system.md](../plans/coupon-system.md)

---

## 1. 문제 정의

선착순 쿠폰 발급은 단일 행 `coupon.issued_count` 를 다수 요청이 동시에 증가시킨다. 베이스라인 구현은:

1. `SELECT issued_count, total_quantity FROM coupon WHERE id=?`
2. 인메모리 `issued_count < total_quantity` 검사
3. `INSERT member_coupon`
4. `UPDATE coupon SET issued_count = issued_count + 1`

이 구간에 락이 없으면, 두 트랜잭션이 같은 `issued_count` 스냅샷을 읽고 각자 증가분을 last-writer-wins 로 덮어써 **초과발급(over-issuance)** 이 발생한다 — 재고 오버셀과 동형(同型)의 lost-update 다.

## 2. 재현 절차 (계획)

| 항목 | 값(예정) | 설명 |
|---|---|---|
| 한정수량 | 100 | `coupon.total_quantity` |
| 동시 요청 수 | 300 | 서로 다른 회원 시뮬레이션 (UNIQUE 회피 위해 회원 분산) |
| 스레드 풀 | 64 | |
| 진입점 | `CouponIssueService.issue()` 직접 호출 | HTTP 계층 배제, race 만 노출 |
| 게이트 | `CountDownLatch` ready/done | 동시 출발 보장 |
| DB | Testcontainers MySQL `mysql:8.4` | 격리수준 기본 `REPEATABLE READ` |

> 회원당 1장 `UNIQUE(coupon_id, member_id)` 때문에 동일 회원 반복은 한 장으로 수렴하므로, **초과발급을 노출하려면 서로 다른 `member_id` 300명**으로 `issued_count` 글로벌 카운터를 때려야 한다.

### 통과 조건 (= 초과발급 증거)
- `발급된 member_coupon 수 > total_quantity(100)` — 한정수량 초과 발급
- 또는 `issued_count` 최종값이 실제 INSERT 수와 불일치 (lost-update 직접 증거)

## 3. 실측 결과 캡처 (구현 후 채움)

```
[coupon baseline] totalQuantity=100, concurrentRequests=300, threadPool=64, elapsedMs=TODO
[coupon baseline] issued=TODO, soldOut=TODO, other=TODO, finalIssuedCount=TODO, persistedMemberCoupons=TODO
```

→ TODO (초과발급 N건)

## 4. 개선 단계

[decisions/coupon-concurrency.md](../decisions/coupon-concurrency.md) 의 단계적 전략을 동일 시나리오로 재측정한다.

1. **비관적 락**: `@Lock(PESSIMISTIC_WRITE)` 로 coupon 행 잠금 → 직렬화. 초과발급 0, 처리량 측정.
2. **원자적 조건부 UPDATE (최종)**: `UPDATE coupon SET issued_count=issued_count+1 WHERE id=? AND issued_count<total_quantity` (affected=1 성공). 초과발급 0 + 처리량 ↑.

## 5. k6 부하 시나리오 (DoD #4, 계획)

`POST /coupons/{id}/issue` 에 회원 토큰 풀로 스파이크를 가해 TPS·p95·소진 시점·정확성(발급수==한정수량)을 측정한다. 베이스라인/비관적/원자적 3종을 비교 기록한다.

## 6. Before/After 비교 (구현·측정 후 채움)

| 지표 | Before (베이스라인) | After (비관적 락) | After (원자적 UPDATE) |
|---|---|---|---|
| 초과발급 (issued > total) | 발생 | 미발생 | 미발생 |
| finalIssuedCount == persisted | 불일치 | 일치 | 일치 |
| 처리량 (TPS) | TODO | TODO (낮음) | TODO (높음) |
| p95 지연 | TODO | TODO | TODO |

---

> 본 문서는 [overselling-baseline.md](./overselling-baseline.md) 와 함께 **"같은 lost-update, 두 도메인, 두 제어"** 를 보여주는 한 쌍이다 — 재고는 비관적 락, 쿠폰은 원자적 조건부 UPDATE 로 각각 해소해 제어 기법 선택의 트레이드오프를 대비시킨다.
