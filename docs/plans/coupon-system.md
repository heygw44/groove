# 계획: 쿠폰 시스템 (Coupon System)

| 항목 | 값 |
|---|---|
| 상태 | **계획** (미구현) — 확장 도메인 (W7 완료 후) |
| 작성일 | 2026-05-26 |
| 라벨 | `type:feature`, `domain:coupon`, effort `M`~`L` |
| 목적 | 이커머스 핵심 기능인 쿠폰을 추가하되, **선착순 한정수량 발급의 대용량 동시성 처리**를 단계적으로 시연해 프로젝트의 차별화(측정·개선) 공백을 메운다 |
| 관련 문서 | [ERD.md](../ERD.md) §4.15/§4.16, [API.md](../API.md) §3.9, [glossary.md](../glossary.md) §2.12, [decisions/coupon-concurrency.md](../decisions/coupon-concurrency.md), [troubleshooting/coupon-issuance-concurrency.md](../troubleshooting/coupon-issuance-concurrency.md) |

> **왜 이 문서가 있는가**: 기능(W1~W7)은 완성됐지만 [project 로드맵](../MILESTONE.md) 기준 **W8~W12(시드/측정/개선/문서화)가 비어 있고**, PRD §11 DoD 중 **#4(k6 부하테스트)·#5(Before/After 개선 사례)** 가 미충족이다 — 평가자 관점 "잘 만든 CRUD에서 멈춤". 선착순 쿠폰 발급은 [재고 오버셀 baseline](../troubleshooting/overselling-baseline.md)의 "After 없음" 공백과 DoD #4·#5 를 **정확히** 메우는 동시성 시연 소재다. 본 문서는 그 설계·구현 계획서다.

---

## 1. 현재 상태 (사실 확인 완료 — 2026-05-26)

| 항목 | 사실 |
|---|---|
| 인프라 | Spring Boot 4.0.6 / Java 21 / MySQL 8 + Flyway + JPA. **Redis·메시지 브로커 없음** (rate limit=Bucket4j in-memory, cache=Caffeine) |
| 비관적 락 선례 | `PaymentRepository` 가 환불에 `@Lock(PESSIMISTIC_WRITE)` 사용 (`src/main/java/com/groove/payment/domain/PaymentRepository.java`) — 동일 패턴 재사용 가능 |
| 낙관적 락 | 미사용 (`ArtistService` 가 last-write-wins 명시) |
| 멱등성 | `common/idempotency` — `@Idempotent` + `IdempotencyService.execute(action)` 존재. 발급 재시도 방어에 재사용 |
| 스케줄링 | `common/scheduling` `@EnableScheduling` + `IdempotencyRecordCleanupTask` 패턴 — 쿠폰 만료 배치에 재사용 |
| 도메인 이벤트 | `OrderPaidEvent` + `@TransactionalEventListener(AFTER_COMMIT)` 선례 존재 |
| 가격 경로 | `Order.totalAmount`(BIGINT, 원)= `Σ OrderItem.subtotal`. **할인 필드 없음**. 결제는 `order.getTotalAmount()` 를 그대로 청구 (`PaymentService.requestPayment`) |
| 재고 차감 | `OrderService.place` 가 **락 없이** 차감 — 의도적 오버셀 노출(W10 비관적 락 예정). 쿠폰 동시성은 이 서사의 연장선 |
| 커버리지 게이트 | `build.gradle.kts` 가 `auth/member/catalog` 패키지 라인 80% 강제. 쿠폰 패키지도 게이트에 편입 필요 |

## 2. 요구사항

### 기능적
- 쿠폰 정책: **정액(FIXED_AMOUNT)** + **정률(PERCENTAGE, 상한 캡)**, 최소 주문금액 조건, 유효기간, 정책 상태(ACTIVE/SUSPENDED/ENDED).
- 발급: **선착순 한정수량**(회원이 직접 발급 요청) + **관리자/이벤트 직접지급**. 회원당 1장.
- 적용: 주문 생성 시 1장 적용 → `discount_amount` 계산 → 결제는 `payable = total − discount` 청구.
- 생명주기: 발급(ISSUED) → 사용(USED) → 만료(EXPIRED). 주문 취소/환불 시 USED→ISSUED 복원.
- 관리자: 쿠폰 CRUD + 상태 변경 + 직접지급.

