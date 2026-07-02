# ADR: PG 실연동 — 토스페이먼츠 어댑터

| 항목 | 값 |
|---|---|
| 상태 | Accepted |
| 날짜 | 2026-06-30 |
| 연관 이슈 | #293(연동 셋업)·#294(어댑터 confirm/query/cancel)·#295(웹훅 수신)·#296(confirm 승인 흐름)·#299(결제위젯 프론트)·#321(실 PG 배포 게이트) |
| 작성자 | ParkGunWoo |
| 관련 문서 | [payment-gateway-mock.md](./payment-gateway-mock.md), [concurrency-control.md](./concurrency-control.md), [ARCHITECTURE.md](../ARCHITECTURE.md) §7, [API.md](../API.md), [ERD.md](../ERD.md) |

이 문서는 [payment-gateway-mock.md](./payment-gateway-mock.md)를 잇는 후속이다. Mock ADR이 "실 PG 를 도입하는 시점에 그 어댑터를 대상으로 한 통합 테스트가 따로 필요하다. 그때 이 ADR 은 Superseded 로 넘어간다"고 예고한 바로 그 지점이다. 단, Mock 은 완전히 폐기되지 않고 `local/test/docker` 의 결정적 대역으로 존속하므로 **dev/prod 경로에 한정한 부분 대체**다.

---

## Context

Mock(`MockPaymentGateway`)은 실 PG 의 비동기·멱등 라이프사이클을 재현하며 결제 도메인을 CI 에서 결정적으로 검증하는 역할을 해왔다. 하지만 Mock 은 태생적으로 못 잡는 게 있다 — 실 승인·망취소·부분취소, 위젯이 발급한 `paymentKey` 로 승인하는 **확인형(confirm) 동기 모델**, 위조 웹훅, 금액 위변조, 외부 PG 의 지연·장애다. 이것들은 실제 PG 를 붙여야만 검증된다.

토스페이먼츠 테스트망 키로 실 PG 를 도입하되, 다음을 지켜야 했다.

- **경계 보존**: Mock ADR 이 세운 `PaymentGateway` 추상화 뒤에서만 교체하고, 도메인·서비스는 건드리지 않는다.
- **CI 결정성 유지**: 외부 PG 의 지연·장애가 CI 를 흔들면 안 된다. 실 어댑터는 운영 계열 프로파일에만 올리고, 로컬·테스트는 Mock 을 그대로 둔다.
- **위조·위변조 방어**: successUrl/failUrl 콜백과 웹훅은 클라이언트를 거쳐 오거나 외부에서 직접 들어오므로, 본문을 그대로 신뢰하면 교차 주문 조작·금액 위변조·위조 웹훅에 노출된다.
- **외부 장애 격리**: 토스 5xx·연결 실패가 워커 스레드를 오래 점유하거나 폴링 스케줄러의 종결 판단을 오염시키면 안 된다.

---

## Decision

`PaymentGateway` 인터페이스 뒤에 `TossPaymentGateway` 어댑터를 추가한다. 프로파일로 택1 한다 — `dev/prod` 는 실 토스, `local/test/docker` 는 Mock. 인터페이스가 `request()`(비동기)와 `confirm()`(동기)를 모두 수용하므로 두 승인 모델이 한 포트에 공존한다.

```java
// gateway/toss/TossPaymentGateway.java
@Component
@Profile({"dev", "prod"})
public class TossPaymentGateway implements PaymentGateway {
    public ConfirmResponse confirm(String paymentKey, String orderId, long amount) { ... }
    public GatewayQuery   query(String pgTransactionId) { ... }
    public RefundResponse refund(RefundRequest request) { ... }
    // request() 는 토스에 대응 API 가 없어 UnsupportedOperationException
}
```

**HTTP 어댑터.** `tossRestClient`(RestClient)로 코어 API 를 호출한다. 인증은 토스 규약대로 시크릿 키 뒤에 콜론을 붙여(빈 패스워드) base64 인코딩한 `Authorization: Basic` 헤더를 인터셉터로 주입한다. connect/read 타임아웃은 프로퍼티로 외부화한다(`TossPaymentConfig`).

**confirm(동기 승인) 흐름.** 토스는 결제위젯이 발급한 `paymentKey` 로 서버가 즉시 승인하는 확인형 모델이다.

