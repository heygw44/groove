# 도메인 용어집 (Glossary): Groove

| 항목 | 값 |
|---|---|
| 버전 | 1.3 |
| 작성일 | 2026-05-05 |
| 최종 수정일 | 2026-06-17 (확장 M16 반영 — 클레임 엔티티·enum 추가, 아웃박스·로컬 캐시 도입 정정) |
| 관련 문서 | ARCHITECTURE.md, ERD.md, API.md |
| 정본 우선순위 | ERD > ARCHITECTURE > API |

> 본 문서는 프로젝트 내 도메인 용어 정의를 일원화하여 팀 내(또는 자기 자신과의) 용어 혼선을 방지하기 위한 단일 출처(Single Source of Truth)다.
> ERD가 변경되면 본 문서도 동기화되어야 하며, 충돌 시 ERD를 우선한다.

---

## 1. 표기 통일 규칙

### 1.1 명명 규칙

| 영역 | 규칙 | 예시 |
|---|---|---|
| Java 클래스 | PascalCase | `Album`, `OrderStatus`, `PaymentGateway` |
| Java 패키지 | 전 소문자, 단수형 | `com.groove.order`, `com.groove.payment` |
| Java 변수/메서드 | camelCase | `memberId`, `createOrder()` |
| Java enum 값 | UPPER_SNAKE_CASE | `PAYMENT_FAILED`, `LP_DOUBLE` |
| DB 테이블 | snake_case, 단수형 | `album`, `cart_item`, `refresh_token` |
| DB 테이블 (예외) | 예약어 회피 시 복수형 | `orders` |
| DB 컬럼 | snake_case | `order_number`, `member_id`, `created_at` |
| FK 컬럼 | `{대상_테이블_단수}_id` | `album_id`, `member_id` |
| 인덱스 | `idx_{table}_{col[_col...]}` | `idx_orders_member_created` |
| 유니크 제약 | `uk_{table}_{col[_col...]}` | `uk_member_email` |
| API URL | kebab-case, 복수형 리소스 | `/api/v1/orders`, `/api/v1/payments` |
| DTO 접미사 | `Request` / `Response` | `CreateOrderRequest`, `OrderResponse` |

### 1.2 영문/한글 표기 정책

- **문서**: 첫 등장 시 `한글(영문)` 병기 — 예: 한정반(Limited Edition)
- **이후 본문**: 한글 또는 영문 중 일관 사용. 코드 식별자와 직접 대응되는 경우 영문 우선 (예: `OrderStatus`)
- **금액**: 항상 원 단위 정수 (KRW), `BIGINT`로 저장
- **시간**: ISO 8601 UTC, 마이크로초 정밀도(`DATETIME(6)`)

### 1.3 정본(Source of Truth) 우선순위

| 카테고리 | 정본 |
|---|---|
| 테이블·컬럼·인덱스 | ERD.md |
| 패키지·클래스 구조 | ARCHITECTURE.md |
| API 시그니처·에러 코드 | API.md |
| **용어 정의 (본 문서)** | 위를 종합한 결과를 기록. 충돌 시 ERD ≻ ARCH ≻ API |

---

## 2. 핵심 도메인 엔티티

이슈 #1 작업 범위의 9개 도메인 엔티티 + 일치 검증 과정에서 누락 방지를 위해 보조 엔티티를 함께 정의한다.

### 2.1 Member — 회원

| 항목 | 값 |
|---|---|
| 영문 | Member |
| 한글 | 회원 |
| 테이블 | `member` |
| 정의 | LP를 탐색·구매하는 인증 사용자. 이메일 + 비밀번호로 식별되며, 역할(USER/ADMIN)에 따라 권한이 분리된다. |
| 비고 | 비회원 주문(게스트)은 Member 엔티티를 갖지 않고 `orders.guest_email`로 처리. Soft delete(`deleted_at`)는 회원만 적용. |