### 비기능적 (헤드라인)
- 선착순 발급의 **대용량 동시성** — 초과발급(over-issuance) 없이 정확히 `total_quantity` 만 발급. 처리량을 측정하고 개선 단계를 Before/After 로 박제.

### 확정된 설계 결정 (사용자 승인 2026-05-26)
1. **동시성 전략**: DB 단계적 — 베이스라인(레이스) → 비관적 락 → 원자적 조건부 UPDATE. Redis 는 ADR 미래안.
2. **할인 정책**: 정액 + 정률 (대상 한정·중복 적용은 v2).
3. **발급 방식**: 선착순 + 직접지급 (동일 `member_coupon` 모델 공유).
4. **전액 할인(payable=0)**: v1 범위 외 (할인 ≤ 주문총액 CHECK, 0원 자동결제는 후속).

## 3. 도메인 모델

신규 패키지 `com.groove.coupon` — 기존 도메인과 동일한 4계층(`api/dto · application · domain · exception · event`).

### 3.1 엔티티 (스키마는 [ERD.md §4.15/§4.16](../ERD.md) 정본)

- **`Coupon`** (`coupon`): 할인 규칙 + 발급 제약 정책. 핫 카운터 `issued_count`.
- **`MemberCoupon`** (`member_coupon`): 회원별 발급 인스턴스. `UNIQUE(coupon_id, member_id)` 로 회원당 1장. 사용 주문은 `order_id` 역참조(순환 FK 회피).

### 3.2 할인 계산 (도메인 메서드)

`Coupon.calculateDiscount(long subtotal)` 에 `CouponDiscountType` enum 의 행위로 위임 (`OrderStatus.canTransitionTo` 와 동일 스타일):

```
FIXED_AMOUNT : discount = min(discountValue, subtotal)
PERCENTAGE   : raw = subtotal * discountValue / 100
               discount = (maxDiscountAmount != null) ? min(raw, maxDiscountAmount) : raw
공통 가드     : subtotal < minOrderAmount        → COUPON_MIN_ORDER_NOT_MET (422)
              : 결과 discount ≤ subtotal 보장 (payable ≥ 0)
```

### 3.3 상태 머신

- `CouponStatus`: ACTIVE → {SUSPENDED, ENDED}, SUSPENDED → {ACTIVE, ENDED}.
- `MemberCouponStatus`: ISSUED → {USED, EXPIRED, CANCELLED}, USED → ISSUED(취소/환불 복원).

## 4. 동시성 전략 — 선착순 발급

상세·근거는 [decisions/coupon-concurrency.md](../decisions/coupon-concurrency.md). 요약:

| 단계 | 방식 | 결과 | 비고 |
|---|---|---|---|
| 0 베이스라인 | read→check→insert→increment (락 없음) | **초과발급** 재현 | "Before" 박제 (재고 오버셀의 쿠폰판) |
| 1 비관적 락 | `@Lock(PESSIMISTIC_WRITE)` 로 coupon 행 잠금 | 정확하나 직렬화로 처리량 ↓ | `PaymentRepository` 패턴 재사용 |
| 2 원자적 UPDATE (**최종**) | `UPDATE coupon SET issued_count=issued_count+1 WHERE id=? AND (total_quantity IS NULL OR issued_count<total_quantity)` | 정확 + 높은 처리량 (affected=1 성공, 0 소진) | 행 락 짧음 |

- 회원당 1장은 `UNIQUE(coupon_id, member_id)` 로 DB 보증 — 더블클릭/동시요청도 한 장.
- 발급 엔드포인트는 `@Idempotent` 로 재시도 멱등 처리.
- 한계(핫 로우): 단일 `issued_count` 행 경합은 원자적 UPDATE 로도 남는다 → 극한 트래픽 시 Redis `RAtomicLong` + 비동기 영속화가 미래안(ADR).

## 5. 주문/결제 통합 (기존 코드 영향 = Blast Radius)

