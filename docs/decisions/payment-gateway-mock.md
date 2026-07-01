# ADR: PG 연동 모킹 전략

| 항목 | 값 |
|---|---|
| 상태 | Accepted |
| 날짜 | 2026-06-17 |
| 연관 이슈 | #257 (ADR 정리) |
| 작성자 | ParkGunWoo |
| 관련 문서 | [ERD.md §payment](../ERD.md), [API.md §payments](../API.md) |

---

## Context

결제 도메인은 처음부터 PG(토스·나이스페이 같은 결제대행사) 연동을 전제로 설계했다. 다만 실 PG 를 붙이려면 가맹 심사와 정산 계정이 필요해, 초기 단계에서 그만한 비용을 들이기는 어렵다.

그렇다고 결제를 대충 처리할 수는 없다. 결제는 이 도메인에서 가장 까다로운 부분이고, 적어도 다음 네 가지는 실제처럼 동작하고 검증돼야 한다.

- 비동기 콜백: 실 PG 는 승인 요청에 곧바로 최종 결과를 주지 않는다. 일단 `PENDING` 으로 응답하고, 잠시 뒤 웹훅으로 `PAID` 나 `FAILED` 가 도착한다.
- 멱등성: 같은 결제 요청(Idempotency-Key)이 중복으로 들어오거나, 같은 웹훅이 두 번 오거나, 환불을 재시도해도 부수효과는 한 번만 일어나야 한다. 이슈 #160 에서 드러난 멱등성 결함이 다시 생기면 안 된다.
- 실패 보상: `FAILED` 콜백이 오면 차감했던 재고를 되돌리고 적용한 쿠폰을 `USED→ISSUED` 로 복원해야 한다.
- 결정적 테스트: 성공률·지연·웹훅 타이밍을 통제할 수 있어야 CI 와 로컬에서 같은 결과가 재현된다.

핵심은 실 PG 의 비동기·멱등 라이프사이클을 신뢰성 있게 재현하면서, 나중에 실 PG 로 갈아끼울 수 있는 경계를 어디에 둘 것인가다.

---

## Decision

PG 를 `PaymentGateway` 인터페이스로 추상화하고(Strategy 패턴), 그 뒤에 비동기 콜백까지 재현하는 `MockPaymentGateway` 를 둔다. 실 PG SDK 나 외부 큐 같은 새 인프라는 들이지 않는다.

```java
// payment/gateway/PaymentGateway.java
public interface PaymentGateway {
    PaymentResponse request(PaymentRequest request);   // 거래 식별자 + PENDING 즉시 응답
    PaymentStatus  query(String pgTransactionId);       // PG 측 상태 폴링
    RefundResponse refund(RefundRequest request);       // 환불 (idempotencyKey 기반)
}
```

동작은 실 PG 의 흐름을 그대로 따라간다. `request()` 는 `mock-tx-{UUID}` 거래 식별자를 발급하고, 설정된 성공률(`success-rate`)로 최종 결과를 미리 정한다. 그리고 웹훅을 쏠 시각(`fireAt`)을 잡아 `MockWebhookSimulator` 에 예약한 뒤, 일단 `PENDING` 으로 응답한다.

```java
String pgTransactionId = "mock-tx-" + UUID.randomUUID();
PaymentStatus result = rollOutcome();                       // success-rate 기반
Instant fireAt = now.plus(randomDuration(webhookDelayMin, webhookDelayMax));
if (autoWebhook) {
    webhookSimulator.scheduleCallback(pgTransactionId, orderNumber, result, fireAt);
}
return new PaymentResponse(pgTransactionId, PaymentStatus.PENDING, PROVIDER);
```

예약된 웹훅은 전용 `TaskScheduler`(`paymentTaskScheduler`) 위에서 `fireAt` 에 일회성으로 실행돼 `WebhookDispatcher` 로 결제 결과를 통보한다. 실 PG 의 서버 간 콜백과 같은 모양이다. 환불은 `idempotencyKey → 첫 응답` 캐시(`refundCache.computeIfAbsent`)로 처리해서, 같은 키로 다시 부르면 처음 만든 응답을 그대로 돌려준다.

운영 환경과는 프로파일로 분리한다. Mock 은 `@Profile({"local","dev","test","docker"})` 에서만 올라오므로, 실 PG 어댑터가 필요해지면 `prod` 프로파일용 `@Component` 로 따로 추가하면 된다.

상태와 전이 규칙은 도메인이 보증한다. `PaymentStatus` 가 합법 전이만 허용하고(`PENDING→{PAID,FAILED}`, `PAID→{PARTIALLY_REFUNDED,REFUNDED}`, 그 외는 종착), `payment.pg_transaction_id` 에 건 **UNIQUE** 제약이 거래 단위 멱등성의 최종 방어선이 된다.

