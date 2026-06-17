# ADR: 패키지 구조 — 도메인 우선 + 경량 레이어

| 항목 | 값 |
|---|---|
| 상태 | Accepted |
| 날짜 | 2026-06-17 |
| 연관 이슈 | #257 (W12-2 ADR 정리) |
| 작성자 | ParkGunWoo |
| 관련 문서 | [ARCHITECTURE.md](../ARCHITECTURE.md) |

---

## Context

단일 모듈 Spring Boot 애플리케이션(`com.groove`)에 회원·카탈로그·주문·결제·쿠폰·배송·리뷰·클레임 등 다수 도메인이 공존한다. 패키지 최상위를 **기술 레이어로 나눌지(controller/service/repository), 도메인으로 나눌지** 가 코드 응집도·탐색성·도메인 간 결합 통제에 직접 영향을 준다.

요구:
- 도메인 경계가 코드에서 드러나, 한 기능 변경이 한 패키지에 모이길 원한다.
- 도메인 간 의존을 단방향으로 통제해 순환 의존을 막고 싶다.
- 단일 모듈이라 컴파일러가 모듈 경계를 강제하진 못하므로, **패키지 구조 자체가 규율**이 돼야 한다.

---

## Decision

**도메인 기반으로 최상위를 나누고, 각 도메인 안에서 경량 레이어로 분리한다.**

### 최상위 = 도메인

```
com.groove/
├── member/  auth/  catalog/{album,artist,genre,label}/
├── order/  payment/  coupon/  cart/  shipping/  review/  claim/  admin/
└── common/   ← 공통 인프라 (도메인 아님)
    ├── outbox/  idempotency/  cache/  ratelimit/
    └── persistence/  transaction/  logging/  hash/  exception/  scheduling/
```

### 도메인 내부 = 경량 레이어

각 도메인은 `api / application / domain / exception` 으로 나누고, 필요 시 `event`·`gateway`·`security`·`migration` 을 둔다.

| 레이어 | 책임 | 예 |
|---|---|---|
| `api` | REST 컨트롤러, 요청/응답 DTO, rate-limit 설정 | `order/api/dto/OrderCreateRequest` |
| `application` | 트랜잭션 경계·오케스트레이션 | `order/application/OrderService` |
| `domain` | 엔티티·리포지토리·업무 규칙·enum | `order/domain/Order`, `OrderRepository` |
| `exception` | 도메인 특화 예외 | `order/exception/OrderNotFoundException` |
| `event` | 도메인 이벤트 정의 | `order/event/OrderPaidEvent` |
| `gateway` | 외부 연동 추상화 | `payment/gateway/PaymentGateway` |

### 의존 규칙

- **단방향 의존**: 코어 흐름(주문·결제)이 주변 도메인을 참조하되 역방향은 금지. 원거리 협력은 **이벤트/Outbox 경유**로 결합을 끊는다(예: 결제→배송은 직접 호출이 아니라 `OrderPaidEvent` 아웃박스). → [domain-events-and-outbox.md](./domain-events-and-outbox.md)
- **공통화는 `common/`** 으로만. 도메인이 서로의 내부를 꺼내 쓰는 대신 공통 인프라(outbox·idempotency·cache·ratelimit)는 `common` 에 둔다.
- **`package-info.java`** 로 패키지 의도를 문서화한다(예: `order/application` = "트랜잭션 경계 + orderNumber 발급기"). 커버리지 측정에서도 `package-info`·`dto`·`*Properties` 는 제외된다.

`OrderService` 의 import 가 규칙을 보여준다 — `catalog.album.domain`(재고), `coupon.application`(쿠폰 적용 위임), `member.domain`(검증)을 단방향으로 참조하고, 역으로 이들이 `order` 를 참조하지 않는다.

---

## Considered Options

### Option A — 레이어 우선 (controller/service/repository 최상위) ❌

| 항목 | 내용 |
|---|---|
| 방식 | `com.groove.controller.*`, `com.groove.service.*` … |
| 장점 | 레이어 위치가 한눈에 |
| 단점 | 한 기능 변경이 여러 최상위 패키지로 흩어짐(응집도↓), 도메인 경계·결합이 코드에 안 드러남 |

### Option B — 완전 헥사고날 (port/adapter 전면 적용) ⚠️

| 항목 | 내용 |
|---|---|
| 방식 | 도메인마다 inbound/outbound port + adapter 전면 분리 |
| 장점 | 의존 역전 엄격, 테스트 대체 용이 |
| 단점 | 단일 모듈·CRUD 다수 도메인엔 **보일러플레이트 과다**(port 인터페이스 양산). 본 프로젝트는 외부 연동이 PG 정도라 효용 대비 비용 큼 |

### Option C — 도메인 우선 + 경량 레이어 ✅ (채택)

| 항목 | 내용 |
|---|---|
| 방식 | 최상위=도메인, 내부=api/application/domain/exception, 외부 연동만 gateway 로 분리 |
| 장점 | 도메인 응집·탐색성, 단방향 의존 통제, 보일러플레이트 최소 |
| 한계 | 컴파일러가 도메인 경계를 강제하진 않음(규율 의존) |

---

## 비교 요약

| 기준 | A 레이어 우선 | B 헥사고날 | **C 도메인+경량** |
|---|---|---|---|
| 도메인 응집도 | 낮음 | 높음 | **높음** |
| 보일러플레이트 | 낮음 | 높음 | **중** |
| 결합 통제 | 약함 | 강함 | **중(규율+이벤트)** |
| 단일 모듈 적합성 | 중 | 낮음 | **높음** |

---

## Consequences

**긍정적**
- 기능 변경이 해당 도메인 패키지에 모여 탐색·수정이 쉽다.
- 외부 연동만 `gateway` 로 분리(헥사고날의 핵심 이점만 취사) → PG 교체가 어댑터 교체로 끝난다. → [payment-gateway-mock.md](./payment-gateway-mock.md)
- 원거리 협력을 이벤트/Outbox 로 끊어 순환 의존이 구조적으로 없다.

**부정적 / 트레이드오프**
- **규율 의존**: 단일 모듈이라 컴파일러가 도메인 간 잘못된 의존을 막지 못한다 → 코드리뷰·ArchUnit 류 테스트로 보강 여지(현재는 리뷰 규율).
- **`common` 비대화 위험**: "어디까지 공통인가" 기준이 모호하면 `common` 이 잡동사니가 될 수 있다 → 공통은 "도메인 무관 인프라"로 한정하는 기준 유지.

---

## References

- [ARCHITECTURE.md](../ARCHITECTURE.md) (전체 구조)
- 코드: [`OrderService.java`](../../backend/src/main/java/com/groove/order/application/OrderService.java) (단방향 import), [`payment/gateway/`](../../backend/src/main/java/com/groove/payment/gateway/), [`common/`](../../backend/src/main/java/com/groove/common/)
- 도메인 간 협력: [domain-events-and-outbox.md](./domain-events-and-outbox.md)
- [Package by Feature, not Layer](https://phauer.com/2020/package-by-feature/)