| 파일 | 변경 |
|---|---|
| `order/domain/Order.java` | `discountAmount`(long, default 0) 필드 + `getPayableAmount()` = `totalAmount − discountAmount` 파생. (coupon FK 미보유 — 순환 FK 회피) |
| `order/api/dto/OrderCreateRequest.java` | optional `memberCouponId` 추가 |
| `order/application/OrderService.place()` | totalAmount 산정 후 쿠폰 검증(소유·ISSUED·미만료·min_order)→할인 적용→`MemberCoupon` USED + `order_id` 연결. **게스트 주문 + memberCouponId → 거부** |
| `order/application/OrderService.cancel()` | 재고 복원과 대칭으로 **쿠폰 복원**(USED→ISSUED) |
| `payment/application/PaymentService.java` | `order.getTotalAmount()` → `order.getPayableAmount()` (저장 금액 + PG 요청 2곳). `payable<=0` 가드 의미 재확인(전액할인 v1 미지원) |
| `admin/application/AdminOrderService.java` | 환불 시 **쿠폰 복원** (cancel 과 동일 정책 — 두 경로 모두 손대야 함) |
| `common/exception/ErrorCode.java` | `COUPON_*` 코드 9종 추가 (시맨틱 명명) |
| `build.gradle.kts` | JaCoCo 80% 게이트 includes 에 `com.groove.coupon.*` 추가 |
| `db/migration/V14__init_coupon.sql`, `V15__order_coupon_columns.sql` | 신규 (버전 번호는 도입 시점 미적용 최대값 다음으로 재배정) |

신규 ErrorCode: `COUPON_NOT_FOUND`(404), `COUPON_SOLD_OUT`(409), `COUPON_ALREADY_ISSUED`(409), `COUPON_NOT_ISSUABLE`(422), `COUPON_EXPIRED`(422), `COUPON_ALREADY_USED`(409), `COUPON_NOT_OWNED`(403/404), `COUPON_MIN_ORDER_NOT_MET`(422), `COUPON_NOT_APPLICABLE`(422).

## 6. API 표면

정본은 [API.md §3.9 (쿠폰)·§3.10 (관리자 쿠폰)](../API.md). 요약:
- `GET /coupons` (Public) · `POST /coupons/{id}/issue` (USER, Idempotency-Key) · `GET /me/coupons` (USER)
- `POST·GET /admin/coupons` · `PATCH /admin/coupons/{id}/status` · `POST /admin/coupons/{id}/grant` (ADMIN)

## 7. 설계 결정 & Why

| 결정 | 선택 | 근거 / 트레이드오프 |
|---|---|---|
| 정책 vs 인스턴스 분리 | `coupon` + `member_coupon` 2테이블 | 정책 1건이 N명에게 발급되는 표준 모델. 발급 카운터와 보유 상태를 분리 |
| 사용 주문 추적 | `member_coupon.order_id` 단방향 | `orders.applied_member_coupon_id` 를 두면 순환 FK → 삽입 순서 의존성. 할인액은 `orders.discount_amount` 로 충분 |
| 발급 동시성 | 원자적 조건부 UPDATE | Redis 없이 정확 + 높은 처리량. 비관적 락 대비 행 락 짧음. 단계적 시연으로 학습 가치 |
| 회원당 1장 | `UNIQUE(coupon_id, member_id)` | per_member_limit>1 은 카운트 기반이라 racy → v1 은 1장 고정, UNIQUE 로 DB 강제 |
| 할인 저장 | `discount_amount` 만 저장, payable 파생 | 중복 저장 방지(불변식). `payment.amount` 가 결제 시점 스냅샷 역할 |
| 전액 할인 | v1 미지원 (payable≥1) | 결제 `amount>0` 계약 유지 — 0원 자동결제는 결제 도메인 침습이 커 후속으로 분리 |
| 만료 처리 | 스케줄러 배치 | `IdempotencyRecordCleanupTask` 패턴 재사용. 실시간 만료는 조회 시점 검증으로 보완 |

## 8. 단계별 구현 계획 (각 Phase = 1 이슈 = 1 PR)

