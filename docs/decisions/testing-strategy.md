# ADR: 테스트 전략 — 피라미드 + Testcontainers

| 항목 | 값 |
|---|---|
| 상태 | Accepted |
| 날짜 | 2026-06-17 |
| 연관 이슈 | #257 |
| 작성자 | ParkGunWoo |
| 관련 문서 | [payment-gateway-mock.md](./payment-gateway-mock.md) |

---

## Context

이 프로젝트에서 정확성이 깨질 만한 자리는 거의 다 DB 와 맞닿아 있다. 비관적 락과 원자적 UPDATE 의 동시성, JPA 쿼리와 인덱스, Flyway 마이그레이션, 결제 콜백의 멱등과 보상 — 전부 실제 DB 의 동작에 의존한다. 이런 걸 믿을 만하게 검증하려면 테스트가 운영과 같은 MySQL 의 락·방언 위에서 돌아야 한다. H2 로 초록불을 봐 봐야 운영에서 깨지면 의미가 없다.

그래서 테스트 전략에 바라는 건 분명했다. 빠른 단위 피드백과 실 DB 기반 통합 검증을 둘 다 갖출 것, 동시성·멱등 같은 비결정적 요소를 결정적으로 재현할 것, 그리고 회귀를 막아 줄 커버리지 게이트와 성능 회귀를 잡아 줄 부하 측정을 둘 것.

---

## Decision

테스트 피라미드를 MySQL Testcontainers 위에 올리고, 결정적 테스트 프로파일과 JaCoCo 게이트, k6 부하를 더한다.

### 1) 피라미드 3종

| 종류 | 애너테이션 | 용도 |
|---|---|---|
| 단위 | `@ExtendWith(MockitoExtension.class)` | 순수 로직·리스너·계산 (DB 없음) |
| 슬라이스 | `@DataJpaTest` + `@Import(TestcontainersConfig)` | 리포지토리 쿼리·인덱스·매핑 |
| 통합/E2E | `@SpringBootTest` + `@Import(TestcontainersConfig)` | HTTP→서비스→DB 전 흐름, 동시성 |

### 2) 실 DB = Testcontainers 싱글턴 재사용

```java
// support/TestcontainersConfig.java
private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
        .withDatabaseName("groove").withUsername("groove").withPassword("test")
        .withReuse(true);
static { MYSQL.start(); }

@Bean @ServiceConnection
public MySQLContainer<?> mysqlContainer() { return MYSQL; }
```

정적 싱글턴에 `.withReuse(true)` 를 더해 모든 테스트 클래스가 컨테이너 하나를 공유한다. 부팅 비용을 한 번만 치르는 셈이다. `@ServiceConnection` 이 DataSource·Flyway·JPA 를 컨테이너에 알아서 연결해 준다.

### 3) 결정적 테스트 프로파일 (`application-test.yaml`)

비결정성의 원천을 하나씩 제거한다. 아웃박스 릴레이·결제 폴링·쿠폰 만료·배송 진행 같은 스케줄러는 `interval: PT1H`·`cron: "-"` 로 자동 실행을 막아 두고, 테스트에서 필요한 순간에 스케줄러 메서드를 직접 호출한다. Mock PG 는 `success-rate: 1.0`, `delay: 0ms` 로 고정한다(→ [payment-gateway-mock.md](./payment-gateway-mock.md)). rate-limit 은 capacity 를 크게 잡아 다회 호출이 한도에 걸리지 않게 하고, `hikari.minimum-idle: 0` 으로 캐시된 컨텍스트가 idle 커넥션을 곧바로 반납하게 해서 CI 의 MySQL `max_connections` 근접과 OOM-kill 을 피한다.

### 4) 동시성 하니스

`support/ConcurrencyHarness.runConcurrently(...)` 가 N 개의 스레드를 게이트에 모았다가 동시에 출발시키고 각 요청의 지연을 모아 TPS 와 p95 를 뽑아낸다. `CouponIssuanceConcurrencyTest`·`PaymentCallbackConcurrencyTest` 등이 이걸 공유한다.

### 5) 게이트 + 부하