테스트에서는 한발 더 나아가 비결정성을 아예 없앤다. `application-test.yaml` 에서 `success-rate: 1.0`, `delay: 0ms`, `webhook-delay: 0ms` 로 고정하고, 멱등성 통합 테스트는 `auto-webhook=false` 로 자동 웹훅을 끈 다음 콜백을 직접 호출한다. 타이밍에 기대지 않으니 결과가 흔들리지 않는다.

---

## Considered Options

### Option A — 실 PG(토스/나이스페이) 직접 연동 ❌

테스트망 키로 실제 결제대행사를 붙이는 방안이다. 충실도는 최고여서 실 승인·망취소·부분취소까지 검증할 수 있다. 하지만 테스트망조차 가맹 심사와 정산 계정을 요구하는 외부 의존이고, 응답 지연이나 일시 장애가 CI 를 비결정적으로 만든다. 초기 단일 인스턴스 스코프에는 과한 투자다.

| 항목 | 내용 |
|---|---|
| 충실도 | 최고 (실 승인·망취소·부분취소) |
| 비용 | 가맹 심사·정산 계정 필요, 테스트망도 외부 의존 |
| 결정성 | 낮음 — 외부 지연·장애가 CI 를 흔든다 |

### Option B — 단순 동기 Mock (요청 즉시 PAID 반환) ⚠️

`request()` 가 곧장 `PAID` 를 돌려주고 웹훅은 없는 방식이다. 구현이 가장 가볍고 해피패스는 빠르게 통과한다. 그런데 이렇게 하면 결제 도메인에서 정작 어려운 부분(비동기 콜백, 중복 웹훅, 웹훅과 폴링의 경합, 실패 보상)을 하나도 검증하지 못한다.

### Option C — 인터페이스 + 콜백형 Mock ✅ (채택)

`PaymentGateway` 추상화 위에 `MockPaymentGateway`(PENDING→웹훅 콜백)와 `MockWebhookSimulator`(TaskScheduler 예약)를 얹는다. 비동기 라이프사이클은 물론 멱등성과 실패 보상까지 재현되고, 성공률·지연·웹훅 타이밍을 프로퍼티로 통제하므로 CI 에서 결정적이다. 실 PG 가 필요해지면 인터페이스 뒤에 어댑터만 추가하면 된다. 대신 망취소·부분취소 같은 실 PG 특유의 엣지케이스는 검증 범위 밖이다.

---

## 비교 요약

| 기준 | A 실 PG | B 동기 Mock | **C 콜백형 Mock** |
|---|---|---|---|
| 비동기 웹훅 재현 | ✓ | ✗ | **✓** |
| 멱등/실패 보상 검증 | ✓ | ✗ | **✓** |
| CI 결정성 | ✗ | ✓ | **✓** |
| 외부 의존/계정 | 필요 | 없음 | **없음** |
| 실 PG 교체 용이성 | — | 낮음 | **높음(어댑터만)** |

---

## Consequences

**긍정적**

실 PG 계정 없이도 결제 라이프사이클 전체(PENDING→웹훅→PAID/FAILED→환불)를 로컬과 CI 에서 결정적으로 검증할 수 있다. 멱등성 방어선이 거래 생성·콜백·환불 세 군데에 겹쳐 있어서(`pg_transaction_id` UNIQUE, 콜백 멱등키, 환불 캐시), 동시 웹훅·폴링 경합을 다루는 `PaymentCallbackConcurrencyTest` 같은 테스트가 여기서 출발한다. 무엇보다 `PaymentGateway` 경계 덕분에 실 PG 전환이 도메인·서비스 코드를 건드리지 않고 어댑터 교체로 끝난다.

**부정적 / 트레이드오프**

모킹인 이상 충실도에는 한계가 있다. 실 PG 의 망취소·부분취소·중복 정산이나 서명 스킴 차이는 Mock 으로 잡히지 않으므로, 실 PG 를 도입하는 시점에 그 어댑터를 대상으로 한 통합 테스트가 따로 필요하다. 그때 이 ADR 은 Superseded 로 넘어간다. 또 Mock 의 거래 상태는 프로세스 메모리(`ConcurrentHashMap`)에만 있어 재기동하면 사라지고 `MAX_TRACKED_TRANSACTIONS`(10k) 상한에서 정리되는데, 로컬·테스트 용도라 문제 삼지 않았다.

---

## References

- 코드: [`payment/gateway/PaymentGateway.java`](../../backend/src/main/java/com/groove/payment/gateway/PaymentGateway.java), [`MockPaymentGateway.java`](../../backend/src/main/java/com/groove/payment/gateway/mock/MockPaymentGateway.java), [`MockWebhookSimulator.java`](../../backend/src/main/java/com/groove/payment/gateway/mock/MockWebhookSimulator.java), [`PaymentStatus.java`](../../backend/src/main/java/com/groove/payment/domain/PaymentStatus.java)
- 결제 콜백 직렬화: 본 디렉토리 [concurrency-control.md](./concurrency-control.md)
- [Spring `TaskScheduler`](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
