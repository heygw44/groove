# ADR: 테스트 전략 — 피라미드 + Testcontainers

| 항목 | 값 |
|---|---|
| 상태 | Accepted |
| 날짜 | 2026-06-17 |
| 연관 이슈 | #257 (W12-2 ADR 정리) |
| 작성자 | ParkGunWoo |
| 관련 문서 | [improvements/](../improvements/), [measurement/baseline.md](../measurement/baseline.md) |

---

## Context

본 프로젝트의 정확성 위험은 대부분 **DB 와 맞닿은 곳**에 있다 — 비관적 락/원자적 UPDATE 의 동시성, JPA 쿼리·인덱스, Flyway 마이그레이션, 결제 콜백의 멱등·보상. 이를 신뢰성 있게 검증하려면 테스트가 **실제 MySQL 의 락·방언과 동일한 환경**에서 돌아야 한다.

요구:
- 빠른 단위 피드백 + 실 DB 기반 통합 검증을 **둘 다** 확보.
- 동시성·멱등 같은 비결정 요소를 **결정적으로** 재현.
- 회귀 방지를 위한 **커버리지 게이트**와 성능 회귀를 잡는 **부하 측정**.

본 ADR 은 테스트 종류 구성·실 DB 전략·결정화 방법·게이트를 기록한다.

---

## Decision

**테스트 피라미드 + MySQL Testcontainers 싱글턴 + 결정적 테스트 프로파일 + JaCoCo 게이트 + k6 부하** 로 구성한다.

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

정적 싱글턴 + `.withReuse(true)` 로 **모든 테스트 클래스가 컨테이너 하나를 공유**(부팅 오버헤드 1회). `@ServiceConnection` 이 DataSource·Flyway·JPA 를 자동 연결한다.

### 3) 결정적 테스트 프로파일 (`application-test.yaml`)

- **스케줄러 비활성** — 아웃박스 릴레이/결제 폴링/쿠폰 만료/배송 진행을 `interval: PT1H`·`cron: "-"` 로 자동 실행 차단 후, 테스트에서 스케줄러 메서드를 **직접 호출**해 타이밍 비결정성 제거.
- **Mock PG 결정화** — `success-rate: 1.0`, `delay: 0ms` → [payment-gateway-mock.md](./payment-gateway-mock.md).
- **rate-limit 완화** — 다회 호출이 한도에 걸리지 않게 capacity 를 크게.
- **커넥션 풀 천장 대응** — `hikari.minimum-idle: 0` 으로 캐시된 컨텍스트가 idle 커넥션을 즉시 반납(CI 의 MySQL `max_connections` 근접·OOM-kill 방지).

### 4) 동시성 하니스

`support/ConcurrencyHarness.runConcurrently(...)` 가 N 스레드를 게이트에서 동시 출발시키고 지연을 수집해 TPS·p95 를 파생 → `CouponIssuanceConcurrencyTest`·`PaymentCallbackConcurrencyTest` 등이 공유.

### 5) 게이트 + 부하

- **JaCoCo** (`build.gradle.kts`): 핵심 도메인(auth·member·catalog·coupon·order·payment) **라인 80%** + 전체 **라인 60%**. `check` 가 트리거하므로 위반 시 빌드 실패. 부트스트랩·DTO·`package-info`·`*Properties` 만 제외.
- **k6** (`loadtest/*.js`): 선착순 쿠폰·결제 멱등·재고 복원 등 HTTP 계층 부하 측정(Before/After 자료).

---

## Considered Options

### Option A — H2 인메모리 DB ❌

| 항목 | 내용 |
|---|---|
| 방식 | 테스트만 H2 로 부팅 |
| 장점 | 빠름, 컨테이너 불필요 |
| 단점 | **MySQL 방언·락(`FOR UPDATE`)·Flyway 호환 차이** → 동시성/쿼리 검증이 운영과 어긋남(가짜 초록) |

### Option B — 단위 테스트 위주 + DB 모킹 ⚠️

| 항목 | 내용 |
|---|---|
| 방식 | 리포지토리를 Mockito 로 스텁 |
| 장점 | 매우 빠름 |
| 단점 | 실제 쿼리·인덱스·동시성·트랜잭션 경계 **미검증** — 본 프로젝트 위험의 핵심을 놓침 |

### Option C — Testcontainers 피라미드 ✅ (채택)

| 항목 | 내용 |
|---|---|
| 방식 | 단위+슬라이스+통합, 실 MySQL 싱글턴 재사용 |
| 장점 | 운영과 동일 DB 로 락·쿼리·마이그레이션·동시성까지 검증, 컨테이너 1회 부팅 |
| 한계 | 컨테이너 부팅 비용·CI 커넥션 천장 관리 필요 |

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
- 비관적 락·원자적 UPDATE·멱등·보상 같은 **DB 밀착 로직을 운영과 동일한 MySQL 로 검증**(가짜 초록 회피).
- 싱글턴 재사용으로 다수 통합 테스트에서도 컨테이너 부팅이 1회.
- 결정적 프로파일 + `ConcurrencyHarness` 로 동시성 테스트가 CI 에서 재현 가능.
- JaCoCo 게이트가 회귀를 빌드 단계에서 차단.

**부정적 / 트레이드오프**
- **컨테이너 부팅 비용**: 첫 실행 시 이미지 pull·부팅 지연(로컬은 `withReuse` 로 완화, Docker 필요).
- **CI 커넥션 천장**: 캐시된 `@SpringBootTest` 컨텍스트들이 Hikari idle 커넥션을 점유해 MySQL `max_connections` 에 근접할 수 있음 → `hikari.minimum-idle: 0` 으로 대응(러너 특정 이슈, 로컬 미재현).
- **로컬 Docker 의존**: Docker/OrbStack 미인식 시 대량 실패 → `DOCKER_HOST`·`TESTCONTAINERS_RYUK_DISABLED` 환경 설정 필요.

---

## References

- 코드: [`support/TestcontainersConfig.java`](../../backend/src/test/java/com/groove/support/TestcontainersConfig.java), [`support/ConcurrencyHarness.java`](../../backend/src/test/java/com/groove/support/ConcurrencyHarness.java), [`application-test.yaml`](../../backend/src/main/resources/application-test.yaml), [`build.gradle.kts`](../../backend/build.gradle.kts) (JaCoCo 룰)
- 부하 측정: [`loadtest/`](../../loadtest/), [measurement/baseline.md](../measurement/baseline.md)
- [Testcontainers for Java](https://java.testcontainers.org/), [Spring Boot — Testcontainers](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html)