### 2.2 Album — LP 상품

| 항목 | 값 |
|---|---|
| 영문 | Album |
| 한글 | 앨범 / LP |
| 테이블 | `album` |
| 정의 | 판매 단위가 되는 LP(바이닐) 상품. 단일 SKU(컨디션·옵션 없음)이며, 아티스트·장르·레이블에 연결된다. |
| 핵심 필드 | `title`, `artist_id`, `genre_id`, `label_id`, `release_year`, `format`, `price`, `stock`, `status`, `is_limited` |
| 비고 | "상품"이라는 일반 용어 대신 본 프로젝트 전반에서 Album으로 통일. 한정반의 핵심 엔티티. |

### 2.3 Artist — 아티스트

| 항목 | 값 |
|---|---|
| 영문 | Artist |
| 한글 | 아티스트 |
| 테이블 | `artist` |
| 정의 | LP를 발매한 음악가/그룹. 별도 엔티티로 분리되어 아티스트 페이지 및 검색 키로 사용된다. |
| 비고 | 1:N 관계 (Artist → Album). 동명이인은 v1 미고려. |

### 2.4 Genre — 장르

| 항목 | 값 |
|---|---|
| 영문 | Genre |
| 한글 | 장르 |
| 테이블 | `genre` |
| 정의 | 단일 레벨의 음악 분류 (Rock, Jazz, K-Pop 등). Album 분류의 1차 기준이며 검색 필터의 핵심. |
| 비고 | 계층(서브장르)은 v1 미지원. `name` UNIQUE 제약. |

### 2.5 Label — 음반 레이블

| 항목 | 값 |
|---|---|
| 영문 | Label |
| 한글 | 레이블 / 음반사 |
| 테이블 | `label` |
| 정의 | 앨범을 발매한 레코드 회사. 검색 보조 정보로 사용되며, 누락 가능(`label_id` NULL 허용). |
| 비고 | 필수 노출 항목 아님. |

### 2.6 Cart — 장바구니

| 항목 | 값 |
|---|---|
| 영문 | Cart |
| 한글 | 장바구니 |
| 테이블 | `cart`, `cart_item` |
| 정의 | 회원이 구매 의향을 임시 저장하는 컨테이너. 회원당 1개(`uk_cart_member`)이며, 동일 Album의 중복 항목은 `uk_cart_item_cart_album`으로 차단된다. |
| 비고 | **게스트는 장바구니를 사용하지 않는다.** 게스트 흐름은 즉시 구매 only. |

### 2.7 Order — 주문

| 항목 | 값 |
|---|---|
| 영문 | Order |
| 한글 | 주문 |
| 테이블 | `orders`(예약어 회피), `order_item` |
| 정의 | 회원 또는 게스트가 1개 이상의 Album을 구매하기로 한 거래 단위. 외부 노출용 식별자 `order_number`(예: `ORD-20260505-XXXX`)와 내부 PK를 분리한다. |
| 상태 | `OrderStatus` 8값 — §3.4 참조 |
| 게스트 처리 | `member_id IS NULL` AND `guest_email IS NOT NULL` (애플리케이션 레벨 XOR 검증) |
| 비고 | 주문 항목(`order_item`)은 가격·앨범명을 스냅샷으로 보존 (사후 상품 변경에도 이력 유지). |

### 2.8 Payment — 결제

| 항목 | 값 |
|---|---|
| 영문 | Payment |
| 한글 | 결제 |
| 테이블 | `payment`, `idempotency_record` |
| 정의 | Order에 대한 1:1 결제 트랜잭션. 재시도는 새 행이 아닌 동일 행의 상태 갱신으로 처리하며, 중복 요청은 멱등성 키(`Idempotency-Key`)로 차단된다. |
| 상태 | `PaymentStatus` 4값 — §3.5 참조 |
| 수단 | `PaymentMethod` 3값 — §3.6 참조 |
| 비고 | v1은 Mock PG. Strategy 패턴(`PaymentGateway` 인터페이스)으로 추상화하여 실 PG 도입 확장점 확보. |

