# 결제 멱등성 — 중복 결제(이중 청구)를 통합 테스트로 박제

> 이슈 [#207](https://github.com/heygw44/groove/issues/207) · 마일스톤 M11(W11 CS 개선 2차) · 도메인 payment
> Before 베이스라인: [#196](https://github.com/heygw44/groove/issues/196) · [`docs/measurement/baseline.md`](../measurement/baseline.md) §1 (`payment_duplicated=0`)
> 멱등성 인프라 도입: #W7-2(`IdempotencyService` + `V9__idempotency_record.sql`) · #160(TTL 만료 캐시 회수)
> 자매 사례(동시성): [`docs/improvements/concurrency.md`](./concurrency.md)(#205)

## 1. 문제 정의

결제 요청 `POST /api/v1/payments` 는 네트워크 재시도·더블클릭·웹훅 중복 통보에 노출된다. 같은 의도의 요청이
두 번 처리되면 **이중 청구(중복 결제)** 가 발생한다 — 정합성 P0 결함이다. #196 베이스라인은 k6 payment
시나리오(30VU·60s)에서 이 불변식을 측정해 박제했다:

| 시나리오 | TPS | p95 | error rate | 핵심 |
|---|---|---|---|---|
| **payment** (30VU 60s, Mock지연0) | 405 | 101 ms | 0% | **`payment_duplicated=0`** (멱등 정상) |

즉 부하 상황에서도 중복 결제가 0건이었다. 본 작업(#207)은 이 **`payment_duplicated=0`** 을
**통합 테스트로 회귀 고정**해, 멱등 계약이 리팩터링·기능 추가로 깨지면 빌드가 빨개지도록 한다.

## 2. 멱등성 메커니즘 — 3중 방어

결제 경로는 세 층으로 단일 처리를 보장한다. 핵심 로직은 `IdempotencyService.execute(key, fingerprint,
resultType, action)` 에 모여 있다.

### (1) HTTP 헤더 검증 — `@Idempotent` 인터셉터

`PaymentController.request` 는 `@Idempotent` 다. `IdempotencyKeyInterceptor` 가 `Idempotency-Key` 헤더를
검증(없으면 400 `IDEMPOTENCY_KEY_REQUIRED`)하고 요청 속성으로 넘긴다. 컨트롤러는 **비트랜잭션** 이어야 하며
(`PaymentService.requestPayment` 가 자기 트랜잭션을 커밋한 뒤 마커가 COMPLETED 로 갱신되도록), 처리 본체를
`IdempotencyService.execute` 로 감싼다:

```java
// PaymentController.request
String fingerprint = body.orderNumber() + "|" + body.method();
PaymentApiResponse response = idempotencyService.execute(
        idempotencyKey, fingerprint, PaymentApiResponse.class,
        () -> paymentService.requestPayment(callerMemberId, body));
```

### (2) DB UNIQUE 마커 — `idempotency_record` (V9)

키마다 `IN_PROGRESS` 마커 행을 독립 트랜잭션(`REQUIRES_NEW`)으로 INSERT 한다. `idempotency_key` 의
**UNIQUE 제약(`uk_idempotency_key`)** 이 단일 처리의 1차 방어선 — 두 호출자가 거의 동시에 같은 키로 INSERT 하면
한 쪽만 성공한다.

```sql
-- V9__idempotency_record.sql (요약)
CREATE TABLE idempotency_record (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    idempotency_key     VARCHAR(255) NOT NULL,   -- 클라이언트 키
    status              VARCHAR(20)  NOT NULL,    -- IN_PROGRESS | COMPLETED
    request_fingerprint VARCHAR(255) NULL,        -- 요청 지문(재사용 검증)
    response_body       TEXT         NULL,        -- 캐시된 결과 JSON
    expires_at          DATETIME(6)  NOT NULL,    -- TTL 만료 시각
    ...,
    CONSTRAINT uk_idempotency_key UNIQUE (idempotency_key),
    INDEX idx_idempotency_expires (expires_at)
);
```

### (3) 서비스 실행 제어 — replay · fingerprint · TTL

INSERT 결과로 분기한다:

- **INSERT 성공(소유권 획득)** → `action` 1회 실행 → 결과를 JSON 직렬화해 마커를 `COMPLETED` 로 갱신. `action`
  이 예외를 던지면 마커를 회수(재시도 허용)하고 예외를 재던진다.
- **INSERT 실패(키 충돌)** → 마커 재조회:
  - `COMPLETED` & TTL 유효 → (지문 불일치면 409 `IDEMPOTENCY_KEY_REUSE_MISMATCH`) 캐시된 결과를 **replay**.
  - `COMPLETED` & TTL 경과 → 만료 캐시를 회수하고 **처음부터 새로 처리**("TTL 후엔 새 처리", #160).
  - `IN_PROGRESS` → 409 `IDEMPOTENCY_IN_PROGRESS`.

`request_fingerprint = orderNumber|method` 라, **같은 키 + 다른 주문**은 409 로 막아 키 오용을 잡고, **같은 키 +
같은 주문** 재요청은 최초 응답(202 + 동일 `paymentId`)을 그대로 replay 한다.

## 3. 웹훅 멱등성 — 한 키로 3경로 통합

PG 결과 통보는 세 경로로 들어온다: HTTP 웹훅(`POST /api/v1/payments/webhook`), 인프로세스 디스패처
(`PaymentWebhookHandler`), 폴링 동기화(`PaymentReconciliationScheduler`). 세 경로가 **동일 멱등 키**를 써
어느 조합으로 중복 수신해도 상태 전이는 1회다:

```java
// PaymentCallbackService — pgTransactionId 단위 멱등 키
public static String idempotencyKeyFor(String pgTransactionId) {
    return "payment-callback:" + pgTransactionId;
}
```

이중 안전선으로 `applyResult` 진입 시 **결제 상태를 재확인**(이미 PENDING 이 아니면 `ALREADY_PROCESSED` 로
무시)한다. FAILED 보상(재고 복원·쿠폰 `USED→ISSUED` 복원)도 이 경계 안에서 정확히 1회 적용된다.

## 4. 통합 테스트로 고정한 시나리오

| 시나리오 | 테스트 | 핵심 단언 |
|---|---|---|
| **동시 동일 키 결제** 16건 | `PaymentIdempotencyConcurrencyIntegrationTest.concurrentSameIdempotencyKey_createsSinglePayment` | Payment **정확히 1건**, 응답 202(생성/replay)·409(IN_PROGRESS)뿐, 5xx 0 |
| **동시 중복 FAILED 웹훅** 16건 | `…concurrentDuplicateFailedWebhook_transitionsOnce` | 결제 FAILED·**재고 복원 1회**·주문 PAYMENT_FAILED, 마커 단일 COMPLETED |
| **동시 중복 PAID 웹훅** 16건 | `…concurrentDuplicatePaidWebhook_transitionsOnce` | 결제 PAID 1회·주문 PREPARING(배송 락스텝) |
| **멱등 키 만료 후 재사용** | `PaymentApiIntegrationTest.request_keyReuseAfterExpiry_reprocessesNewPayment` | TTL 만료가 키 잠금을 해제 → 다른 주문으로 **새 결제 생성**(Payment 2건) |
| (순차) 동일 키 replay | `PaymentApiIntegrationTest.request_sameIdempotencyKey_replaysFirstResponse` | 동일 `paymentId` replay, Payment 1건 |
| (서비스 레벨) 동시·만료 | `IdempotencyServiceTest.*` | `action` 정확히 1회, 만료 캐시 재처리 |

**설계 결정 — 실 HTTP 동시성**: MockMvc 는 running server 가 없어 멀티스레드 `perform()` 에 thread-safe 하지
않다(Spring Framework reference). 동시 시나리오는 baseline(k6 HTTP)과 측정 층을 맞춰
`@SpringBootTest(RANDOM_PORT)` + JDK `HttpClient` 로 임베디드 서버에 진짜 동시 HTTP 를 발사하고, 동시 출발/완료
집계는 공용 `ConcurrencyHarness`(#205)를 재사용한다. (이 앱은 클라이언트측 `spring-boot-restclient` 를 두지
않아 `TestRestTemplate` 가 없으므로, thread-safe 한 JDK `HttpClient` 단일 인스턴스를 공유한다.) 비동시
시나리오(만료 재사용·헤더 누락 등)는 기존 MockMvc 경로를 유지한다.

## 5. 결과

전 시나리오가 통과해 `payment_duplicated=0` 불변식이 회귀 테스트로 박제됐다. 멱등 계약(동시 동일 키 → 단일
결제, 중복 웹훅 → 전이 1회, TTL 만료 → 새 처리)을 깨는 변경은 빌드를 실패시킨다.

**알려진 한계** — `action` 커밋 직후 완료 갱신이 실패하면 키는 `ttl + in-progress-grace` 동안 `IN_PROGRESS`(409)로
남고 `IdempotencyRecordCleanupTask` 가 그 뒤 회수한다(처리 중 마커를 TTL 경과만으로 지워 `action` 이중 실행되는 것을
막는 의도된 유예). 부수효과는 이미 반영됐으므로 이 키로 재시도하면 안 되고 새 키를 써야 한다.
