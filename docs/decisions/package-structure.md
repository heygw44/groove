# ADR: 패키지 구조 — 도메인 우선 + 경량 레이어

| 항목 | 값 |
|---|---|
| 상태 | Accepted |
| 날짜 | 2026-06-17 |
| 연관 이슈 | #257 |
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

기능을 고칠 때 손댈 코드가 해당 도메인 패키지에 모여 있어 찾고 바꾸기가 수월하다. 헥사고날의 장점 중 정작 필요한 것 — 외부 연동의 분리 — 만 `gateway` 로 취했기 때문에, PG 를 교체할 때도 어댑터만 갈아끼우면 된다(→ [payment-gateway-mock.md](./payment-gateway-mock.md)). 멀리 떨어진 협력은 이벤트·Outbox 로 끊어 결합을 줄였다. 도메인 간 단방향(순환 없음)은 이제 슬라이스 단위 `beFreeOfCycles` 로 테스트가 강제한다 — 가까운 도메인 간 가드 조회의 역참조는 읽기 전용 포트 인버전으로, 시드의 역참조는 패키지 분리로 끊었다(#349, 아래 ["ArchUnit 으로 강제하는 규칙"](#archunit-으로-강제하는-규칙-344-349) 참고).

**부정적 / 트레이드오프**

단일 모듈이라 컴파일러가 잘못된 도메인 간 의존을 막아 주지 못한다. 결국 코드리뷰 규율에 기대게 되는데, 더 단단히 하려고 ArchUnit 의존성 테스트로 보강했다(#344 — 계층 순서와 도메인 모델의 표현 계층 비의존, #349 — 도메인 간 단방향까지 전역 `beFreeOfCycles` 로 고정). 또 하나는 "어디까지가 공통인가"가 모호해지면 `common` 이 잡동사니로 비대해질 수 있다는 점이다. 그래서 공통은 "도메인과 무관한 인프라"로만 한정한다는 기준을 유지하고, 도메인을 참조하는 부팅 시드는 `common` 이 아니라 `com.groove.bootstrap` 에 둔다(#349).

---

## ArchUnit 으로 강제하는 규칙 (#344, #349)

리뷰 규율로만 지키던 의존 방향을 테스트로 고정했다. 규칙은 `backend/src/test/java/com/groove/architecture/ModuleDependencyRulesTest.java` 에 있고 `./gradlew test` 로 검증된다.

**강제하는 규칙**

- 계층 순서(api → application → domain): `domain` 은 `application`·`api` 에 의존하지 않고, `application` 은 `api` 의 컨트롤러·설정에 의존하지 않는다. 경계 계약인 `api.dto` 는 예외이되 **같은 도메인의 `api.dto` 로만** 좁힌다(예: `OrderService` 의 `OrderCreateRequest`/`OrderResponse`. 다른 도메인의 `api.dto` 참조는 금지 — #349).
- 도메인 모델의 표현 계층 비의존: `domain` 은 `com.groove.web`·`api` 타입은 물론 `org.springframework.web`·`jakarta.servlet` 같은 웹 프레임워크 타입에도 의존하지 않는다.
- 도메인 간 단방향(순환 없음): 슬라이스(`com.groove.(*)..`) 단위 `beFreeOfCycles` 로 양방향 순환을 금지한다(#349).

**도메인 간 순환을 끊은 방법 (#349)**

이전에는 가드 조회·시드·DTO 소유 위치에서 양방향 순환이 여럿 남아 `beFreeOfCycles` 를 켜지 못했다. 끊은 방식은 다음과 같다.

| 순환 | 끊은 방법 |
|---|---|
| `order ↔ catalog`, `catalog ↔ cart` | 앨범 삭제 가드를 `AlbumReferenceGuard`(catalog 소유) 포트로 역전 — order·cart 가 구현 |
| `order ↔ member` | 탈퇴 가드를 `MemberOrderGuard`(member 소유) 포트로 역전 — order 가 구현(차단 상태셋 소유) |
| `order ↔ coupon` | `CouponApplicationService.applyToOrder` 가 `Order` 대신 `(orderId, 총액)` 만 받고 할인액 반환; 보유목록 주문번호 조회는 `OrderNumberLookup`(coupon 소유) 포트로 역전 |
| `catalog ↔ review` | 리뷰 평점 집계를 `AlbumRatingProvider`(catalog 소유) 포트로 역전 — review 가 구현 |
| `coupon ↔ admin` | 쿠폰 생성 입력을 coupon 소유 `CouponCreateCommand` 로 — admin 컨트롤러가 자기 DTO 를 매핑 |
| `auth ↔ member` | `AuthPrincipal` 을 도메인 무관 중립 슬라이스 `com.groove.security` 로 이전(role 은 문자열) — 모든 표현 계층이 의존하되 member 역참조 제거 |
| `common → 도메인` | 도메인을 참조하는 부팅 시드(`LocalDataSeeder`·`ProductionSeedGuard`)를 `common` 밖 `com.groove.bootstrap` 으로 이전해 `common` 을 인프라 sink 로 회복 |

가드는 동기 조회라 이벤트화 대신 **데이터가 필요한 도메인이 포트를 소유하고 데이터를 가진 도메인이 구현**하는 의존성 인버전으로 풀었다(인터페이스는 owner 의 `application` 계층, 구현은 provider 의 `application` 계층).

---

## References

- [ARCHITECTURE.md](../ARCHITECTURE.md) (전체 구조)
- 코드: [`OrderService.java`](../../backend/src/main/java/com/groove/order/application/OrderService.java) (단방향 import), [`payment/gateway/`](../../backend/src/main/java/com/groove/payment/gateway/), [`common/`](../../backend/src/main/java/com/groove/common/)
- 도메인 간 협력: [domain-events-and-outbox.md](./domain-events-and-outbox.md)
- [Package by Feature, not Layer](https://phauer.com/2020/package-by-feature/)