JaCoCo 게이트(`build.gradle.kts`)는 핵심 도메인(auth·member·catalog·coupon·order·payment)에 라인 80%, 전체에 라인 60% 를 요구한다. `check` 가 이 검증을 끌고 가므로 임계값을 못 넘기면 빌드가 멈춘다. 부트스트랩·DTO·`package-info`·`*Properties` 만 측정에서 빠진다. 여기에 k6(`loadtest/*.js`)로 선착순 쿠폰·결제 멱등·재고 복원 같은 흐름을 HTTP 계층에서 부하 측정해 Before/After 자료로 남긴다.

---

## Considered Options

### Option A — H2 인메모리 DB ❌

테스트만 H2 로 띄우는 방식이다. 빠르고 컨테이너도 필요 없다. 그런데 MySQL 의 방언과 `FOR UPDATE` 락, Flyway 호환성이 H2 와 달라서, 정작 검증하고 싶은 동시성과 쿼리가 운영과 어긋난다. 가짜 초록불의 위험이 크다.

### Option B — 단위 테스트 위주 + DB 모킹 ⚠️

리포지토리를 Mockito 로 스텁해서 도는 방식이라 더없이 빠르다. 하지만 실제 쿼리·인덱스·동시성·트랜잭션 경계가 전혀 검증되지 않는다. 하필 이 프로젝트 위험의 핵심이 거기 있다.

### Option C — Testcontainers 피라미드 ✅ (채택)

단위·슬라이스·통합을 갖추되 실 MySQL 을 싱글턴으로 재사용한다. 운영과 같은 DB 위에서 락·쿼리·마이그레이션·동시성까지 검증되고, 컨테이너는 한 번만 부팅한다. 대신 컨테이너 부팅 비용과 CI 커넥션 천장을 관리해야 한다.

---

## 비교 요약

| 기준 | A H2 | B DB 모킹 | **C Testcontainers** |
|---|---|---|---|
| 운영 DB 충실도 | 낮음 | — | **높음** |
| 동시성/락 검증 | ✗ | ✗ | **✓** |
| 속도 | 빠름 | 최고 | **중(싱글턴 완화)** |
| 마이그레이션 검증 | 부분 | ✗ | **✓** |

---

## Consequences

**긍정적**

비관적 락·원자적 UPDATE·멱등·보상처럼 DB 에 밀착한 로직을 운영과 같은 MySQL 로 검증하니 가짜 초록불을 피할 수 있다. 싱글턴 재사용 덕에 통합 테스트가 많아도 컨테이너 부팅은 한 번뿐이다. 결정적 프로파일과 `ConcurrencyHarness` 가 있어 동시성 테스트도 CI 에서 재현되고, JaCoCo 게이트가 회귀를 빌드 단계에서 끊는다.

**부정적 / 트레이드오프**

대가도 있다. 첫 실행에는 이미지를 받고 컨테이너를 띄우는 지연이 붙는다(로컬은 `withReuse` 로 완화하지만 Docker 는 있어야 한다). CI 에서는 캐시된 `@SpringBootTest` 컨텍스트들이 Hikari idle 커넥션을 쥐고 있다가 MySQL `max_connections` 에 근접할 수 있어 `hikari.minimum-idle: 0` 으로 눌렀는데, 이건 러너에서만 나타나고 로컬에서는 재현되지 않는 이슈였다. 로컬에서 Docker 나 OrbStack 이 인식되지 않으면 테스트가 무더기로 깨지므로, `DOCKER_HOST` 와 `TESTCONTAINERS_RYUK_DISABLED` 환경 설정이 필요하다.

---

## References

- 코드: [`support/TestcontainersConfig.java`](../../backend/src/test/java/com/groove/support/TestcontainersConfig.java), [`support/ConcurrencyHarness.java`](../../backend/src/test/java/com/groove/support/ConcurrencyHarness.java), [`application-test.yaml`](../../backend/src/main/resources/application-test.yaml), [`build.gradle.kts`](../../backend/build.gradle.kts) (JaCoCo 룰)
- 부하 측정: [`loadtest/`](../../loadtest/)
- [Testcontainers for Java](https://java.testcontainers.org/), [Spring Boot — Testcontainers](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html)
