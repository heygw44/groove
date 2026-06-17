# ADR: PG 연동 모킹 전략

| 항목 | 값 |
|---|---|
| 상태 | Accepted |
| 날짜 | 2026-06-17 |
| 연관 이슈 | #257 (W12-2 ADR 정리) |
| 작성자 | ParkGunWoo |
| 관련 문서 | [improvements/idempotency.md](../improvements/idempotency.md), [ERD.md §payment](../ERD.md), [API.md §payments](../API.md) |

---

## Context

결제 도메인(W7)은 PG(토스·나이스페이 등) 연동을 전제로 설계됐다. 그러나 본 프로젝트는 **학습·포트폴리오 목적의 단일 인스턴스 서비스**이고, 실 PG 계약/심사/정산 계정이 없다. 그럼에도 결제는 다음 비기능 요건을 시연·검증해야 한다.

- **비동기 콜백**: 실 PG 는 승인 요청에 즉시 최종 결과를 주지 않는다 — `PENDING` 응답 후 웹훅으로 `PAID`/`FAILED` 가 도착한다.
- **멱등성**: 같은 결제 요청(Idempotency-Key)·중복 웹훅·환불 재시도가 부수효과를 1회로 수렴해야 한다(이슈 #160 멱등성 결함의 회귀 방지).
- **실패 보상**: `FAILED` 콜백 시 재고 복원 + 쿠폰 `USED→ISSUED` 복원이 일어나야 한다.
- **결정적 테스트**: CI/로컬에서 성공률·지연·웹훅 타이밍을 재현 가능하게 통제해야 한다.

따라서 결정 대상은 "실 PG 를 붙이느냐"가 아니라 **"실 PG 의 비동기·멱등 라이프사이클을 신뢰성 있게 흉내 내면서, 나중에 실 PG 로 교체 가능한 경계를 어떻게 두느냐"** 다.

---

## Decision

**PG 를 `PaymentGateway` 인터페이스(Strategy 패턴)로 추상화하고, `MockPaymentGateway` 구현으로 비동기 콜백형 라이프사이클을 재현한다.** 신규 인프라(실 PG SDK·외부 큐)는 도입하지 않는다.

```java
// payment/gateway/PaymentGateway.java
public interface PaymentGateway {
    PaymentResponse request(PaymentRequest request);   // 거래 식별자 + PENDING 즉시 응답
    PaymentStatus  query(String pgTransactionId);       // PG 측 상태 폴링
    RefundResponse refund(RefundRequest request);       // 환불 (idempotencyKey 기반)
}
```

핵심 동작:

- **요청 즉시 PENDING**: `MockPaymentGateway.request()` 는 `mock-tx-{UUID}` 거래 식별자를 발급하고 성공률(`success-rate`)로 최종 결과(`PAID`/`FAILED`)를 미리 정한 뒤, **발사 시각(`fireAt`)을 정해 `MockWebhookSimulator` 에 웹훅 콜백을 예약**하고 `PENDING` 으로 응답한다.

  ```java
  String pgTransactionId = "mock-tx-" + UUID.randomUUID();
  PaymentStatus result = rollOutcome();                       // success-rate 기반
  Instant fireAt = now.plus(randomDuration(webhookDelayMin, webhookDelayMax));
  if (autoWebhook) {
      webhookSimulator.scheduleCallback(pgTransactionId, orderNumber, result, fireAt);
  }
  return new PaymentResponse(pgTransactionId, PaymentStatus.PENDING, PROVIDER);
  ```

- **비동기 웹훅**: `MockWebhookSimulator` 가 전용 `TaskScheduler`(`paymentTaskScheduler`) 위에서 `fireAt` 에 일회성 작업을 예약, `WebhookDispatcher` 로 결제 결과를 통보한다 — 실 PG 의 서버→서버 콜백과 동형.
- **환불 멱등**: `refund()` 는 `idempotencyKey → 첫 응답` 캐시(`refundCache.computeIfAbsent`)로 같은 키 재호출 시 첫 응답을 그대로 반환한다.
- **프로파일 격리**: `@Profile({"local","dev","test","docker"})` — Mock 은 운영 외 프로파일에서만 활성. 실 PG 어댑터는 `prod` 프로파일에 별도 `@Component` 로 추가하면 된다.

상태·전이는 도메인이 보증한다. `PaymentStatus` 가 합법 전이만 허용하고(`PENDING→{PAID,FAILED}`, `PAID→{PARTIALLY_REFUNDED,REFUNDED}`, 나머지 종착), `payment.pg_transaction_id` 는 **UNIQUE** 로 거래 단위 멱등의 DB 보증선이 된다.

테스트에서는 `application-test.yaml` 로 `success-rate: 1.0`, `delay: 0ms`, `webhook-delay: 0ms` 로 **완전 결정화**하고, 멱등성 통합 테스트는 `auto-webhook=false` 로 웹훅을 끈 뒤 콜백을 직접 호출해 타이밍 비결정성을 제거한다.

---

## Considered Options

### Option A — 실 PG(토스/나이스페이) 직접 연동 ❌

| 항목 | 내용 |
|---|---|
| 방식 | 실 PG SDK/REST 연동, 테스트망(test key) 사용 |
| 충실도 | 최고 (실제 승인·망취소·부분취소 검증) |
| 비용 | PG 가맹 심사·정산 계정 필요, 테스트망도 외부 의존 |
| 결정성 | 낮음 — 외부 응답 지연·장애가 CI 를 비결정적으로 만든다 |
| 결론 | 학습·단일 인스턴스 스코프 대비 과투자, CI 신뢰성 저하 |

### Option B — 단순 동기 Mock (요청 즉시 PAID 반환) ⚠️

| 항목 | 내용 |
|---|---|
| 방식 | `request()` 가 곧바로 `PAID` 를 반환, 웹훅 없음 |
| 장점 | 구현 최소, 해피패스 빠름 |
| 단점 | **비동기 콜백·멱등·실패 보상 시연 불가** — 결제의 본질적 난점(중복 웹훅·폴링/웹훅 경합·보상 트랜잭션)을 전혀 검증하지 못함 |
| 결론 | 결제 도메인의 핵심 학습 가치를 잃음 |

### Option C — 인터페이스 + 콜백형 Mock ✅ (채택)

| 항목 | 내용 |
|---|---|
| 방식 | `PaymentGateway` 추상화 + `MockPaymentGateway`(PENDING→웹훅 콜백) + `MockWebhookSimulator`(TaskScheduler 예약) |
| 충실도 | 비동기 라이프사이클·멱등·실패 보상까지 재현 |
| 결정성 | 성공률·지연·웹훅 타이밍을 프로퍼티로 통제 → CI 결정적 |
| 교체성 | 인터페이스 분리로 실 PG 는 어댑터만 추가(`@Profile("prod")`) |
| 한계 | 망취소·부분취소 등 실 PG 엣지케이스는 미검증 |

---

## 비교 요약

| 기준 | A 실 PG | B 동기 Mock | **C 콜백형 Mock** |
|---|---|---|---|
| 비동기 웹훅 시연 | ✓ | ✗ | **✓** |
| 멱등/실패 보상 검증 | ✓ | ✗ | **✓** |
| CI 결정성 | ✗ | ✓ | **✓** |
| 외부 의존/계정 | 필요 | 없음 | **없음** |
| 실 PG 교체 용이성 | — | 낮음 | **높음(어댑터만)** |

---

## Consequences

**긍정적**
- 실 PG 계정 없이 결제 라이프사이클(PENDING→웹훅→PAID/FAILED→환불)을 로컬·CI 에서 결정적으로 검증한다.
- `pg_transaction_id` UNIQUE + 콜백 멱등키 + 환불 캐시로 **멱등 방어선이 3겹**(생성/콜백/환불)이며, 이는 동시 웹훅·폴링 경합 테스트(`PaymentCallbackConcurrencyTest`)의 기반이 된다.
- `PaymentGateway` 경계 덕에 실 PG 전환 시 도메인·서비스 코드 변경 없이 어댑터만 추가한다.

**부정적 / 트레이드오프**
- **모킹 충실도 한계**: 실 PG 의 망취소·부분취소·중복 정산·서명 스킴 차이는 Mock 으로 검증되지 않는다 → 실 PG 도입 시 그 어댑터 레벨 통합 테스트가 별도로 필요(그 시점에 본 ADR 을 Superseded 처리).
- **인메모리 상태**: Mock 거래 상태는 프로세스 메모리(`ConcurrentHashMap`)에만 있어 재기동 시 소실되고 `MAX_TRACKED_TRANSACTIONS`(10k) 상한으로 정리된다 — 테스트·시연 용도라 수용.

---

## References

- 코드: [`payment/gateway/PaymentGateway.java`](../../backend/src/main/java/com/groove/payment/gateway/PaymentGateway.java), [`MockPaymentGateway.java`](../../backend/src/main/java/com/groove/payment/gateway/mock/MockPaymentGateway.java), [`MockWebhookSimulator.java`](../../backend/src/main/java/com/groove/payment/gateway/mock/MockWebhookSimulator.java), [`PaymentStatus.java`](../../backend/src/main/java/com/groove/payment/domain/PaymentStatus.java)
- 멱등성 개선 사례: [improvements/idempotency.md](../improvements/idempotency.md)
- 결제 콜백 직렬화: 본 디렉토리 [concurrency-control.md](./concurrency-control.md)
- [Spring `TaskScheduler`](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
