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

`com.groove` 라는 하나의 모듈 안에 회원·카탈로그·주문·결제·쿠폰·배송·리뷰·클레임이 함께 산다. 이 정도 규모가 되면 패키지 최상위를 무엇으로 가르느냐 — 기술 레이어(controller/service/repository)냐, 도메인이냐 — 가 코드 응집도와 탐색성, 그리고 도메인 간 결합을 통제하는 능력에 곧바로 영향을 준다.

바라는 바는 세 가지였다. 한 기능을 고칠 때 관련 코드가 한 패키지에 모여 있을 것, 도메인 간 의존이 단방향으로 흘러 순환이 생기지 않을 것, 그리고 단일 모듈이라 컴파일러가 경계를 강제해 주지 못하는 만큼 패키지 구조 자체가 규율 역할을 할 것.

---

## Decision

최상위는 도메인으로 가르고, 각 도메인 안에서 가벼운 레이어로 나눈다.

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

각 도메인은 `api / application / domain / exception` 으로 나누고, 필요할 때만 `event`·`gateway`·`security`·`migration` 을 덧붙인다.

| 레이어 | 책임 | 예 |
|---|---|---|
| `api` | REST 컨트롤러, 요청/응답 DTO, rate-limit 설정 | `order/api/dto/OrderCreateRequest` |
| `application` | 트랜잭션 경계·오케스트레이션 | `order/application/OrderService` |
| `domain` | 엔티티·리포지토리·업무 규칙·enum | `order/domain/Order`, `OrderRepository` |
| `exception` | 도메인 특화 예외 | `order/exception/OrderNotFoundException` |
| `event` | 도메인 이벤트 정의 | `order/event/OrderPaidEvent` |
| `gateway` | 외부 연동 추상화 | `payment/gateway/PaymentGateway` |

### 의존 규칙

의존은 단방향으로 흐른다. 주문·결제 같은 코어 흐름이 주변 도메인을 참조하되 그 반대는 막는다. 멀리 떨어진 도메인끼리의 협력은 직접 호출 대신 이벤트나 Outbox 를 거쳐 결합을 끊는다(결제가 배송을 직접 부르지 않고 `OrderPaidEvent` 를 아웃박스에 남기는 식이다 → [domain-events-and-outbox.md](./domain-events-and-outbox.md)).

공통화가 필요한 것은 전부 `common/` 으로만 모은다. 도메인이 서로의 내부를 들춰 쓰는 대신, outbox·idempotency·cache·ratelimit 같은 공통 인프라를 한곳에 둔다. 패키지의 의도는 `package-info.java` 로 적어 둔다(예: `order/application` 에는 "트랜잭션 경계 + orderNumber 발급기"라고 명시). 커버리지 측정에서도 `package-info`·`dto`·`*Properties` 는 빠진다.

규칙이 실제로 어떻게 적용되는지는 `OrderService` 의 import 가 잘 보여준다. 재고를 위해 `catalog.album.domain`, 쿠폰 적용을 위임하려고 `coupon.application`, 회원 검증을 위해 `member.domain` 을 단방향으로 참조하지만, 그 도메인들이 거꾸로 `order` 를 끌어다 쓰지는 않는다.

---

## Considered Options

### Option A — 레이어 우선 (controller/service/repository 최상위) ❌

`com.groove.controller.*`, `com.groove.service.*` 처럼 기술 레이어로 최상위를 가르는 전통적인 방식이다. 레이어 위치는 한눈에 들어온다. 하지만 기능 하나를 고치면 컨트롤러·서비스·리포지토리가 서로 다른 최상위 패키지로 흩어져 응집도가 떨어지고, 도메인 경계나 결합 관계가 코드에 드러나지 않는다.

### Option B — 완전 헥사고날 (port/adapter 전면 적용) ⚠️

도메인마다 inbound/outbound port 와 adapter 를 전면적으로 분리하는 방식이다. 의존 역전이 엄격해지고 테스트 대역을 끼우기 쉽다. 다만 단일 모듈에 CRUD 성격의 도메인이 다수인 이 프로젝트에서는 port 인터페이스를 끝없이 양산하는 보일러플레이트가 따른다. 외부 연동이 사실상 PG 정도라, 들이는 비용 대비 효용이 작다.

### Option C — 도메인 우선 + 경량 레이어 ✅ (채택)

최상위는 도메인으로, 내부는 api/application/domain/exception 으로 나누고, 외부 연동만 `gateway` 로 떼어 낸다. 도메인 응집도와 탐색성이 좋고 의존도 단방향으로 통제되며 보일러플레이트는 최소다. 단점이라면 컴파일러가 도메인 경계를 강제하지 못해 규율에 기대야 한다는 점이다.

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

기능을 고칠 때 손댈 코드가 해당 도메인 패키지에 모여 있어 찾고 바꾸기가 수월하다. 헥사고날의 장점 중 정작 필요한 것 — 외부 연동의 분리 — 만 `gateway` 로 취했기 때문에, PG 를 교체할 때도 어댑터만 갈아끼우면 된다(→ [payment-gateway-mock.md](./payment-gateway-mock.md)). 멀리 떨어진 협력을 이벤트·Outbox 로 끊어 둔 덕에 순환 의존은 구조적으로 생기지 않는다.

**부정적 / 트레이드오프**

단일 모듈이라 컴파일러가 잘못된 도메인 간 의존을 막아 주지 못한다. 결국 코드리뷰 규율에 기대게 되는데, 더 단단히 하려면 ArchUnit 류의 의존성 테스트로 보강할 여지가 있다(지금은 리뷰로 통제한다). 또 하나는 "어디까지가 공통인가"가 모호해지면 `common` 이 잡동사니로 비대해질 수 있다는 점이다. 그래서 공통은 "도메인과 무관한 인프라"로만 한정한다는 기준을 유지한다.

---

## References

- [ARCHITECTURE.md](../ARCHITECTURE.md) (전체 구조)
- 코드: [`OrderService.java`](../../backend/src/main/java/com/groove/order/application/OrderService.java) (단방향 import), [`payment/gateway/`](../../backend/src/main/java/com/groove/payment/gateway/), [`common/`](../../backend/src/main/java/com/groove/common/)
- 도메인 간 협력: [domain-events-and-outbox.md](./domain-events-and-outbox.md)
- [Package by Feature, not Layer](https://phauer.com/2020/package-by-feature/)