> 브랜치 `feat/{issue-number}`, 커밋·PR 한글, `Closes #N` ([MILESTONE.md §0.3](../MILESTONE.md)).

| Phase | 범위 | 라벨 | 선행 |
|---|---|---|---|
| **P0 설계·마이그레이션** | V14/V15 + ERD/API/glossary 확정 (본 문서들) | `type:docs` `M` | — |
| **P1 쿠폰 도메인** | `Coupon`·`MemberCoupon`·enum·repository + 할인계산/상태머신 TDD | `type:feature` `M` | P0 |
| **P2 선착순 발급 (헤드라인)** | `CouponIssueService` 3단계 + `POST /coupons/{id}/issue` + 동시성 테스트(초과발급 재현→원자적 UPDATE 검증) | `type:feature` `L` | P1 |
| **P3 주문 통합** | Order `discount_amount`·`memberCouponId`·payable + PaymentService 청구액 변경 + 취소/환불 복원 + 통합테스트 | `type:feature` `L` | P2 |
| **P4 관리자·직접지급·만료** | admin CRUD/grant + 만료 스케줄러 | `type:feature` `M` | P3 |
| **P5 측정·문서** | k6 발급 부하 스크립트(**DoD #4**) + Before/After 사례(**DoD #5**) + 커버리지 게이트 확장 + README | `type:improvement` `M` | P4 |

## 9. 리스크

| 리스크 | 등급 | 대응 |
|---|---|---|
| 전액 할인 시 payable=0 → PG 0원 거부 | HIGH | v1 미지원(할인≤총액 CHECK), 0원 자동결제 후속. 결정 완료 |
| 쿠폰 복원 2경로(취소+환불) 중 누락 | HIGH | OrderService.cancel + AdminOrderService 환불 양쪽 테스트로 강제 |
| 핫 로우 병목 | MED | 원자적 UPDATE 로 v1 충분, Redis 전환은 ADR 미래안 |
| 커버리지 게이트 80% 미달 → CI 빨강 | MED | Phase별 TDD, 게이트 편입은 P5 마지막 |
| 멱등성 트랜잭션 경계 오용 | LOW | 컨트롤러 비트랜잭션 규약(`PaymentService` 주석) 준수 |

## 10. 테스트 전략

- **단위**: 할인 계산(`@ParameterizedTest` 정액/정률/캡/최소금액 경계), 쿠폰·회원쿠폰 상태 머신.
- **동시성**: Testcontainers MySQL, N스레드 `CountDownLatch` — 베이스라인 초과발급 재현(`@Disabled` 보존) + 원자적 UPDATE 로 `issued_count == total_quantity` & 발급수 == 한정수량 검증. ([overselling-baseline](../troubleshooting/overselling-baseline.md) 포맷 재사용)
- **통합(MockMvc)**: 발급→내쿠폰 조회→쿠폰주문(payable 감소)→결제 청구액→취소/환불 복원 E2E.
- **커버리지**: `com.groove.coupon.*` 라인 80% (P5 게이트 편입).

---

## 부록 A. 마이그레이션 초안 (V14/V15)

> **상태**: 초안(SQL). **실행 가능한 Flyway 파일은 엔티티와 함께 P1(#89)에서 도입**한다 — 기존 프로젝트 컨벤션(모든 도메인이 migration+entity+test 를 한 이슈에서 함께 적용)과 CI(이제 PR마다 `./gradlew build`로 Flyway 실행)를 고려해, 본 docs 단계(#88)에서는 SQL을 문서 초안으로만 둔다. 버전 번호는 도입 시점 미적용 최대값 다음으로 재배정(W10 검색 인덱스가 먼저 들어오면 V16/V17 등). 정본 스키마는 [ERD §4.15/§4.16](../ERD.md).

### V14 — `coupon`, `member_coupon`

```sql
-- V14: 쿠폰 — coupon, member_coupon (ERD §4.15, §4.16).
--
-- 확장(M13) 범위. §8 v2 후보였던 coupon/coupon_issue 를 정식 도메인으로 승격.
-- 선착순 한정수량 발급의 동시성 제어는 APP(원자적 조건부 UPDATE) — decisions/coupon-concurrency.md.
--
-- 비즈니스 룰 위치:
--   - discount_value > 0 / 정률 1~100      : DB CHECK + 도메인
--   - issued_count <= total_quantity(한정) : DB CHECK + APP 원자적 UPDATE(1차 방어)
--   - 회원당 동일 쿠폰 1장                  : DB UNIQUE(coupon_id, member_id)
--   - status 전이                          : APP (CouponStatus / MemberCouponStatus canTransitionTo)
--   - expires_at = 발급 시 valid_until 스냅샷 : APP
--   - FK: coupon ON DELETE RESTRICT / member ON DELETE CASCADE / orders ON DELETE SET NULL
CREATE TABLE coupon (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    name                VARCHAR(100) NOT NULL,
    discount_type       VARCHAR(30)  NOT NULL,
    discount_value      BIGINT       NOT NULL,
    max_discount_amount BIGINT       NULL,
    min_order_amount    BIGINT       NOT NULL DEFAULT 0,
    total_quantity      INT          NULL,
    issued_count        INT          NOT NULL DEFAULT 0,
    per_member_limit    INT          NOT NULL DEFAULT 1,
    valid_from          DATETIME(6)  NOT NULL,
    valid_until         DATETIME(6)  NOT NULL,
    status              VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_coupon_discount_value_positive CHECK (discount_value > 0),
    CONSTRAINT ck_coupon_percentage_range        CHECK (discount_type <> 'PERCENTAGE' OR discount_value BETWEEN 1 AND 100),
    CONSTRAINT ck_coupon_min_order_non_negative  CHECK (min_order_amount >= 0),
    CONSTRAINT ck_coupon_issued_non_negative     CHECK (issued_count >= 0),
    CONSTRAINT ck_coupon_quantity_non_negative   CHECK (total_quantity IS NULL OR total_quantity >= 0),
    CONSTRAINT ck_coupon_issued_within_total     CHECK (total_quantity IS NULL OR issued_count <= total_quantity),
    CONSTRAINT ck_coupon_per_member_positive     CHECK (per_member_limit > 0),
    CONSTRAINT ck_coupon_valid_period            CHECK (valid_until > valid_from),
    INDEX idx_coupon_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- FK 컬럼 인덱스: coupon_id 는 uk(coupon_id, member_id) 의 좌측 프리픽스로 커버,
-- member_id 는 idx(member_id, status) 좌측, order_id 는 idx_member_coupon_order 로 커버.
CREATE TABLE member_coupon (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    coupon_id   BIGINT      NOT NULL,
    member_id   BIGINT      NOT NULL,
    status      VARCHAR(30) NOT NULL DEFAULT 'ISSUED',
    issued_at   DATETIME(6) NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    used_at     DATETIME(6) NULL,
    order_id    BIGINT      NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_member_coupon_coupon_member UNIQUE (coupon_id, member_id),
    CONSTRAINT fk_member_coupon_coupon FOREIGN KEY (coupon_id) REFERENCES coupon (id) ON DELETE RESTRICT,
    CONSTRAINT fk_member_coupon_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT fk_member_coupon_order  FOREIGN KEY (order_id)  REFERENCES orders (id) ON DELETE SET NULL,
    INDEX idx_member_coupon_member_status (member_id, status),
    INDEX idx_member_coupon_order (order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
```

### V15 — `orders.discount_amount`

```sql
-- V15: orders 쿠폰 할인 컬럼 (ERD §4.9).
--
-- 확장(M13) 범위. payable = total_amount - discount_amount 파생(저장 안 함).
-- applied_member_coupon_id 를 두지 않아 orders <-> member_coupon 순환 FK 회피
-- (사용 쿠폰 추적은 member_coupon.order_id 역참조 — ERD §4.16 비고).
ALTER TABLE orders
    ADD COLUMN discount_amount BIGINT NOT NULL DEFAULT 0 AFTER safe_packaging_requested,
    ADD CONSTRAINT ck_orders_discount_within_total CHECK (discount_amount >= 0 AND discount_amount <= total_amount);
```