### 2.9 Shipping — 배송

| 항목 | 값 |
|---|---|
| 영문 | Shipping |
| 한글 | 배송 |
| 테이블 | `shipping` |
| 정의 | 결제가 완료된 Order에 대해 생성되는 배송 단위. 운송장 번호(UUID 기반)와 수령인 정보를 보유하며, 스케줄러가 상태를 자동 진행한다. |
| 상태 | `ShippingStatus` 3값 — §3.7 참조 |
| 생성 시점 | 결제 완료 시 `ORDER_PAID` **트랜잭셔널 아웃박스** 이벤트를 통해 별도 트랜잭션으로 생성 (#237, `OrderPaidOutboxHandler`) |
| 비고 | LP 특성 반영 필드 `safe_packaging_requested`(안전 포장 요청). |

### 2.10 Review — 리뷰

| 항목 | 값 |
|---|---|
| 영문 | Review |
| 한글 | 리뷰 / 평점 |
| 테이블 | `review` |
| 정의 | 배송 완료(`DELIVERED`)된 주문의 Album에 대해 회원이 작성하는 1~5점 평가. 1주문-1상품-1리뷰 제약(`uk_review_order_album`). |
| 비고 | 게스트 주문은 리뷰 작성 불가 (`member_id` NOT NULL). |

### 2.11 보조 엔티티 (참고)

| 영문 | 한글 | 테이블 | 용도 |
|---|---|---|---|
| RefreshToken | 리프레시 토큰 | `refresh_token` | Rotation 기반 재발급. 평문 미저장(SHA-256 해시). |
| OrderItem | 주문 항목 | `order_item` | 주문 시점의 가격/앨범명 스냅샷 보존. |
| CartItem | 장바구니 항목 | `cart_item` | Cart 내 Album 수량 관리. |
| IdempotencyRecord | 멱등성 레코드 | `idempotency_record` | 결제 등 멱등성 키 + 응답 스냅샷. TTL 24시간. |
| MemberCoupon | 회원 보유 쿠폰 | `member_coupon` | Coupon 정책의 회원별 발급 인스턴스. 발급·사용·만료 이력. *(확장 M13)* |

### 2.12 Coupon — 쿠폰 (확장 M13)

| 항목 | 값 |
|---|---|
| 영문 | Coupon |
| 한글 | 쿠폰 |
| 테이블 | `coupon`(정책), `member_coupon`(회원 발급분) |
| 정의 | 주문 금액에 정액/정률 할인을 적용하는 혜택 정책. 정책(`coupon`)과 회원 보유 인스턴스(`member_coupon`)를 분리한다. 발급은 **선착순 한정수량**(대용량 동시성 처리의 핵심) 또는 **관리자/이벤트 직접지급**. |
| 할인 종류 | `CouponDiscountType` 2값 — §3.9 참조 |
| 정책 상태 | `CouponStatus` 3값 — §3.10 / 보유 상태 `MemberCouponStatus` 4값 — §3.11 |
| 적용 | 회원 주문에만 적용(게스트 불가). **payable = total_amount − discount_amount**, 결제는 payable 청구. 주문 취소/환불 시 쿠폰 복원(USED→ISSUED). |
| 비고 | §8 v2 후보였던 `coupon`/`coupon_issue` 를 이후 확장 도메인으로 승격. 동시성 결정 [decisions/coupon-concurrency.md](decisions/coupon-concurrency.md). |

---

### 2.13 Claim — 클레임 (취소/반품 통합, 확장 M16)

| 항목 | 값 |
|---|---|
| 영문 | Claim |
| 한글 | 클레임 (취소/반품) |
| 테이블 | `claim`, `claim_item` |
| 정의 | 취소·반품을 `OrderStatus` 에 섞지 않고 주문을 참조하는 별도 aggregate 로 통합한 모델. `claimType` 으로 **CANCEL**(발송 전 부분 취소·즉시 환불)과 **RETURN**(발송 후 반품·역물류)을 구분한다(상태 폭발 회피). |
| 종류 | `ClaimType` 2값 — §3.12 참조 |
| 상태 | `ClaimStatus` 6값 — §3.13 참조 |
| 환불 | 부분 환불액은 할인 안분(`payable × 취소·반품정가/총정가`), 누적액은 `payment.refunded_amount`. 전량 도달 시 `PaymentStatus` PARTIALLY_REFUNDED→REFUNDED. |
| 비고 | 발송 전 부분 취소는 주문 상태 유지, 전량 취소 시에만 CANCELLED. 자동 진행은 `ClaimProgressScheduler`. |

---

## 3. 상태 enum

ERD §6 기준. DB는 `VARCHAR(30)`, JPA는 `@Enumerated(EnumType.STRING)`.

### 3.1 MemberRole — 회원 역할

| 값 | 의미 |
|---|---|
| `USER` | 일반 회원 (기본값) |
| `ADMIN` | 관리자 |

### 3.2 AlbumStatus — 상품 노출 상태

| 값 | 의미 |
|---|---|
| `SELLING` | 판매 중 (기본값) |
| `SOLD_OUT` | 품절 (재고 0, 자동 또는 수동 전환) |
| `HIDDEN` | 노출 차단 (관리자 비공개 처리) |

### 3.3 AlbumFormat — LP 포맷

| 값 | 의미 |
|---|---|
| `LP_12` | 12인치 LP (표준) |
| `LP_DOUBLE` | 더블 LP (2장 구성) |
| `EP` | EP (Extended Play) |
| `SINGLE_7` | 7인치 싱글 |

### 3.4 OrderStatus — 주문 상태 ★

상태 머신은 ARCHITECTURE.md §8 다이어그램 참조.

| 값 | 의미 | 진입 조건 |
|---|---|---|
| `PENDING` | 주문 생성됨, 결제 대기 | 주문 생성 시 (재고 차감 완료) |
| `PAID` | 결제 완료 | PG 웹훅 성공 콜백 |
| `PREPARING` | 배송 준비 중 | 결제 완료 → 배송 엔트리 생성 |
| `SHIPPED` | 출고 완료 | 스케줄러 자동 전환 |
| `DELIVERED` | 배송 완료 | 스케줄러 자동 전환 |
| `COMPLETED` | 거래 종료 (확정) | 배송 완료 후 N일 경과 |
| `CANCELLED` | 주문 취소 | 사용자(PAID 이전) 또는 관리자(PREPARING까지) |
| `PAYMENT_FAILED` | 결제 실패 | PG 응답 실패 → 재고 복원 (보상 트랜잭션) |

**전이 규칙**
- 모든 전이는 `OrderStatus.canTransitionTo(next)` 메서드로 검증
- 위반 시 `IllegalStateTransitionException` (BusinessException 상속) → API 응답 `ORDER_INVALID_STATE_TRANSITION` (HTTP 409)
- DB 트리거 미사용. 애플리케이션 레벨 단일 진입점(`Order.changeStatus`)으로 일원화

### 3.5 PaymentStatus — 결제 상태

| 값 | 의미 |
|---|---|
| `PENDING` | 결제 요청 후 PG 응답 대기 (웹훅 대기 포함) |
| `PAID` | 결제 성공 (`paid_at` 기록) |
| `FAILED` | 결제 실패 (`failure_reason` 기록) |
| `PARTIALLY_REFUNDED` | 부분 환불 — 누적 환불액(`refunded_amount`)이 전액 미만 (부분 취소·부분 반품, M16) |
| `REFUNDED` | 전액 환불 완료 (관리자 처리 / 누적 환불액 == amount) |

### 3.6 PaymentMethod — 결제 수단

| 값 | 의미 |
|---|---|
| `CARD` | 신용/체크카드 (Mock 분기) |
| `BANK_TRANSFER` | 계좌이체 (Mock 분기) |
| `MOCK` | Mock PG 기본값 (수단 미특정) |

### 3.7 ShippingStatus — 배송 상태

| 값 | 의미 | 전이 |
|---|---|---|
| `PREPARING` | 배송 준비 (기본값) | Shipping 생성 시 |
| `SHIPPED` | 출고 완료 | 스케줄러 자동 전환 (`shipped_at` 기록) |
| `DELIVERED` | 배송 완료 | 스케줄러 자동 전환 (`delivered_at` 기록) |

### 3.8 IdempotencyStatus — 멱등성 처리 상태

| 값 | 의미 |
|---|---|
| `PROCESSING` | 처리 중 (동시 동일 키 요청 → 409 응답) |
| `COMPLETED` | 처리 완료 (저장된 응답 스냅샷 반환) |
| `FAILED` | 처리 실패 (재시도 가능 여부는 정책에 따름) |

### 3.9 CouponDiscountType — 쿠폰 할인 종류 (확장 M13)

| 값 | 의미 |
|---|---|
| `FIXED_AMOUNT` | 정액 할인 (예: 5,000원). `discount_value` = 원 |
| `PERCENTAGE` | 정률 할인 (예: 10%). `discount_value` = 1~100, `max_discount_amount` 상한 캡 적용 |

### 3.10 CouponStatus — 쿠폰 정책 상태 (확장 M13)

| 값 | 의미 | 전이 |
|---|---|---|
| `ACTIVE` | 발급/사용 가능 (기본값) | 생성 시 |
| `SUSPENDED` | 발급 일시 중단 (기보유분은 사용 가능) | 관리자 |
| `ENDED` | 종료 (발급·신규 사용 불가) | 관리자 또는 기간 만료 |

### 3.11 MemberCouponStatus — 회원 보유 쿠폰 상태 (확장 M13)

| 값 | 의미 | 전이 |
|---|---|---|
| `ISSUED` | 발급됨, 사용 대기 (기본값) | 발급 시 |
| `USED` | 주문에 사용됨 (`used_at`·`order_id` 기록) | 주문 적용 |
| `EXPIRED` | 만료됨 (`expires_at` 경과) | 만료 스케줄러 |
| `CANCELLED` | 회수/무효 | 관리자, 또는 사용 후 주문 취소 시 USED→ISSUED 복원(CANCELLED 아님) |

### 3.12 ClaimType — 클레임 종류 (확장 M16)

| 값 | 의미 |
|---|---|
| `CANCEL` | 발송 전(PAID/PREPARING) 부분 취소 — 회수·검수 없이 `REQUESTED→REFUNDED` 즉시 환불 (관리자) |
| `RETURN` | 발송 후(DELIVERED/COMPLETED) 반품 — 역물류 상태머신 (회원 접수 + 관리자 승인) |

### 3.13 ClaimStatus — 클레임 상태 (확장 M16)

| 값 | 의미 | 전이 |
|---|---|---|
| `REQUESTED` | 접수됨 (기본값) | 회원 반품 접수 / 관리자 부분취소(즉시 REFUNDED) |
| `APPROVED` | 승인 — 회수 대기 | 관리자 승인 |
| `IN_TRANSIT` | 회수 중 | 스케줄러 자동 |
| `INSPECTING` | 검수 중 | 스케줄러 자동 |
| `REFUNDED` | 환불 완료 (종착) | 검수 통과 + 환불 |
| `REJECTED` | 거부 (종착) | 접수 반려 / 검수 불합격 |

---

## 4. 자주 쓰는 용어 (도메인·기술)

### 4.1 도메인 용어

| 용어 | 정의 |
|---|---|
| 한정반 (Limited Edition) | 발행 수량이 제한된 LP. `album.is_limited=true` 플래그로 표시. 동시성 처리의 핵심 트리거. |
| 단일 SKU | 한 Album에 옵션·컨디션 분기가 없음(v1). 색상·픽처디스크 등은 v2 후보. |
| 게스트 주문 (Guest Order) | 회원가입 없이 진행하는 주문. `member_id IS NULL`, 조회는 (주문번호 + 이메일) 매칭 인증. |
| 주문 번호 (Order Number) | 외부 노출용 주문 식별자. 형식 `ORD-YYYYMMDD-XXXX`. 내부 PK(`orders.id`)와 분리. |
| 운송장 번호 (Tracking Number) | UUID 기반으로 자체 발급. 외부 택배사 API 미연동(v1). |
| 단일 재고 희귀반 | `stock=1`인 Album. 재고 정합성의 경계 시나리오. |
| 선착순 발급 (First-Come Issuance) | 한정수량(`coupon.total_quantity`) 쿠폰을 회원이 먼저 요청한 순서대로 발급. 대용량 동시성 처리의 핵심 — 재고 오버셀과 동형 문제. *(확장 M13)* |
| 정액 할인 (Fixed Amount) | 고정 금액 할인. `CouponDiscountType.FIXED_AMOUNT`. *(확장 M13)* |
| 정률 할인 (Percentage) | 비율 할인 + 상한 캡(`max_discount_amount`). `CouponDiscountType.PERCENTAGE`. *(확장 M13)* |
| 최소 주문금액 (Min Order Amount) | 쿠폰 적용 가능한 주문 총액 하한(`coupon.min_order_amount`). 미달 시 적용 거부. *(확장 M13)* |
| 결제 금액 (Payable Amount) | 쿠폰 할인 후 실제 청구액 = `total_amount − discount_amount`. 별도 저장 없이 파생. *(확장 M13)* |
| 직접지급 (Direct Grant) | 관리자/이벤트가 특정 회원에게 쿠폰을 발급(선착순 한정수량과 무관, `total_quantity` NULL 가능). *(확장 M13)* |

### 4.2 기술 용어

| 용어 | 정의 |
|---|---|
| 멱등성 키 (Idempotency-Key) | 동일 결제 요청의 중복 처리 방지를 위한 클라이언트 발급 키. HTTP 헤더로 전달, `idempotency_record` 테이블에 응답 스냅샷과 매핑 저장. TTL 24시간. |
| Refresh Token Rotation | 리프레시 토큰 사용 시마다 새 토큰 발급 + 기존 토큰 폐기. 폐기된 토큰 재사용 시 해당 사용자의 모든 활성 토큰 무효화(탈취 감지). |
| 보상 트랜잭션 (Compensating Transaction) | 실패한 작업의 부수 효과를 되돌리는 후속 트랜잭션. 예: 결제 실패 시 재고 복원. |
| 비관적 락 (Pessimistic Lock) | `SELECT ... FOR UPDATE`로 행 단위 잠금. v1 동시성 처리 기본. 정합성 확실, TPS 측정에 사용. |
| 낙관적 락 (Optimistic Lock) | 버전 컬럼 기반 충돌 감지. 비관적 락의 대안 후보. |
| 원자적 조건부 UPDATE (Atomic Conditional Update) | `UPDATE ... SET cnt=cnt+1 WHERE id=? AND cnt<limit` 처럼 조건과 증가를 단일 DB 문으로 처리. 영향 행 수(0/1)로 성공 판정. 행 락을 길게 잡지 않아 비관적 락보다 처리량이 높다. 선착순 쿠폰 발급의 최종 채택안. *(확장 M13)* |
| 핫 로우 (Hot Row) | 다수 트랜잭션이 동시에 갱신하는 단일 행(예: `coupon.issued_count`). 원자적 UPDATE 로도 남는 병목 — 극한 트래픽에서 Redis 카운터 전환의 근거. *(확장 M13)* |
| N+1 문제 | 연관 엔티티를 개별 쿼리로 조회하여 1+N개 쿼리가 발생하는 현상. 페치 조인 / `@EntityGraph`로 해결. |
| ProblemDetail (RFC 7807) | 에러 응답 표준 포맷. `application/problem+json`. 본 프로젝트는 `code`, `timestamp`, `traceId` 필드를 추가 확장. |
| MDC (Mapped Diagnostic Context) | SLF4J/Logback의 요청 단위 컨텍스트 저장소. `requestId`, `userId` 등을 모든 로그에 자동 포함. |
| X-Request-Id | 요청 추적 ID. 클라이언트 미설정 시 서버에서 생성하여 응답 헤더로 반환. MDC에 주입. |
| Idempotency-Key (헤더) | §4.2 멱등성 키와 동일. 결제 등 일부 엔드포인트에서 필수. |
| Application Event | Spring 내부 이벤트 메커니즘. 인프로세스 비동기 전파(현재 `MemberWithdrawnEvent` — 탈퇴 정리). 결제 후속(`OrderPaidEvent`)은 아웃박스로 전환됨. v2에서 외부 큐(Kafka/Redis Stream) 후보. |
| AFTER_COMMIT | `@TransactionalEventListener`의 phase. 발행 트랜잭션 커밋 이후에만 핸들러 실행 → 롤백 시 부수효과 차단. |
| Outbox 패턴 | 이벤트 발행과 DB 트랜잭션의 원자성을 보장하는 패턴. 결제 후속(배송 생성)에 **도입 완료**(#237) — `outbox_event` 기록 + 릴레이 at-least-once 발행 + 멱등 컨슈머. |
| Cache-Aside | 읽기 시 캐시 우선 조회 → 미스 시 DB 조회 + 캐시 적재. 카탈로그 조회에 **Caffeine 로컬 캐시로 적용 완료**(`AlbumService`, #236). 분산(Redis) 캐시는 멀티 인스턴스 시. |
| Rate Limit | 시간 단위 요청 횟수 제한. v1 Bucket4j(in-memory). 멀티 인스턴스 시 Bucket4j-Redis 전환. |
| Bucket4j | Token Bucket 알고리즘 기반 자바 Rate Limit 라이브러리. |
| Strategy 패턴 | PG 결제 모듈 추상화에 사용. `PaymentGateway` 인터페이스 + `MockPaymentGateway` 구현체. |
| Mock PG | 자체 모킹 결제 게이트웨이. 지연 시뮬레이션(100~500ms) + 비동기 웹훅 콜백(1~5초). |
| TPS (Transactions Per Second) | 초당 처리 트랜잭션 수. 부하 테스트(k6) 핵심 지표. Before/After 비교 단위. |
| 베이스라인 (Baseline) | 개선 전/후 비교의 기준값. |
| ADR (Architecture Decision Record) | 아키텍처 결정 기록. 결정·근거·대안을 표 형태로 보존. |
| SoT (Single Source of Truth) | 단일 출처. 본 프로젝트는 도메인별로 정본 우선순위(§1.3) 명시. |

---

## 5. 영문↔한글 매핑 표 (검색용)

| 영문 | 한글 | 분류 |
|---|---|---|
| Album | 앨범 / LP | 엔티티 |
| Artist | 아티스트 | 엔티티 |
| Genre | 장르 | 엔티티 |
| Label | 레이블 / 음반사 | 엔티티 |
| Member | 회원 | 엔티티 |
| Cart | 장바구니 | 엔티티 |
| Order | 주문 | 엔티티 |
| OrderItem | 주문 항목 | 엔티티 |
| Payment | 결제 | 엔티티 |
| Shipping | 배송 | 엔티티 |
| Review | 리뷰 / 평점 | 엔티티 |
| RefreshToken | 리프레시 토큰 | 엔티티 |
| IdempotencyRecord | 멱등성 레코드 | 엔티티 |
| Coupon | 쿠폰 (정책) | 엔티티 |
| MemberCoupon | 회원 보유 쿠폰 | 엔티티 |
| Claim | 클레임 (취소/반품) | 엔티티 |
| ClaimItem | 클레임 항목 | 엔티티 |
| OutboxEvent | 아웃박스 이벤트 | 엔티티 |
| Partial Cancel / Refund | 부분 취소 / 부분 환불 | 도메인 |
| Reverse Logistics | 역물류 (회수·검수) | 도메인 |
| First-Come Issuance | 선착순 발급 | 도메인 |
| Fixed Amount / Percentage | 정액 / 정률 할인 | 도메인 |
| Payable Amount | 결제 금액 (할인 후) | 도메인 |
| Atomic Conditional Update | 원자적 조건부 UPDATE | 기술 |
| Hot Row | 핫 로우 | 기술 |
| Limited Edition | 한정반 | 도메인 |
| Guest Order | 게스트 주문 | 도메인 |
| Order Number | 주문 번호 | 도메인 |
| Tracking Number | 운송장 번호 | 도메인 |
| SKU | 단일 재고 단위 | 도메인 |
| Idempotency-Key | 멱등성 키 | 기술 |
| Refresh Token Rotation | 리프레시 토큰 회전 | 기술 |
| Compensating Transaction | 보상 트랜잭션 | 기술 |
| Pessimistic Lock | 비관적 락 | 기술 |
| Optimistic Lock | 낙관적 락 | 기술 |
| N+1 Problem | N+1 문제 | 기술 |
| ProblemDetail | 문제 상세 (에러 포맷) | 기술 |
| MDC | 매핑된 진단 컨텍스트 | 기술 |
| Application Event | 애플리케이션 이벤트 | 기술 |
| Outbox Pattern | 아웃박스 패턴 | 기술 |
| Cache-Aside | 캐시 어사이드 | 기술 |
| Rate Limit | 요청 제한 | 기술 |
| Strategy Pattern | 전략 패턴 | 기술 |
| Mock PG | 모킹 결제 게이트웨이 | 기술 |
| TPS | 초당 트랜잭션 수 | 측정 |
| Baseline | 베이스라인 | 측정 |
| ADR | 아키텍처 결정 기록 | 메타 |
| SoT | 단일 출처 | 메타 |

---

## 6. 변경 이력

| 버전 | 날짜 | 내용 |
|---|---|---|
| 1.0 | 2026-05-05 | 최초 작성. 9개 핵심 도메인 + 8개 enum + 보조 엔티티 4개 + 자주 쓰는 용어. ERD/PRD/ARCHITECTURE/API 교차 검증. |
| 1.1 | 2026-05-26 | 확장(쿠폰 시스템, *계획*) 반영: Coupon/MemberCoupon 엔티티(§2.12), enum 3종(§3.9~3.11), 도메인 용어 6종 + 기술 용어 2종, 매핑표 갱신. ERD v1.5 와 동기화. |
| 1.2 | 2026-06-04 | 확장 M13 쿠폰 시스템 **구현 완료** 반영 — 쿠폰 용어·enum·매핑표의 *계획* 표기 일괄 제거. ERD v1.6 와 동기화. |
| 1.3 | 2026-06-17 | 확장 M16 반영 — Claim 엔티티(§2.13)·`ClaimType`(§3.12)·`ClaimStatus`(§3.13) 추가, `PaymentStatus` 에 PARTIALLY_REFUNDED 보강, 매핑표에 클레임/아웃박스/부분취소·역물류 용어 추가. stale 정정: 배송 생성·Outbox 패턴을 **트랜잭셔널 아웃박스 도입 완료(#237)**, Cache-Aside 를 **Caffeine 로컬 캐시 도입 완료(#236)** 로 갱신. ERD v1.8 와 동기화. |