1. `POST /api/v1/payments/toss/checkout` — 주문 검증 후 PENDING 결제를 저장하고, 무작위 **콜백 토큰**(UUID)을 함께 저장한다. 응답의 successUrl 에 토큰을 실어 위젯에 넘긴다.
2. 위젯 결제 성공 → 브라우저가 successUrl(`GET /payments/toss/success?paymentKey&orderId&amount&token`)로 리다이렉트.
3. 서버는 **콜백 토큰을 저장값과 상수시간 비교**(`verifyCallbackToken`)해 교차 주문 조작을 차단하고, **저장 금액과 요청 amount 를 대조**(`PaymentAmountMismatchException`)해 위변조를 막은 뒤, `paymentGateway.confirm(paymentKey, orderId, amount)` 로 실제 승인한다.
4. `confirm` 은 `payment-callback:{paymentKey}` **멱등 래퍼**로 감싸 새로고침·중복 콜백에도 1회만 전이한다. PAID 면 `applyConfirmedPaid`(order PAID + `OrderPaidEvent`), 가상계좌 등 비-PAID 는 `linkPendingPaymentKey` 로 실제 paymentKey 만 연결하고 후속 웹훅/폴링을 기다린다.
5. 모든 예외는 302 리다이렉트로 흡수한다(`?payment=success|fail`). failUrl 은 **상태를 바꾸지 않는다** — 보상은 폴링 리퍼의 만료 경로가 수행한다.

**웹훅(위조 방어).** `POST /api/v1/payments/toss/webhook` 은 본문을 신뢰하지 않는다. `PAYMENT_STATUS_CHANGED` 만 처리하고, 로컬에 해당 결제가 있으면 **본문 status 를 무시한 채 `query(paymentKey)` 로 토스에 권위 재조회**해 그 값만 반영한다. 위조 본문은 재조회에서 무력화되고, confirm·폴링·다른 웹훅과 `payment-callback:{paymentKey}` 멱등 키를 공유해 동시 콜백에도 1회만 전이한다.

**외부 장애 격리.** 토스 호출을 서킷브레이커(바깥)·재시도(안쪽)로 감싼다(`callGuarded`). 재시도는 **5xx·연결 실패 같은 일시 장애만** 대상이고(`isRetryableTransient`), 읽기 타임아웃·4xx 는 재시도하지 않는다(워커 점유 증폭 방지). `query()` 의 서킷 OPEN(`CallNotPermittedException`)은 502 로 정규화하지 않고 그대로 전파해, 폴링 스케줄러가 이를 '영구 해소 불가'로 오인해 만료 PENDING 을 FAILED 로 종결하지 않게 한다.

**멱등성 3중 방어.** ① `Idempotency-Key` 헤더(confirm=paymentKey, refund=`RefundRequest#idempotencyKey`)로 토스 측 dedup, ② DB `uk_payment_order`(주문당 1건)·`uk_payment_pg_tx`(거래당 1건), ③ 앱의 `IdempotencyService` 멱등 래퍼 + `SELECT … FOR UPDATE` 콜백 직렬화.

**배포 게이트.** 실 PG 는 시크릿을 요구한다 — `PAYMENT_TOSS_CLIENT_KEY`/`SECRET_KEY`/`SUCCESS_URL`/`FAIL_URL` 을 env 로 주입하고, prod 프로파일은 플레이스홀더 시크릿으로는 기동하지 못하게 가드한다(`common/config/SecretPlaceholderGuard`·`DbSecretGuard`).

**스키마.** `V28__payment_pg_tx_unique.sql`(멱등 단건 조회를 위해 `idx_payment_pg_tx` → `uk_payment_pg_tx` 승격), `V31__payment_callback_token.sql`(`callback_token` 컬럼 — 교차 주문 조작 차단).

---

## Considered Options

### 승인 모델

- **위젯 confirm(동기) ✅ 채택** — 위젯이 카드사 인증·`paymentKey` 발급까지 처리하고, 서버는 `paymentKey` 로 즉시 승인해 확정 상태를 응답으로 받는다. 카드 정보가 서버를 거치지 않아 PCI 부담이 낮고 승인 결과가 동기라 흐름이 단순하다.
- **결제창 리다이렉트/직접 승인 ⚠️** — 서버가 승인 파라미터를 더 많이 다루지만, 위젯 대비 프론트·서버 양쪽 결합이 커지고 이점이 적다.

### 웹훅 신뢰 모델

- **재조회 권위값 ✅ 채택** — 웹훅 본문을 무시하고 `query(paymentKey)` 재조회 결과만 반영. 위조 본문이 무력화되고, 서명 스킴 변화에 덜 취약하다. 대가는 웹훅당 조회 1회.
- **본문 서명 검증 ⚠️** — 조회 왕복은 없지만, 서명 키 관리·스킴 변경 대응 부담이 있고 본문 값 자체를 신뢰해야 한다. 재조회가 더 방어적이라 우선했다.

### 프로파일 분리

- **dev/prod=real · local/test/docker=Mock ✅ 채택** — 실 어댑터를 운영 계열에만 올려 CI 결정성을 보존한다.
- **전 환경 real ❌** — 외부 지연·장애가 CI 를 비결정적으로 만든다. Mock ADR 이 이미 기각한 방향.

---

## 비교 요약

| 기준 | Mock 유지 | **토스 실연동(채택)** |
|---|---|---|
| 실 승인·망취소·부분취소 | ✗ | **✓** |
| 위조 웹훅·금액 위변조 방어 검증 | ✗(재현 불가) | **✓** |
| CI 결정성 | ✓ | **✓ (local/test 는 Mock 유지)** |
| 외부 의존/계정 | 없음 | 테스트망 키 필요 |
| 도메인·서비스 변경 | — | **없음(어댑터 교체)** |

---

## Consequences

**긍정적**

실 승인·망취소·부분취소가 실제로 검증되고, 위조 웹훅(재조회 무력화)·금액 위변조(저장값 대조)·교차 주문 조작(콜백 토큰)이 방어된다. 서킷브레이커·타임아웃으로 외부 장애가 워커·폴링 종결 판단으로 번지지 않는다. 무엇보다 Mock ADR 이 세운 `PaymentGateway` 경계 덕분에 이 전환이 **도메인·서비스 코드를 건드리지 않고 어댑터 추가로 끝났다** — 경계 설계가 값을 한 사례다.

**부정적 / 트레이드오프**

토스 테스트망 키라는 외부 의존이 생겼고, dev/prod 는 시크릿 주입·배포 게이트가 전제된다. CI 는 여전히 Mock(단위 어댑터 테스트는 `MockRestServiceServer` 스텁)으로 돌아 실 토스망을 직접 치지 않으므로, 실 API 계약 변화는 테스트망 수동 점검으로 확인해야 한다. 가상계좌 등 비-PAID 결제는 confirm 시점에 확정되지 않아 후속 웹훅/폴링에 의존한다. 웹훅 재조회는 웹훅당 조회 왕복 비용을 더한다.

---

## References

- 코드: [`gateway/PaymentGateway.java`](../../backend/src/main/java/com/groove/payment/gateway/PaymentGateway.java), [`gateway/toss/TossPaymentGateway.java`](../../backend/src/main/java/com/groove/payment/gateway/toss/TossPaymentGateway.java), [`gateway/toss/TossPaymentConfig.java`](../../backend/src/main/java/com/groove/payment/gateway/toss/TossPaymentConfig.java), [`gateway/TossPaymentProperties.java`](../../backend/src/main/java/com/groove/payment/gateway/TossPaymentProperties.java)
- 승인/콜백: [`application/TossPaymentService.java`](../../backend/src/main/java/com/groove/payment/application/TossPaymentService.java), [`api/TossPaymentController.java`](../../backend/src/main/java/com/groove/payment/api/TossPaymentController.java), [`application/PaymentCallbackService.java`](../../backend/src/main/java/com/groove/payment/application/PaymentCallbackService.java)
- 웹훅: [`api/TossWebhookController.java`](../../backend/src/main/java/com/groove/payment/api/TossWebhookController.java), [`application/TossWebhookService.java`](../../backend/src/main/java/com/groove/payment/application/TossWebhookService.java)
- 마이그레이션: `V10__init_payment.sql`, `V28__payment_pg_tx_unique.sql`, `V31__payment_callback_token.sql`
- 테스트: `TossPaymentConfirmIntegrationTest`, `TossWebhookIntegrationTest`, `gateway/toss/TossPaymentGatewayTest`(`MockRestServiceServer` 스텁)
- 관련 결정: [payment-gateway-mock.md](./payment-gateway-mock.md), [concurrency-control.md](./concurrency-control.md)
- 외부: [토스페이먼츠 결제위젯/confirm](https://docs.tosspayments.com/)
