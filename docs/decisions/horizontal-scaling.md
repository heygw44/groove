# ADR: 수평 확장 — 스케줄러 분산락(ShedLock) · rate-limit 분산(Bucket4j-Redis)

| 항목 | 값 |
|---|---|
| 상태 | Accepted |
| 날짜 | 2026-06-30 (스케줄러 분산락 #365 · rate-limit 분산 #367) |
| 연관 이슈 | #274, #210, #164, #365, #367 (#364 수평 확장 셋업 후속) |
| 작성자 | ParkGunWoo |
| 관련 문서 | [concurrency-control.md](./concurrency-control.md), [ARCHITECTURE.md](../ARCHITECTURE.md) §10.5 · §11 · §12 |

---

## Context

앱을 로드밸런서 뒤로 수평 확장(`deploy.replicas > 1`)하면 노드 로컬 상태에 기대던 두 메커니즘이 깨진다.

- **rate-limit** — 버킷이 인스턴스 로컬 Caffeine 캐시(`common/ratelimit/RateLimitRegistry.java`)에 있다. N대로 늘리면 동일 IP/회원의 실효 한도가 N배가 되어 로그인 무차별 대입·계정 열거·비밀번호 변경·쿠폰 사재기 억제력이 인스턴스 수에 비례해 약해진다.
- **스케줄러** — 단일 `@EnableScheduling`(`common/scheduling/SchedulingConfig.java`)으로 배치 10종이 돈다. 다중화하면 각 배치가 노드마다 동시에 실행된다. 외부 부수효과가 있는 배치(결제 reconciliation·반품 환불·아웃박스 릴레이)는 `IdempotencyService`·UK 제약·상태 전이로 중복을 상당 부분 흡수하지만, 노드 간 1회 실행을 보장하는 게 정공법이다.

두 문제는 인프라 비용이 비대칭이다. 스케줄러 분산락은 기존 MySQL 로 풀 수 있어 새 인프라가 필요 없고, rate-limit 분산은 노드 간 공유 저장소(Redis)를 동반한다. 그래서 같은 수평 확장 작업이라도 둘을 독립적으로 도입했다 — 스케줄러 분산락을 먼저, rate-limit 분산을 뒤에.

---

## Decision

- **스케줄러**: ShedLock 으로 각 배치를 노드 간 1회만 실행한다. 락은 기존 MySQL 에 둔다(새 인프라 0).
- **rate-limit**: Bucket4j 의 Redis(Lettuce) 백엔드로 버킷을 노드 간 공유한다. 저장소는 `groove.rate-limit.store` 로 토글해 단일 인스턴스(base/로컬/테스트)는 Caffeine, 다중화(docker/prod)는 Redis 를 쓴다.

이는 [concurrency-control.md](./concurrency-control.md)의 "가능하면 DB 로 푼다" 방침과 일관된다. 분산락은 MySQL 로 충분하므로 Redis 를 들이지 않았고, Redis 는 rate-limit 처럼 노드 간 원자적 공유가 꼭 필요한 곳에만 도입했다.

---

## 구현

### ② 스케줄러 분산락 = ShedLock (기존 MySQL)

락을 MySQL 에 두므로 새 인프라가 필요 없다.

- **의존성**: `net.javacrumbs.shedlock:shedlock-spring` + `net.javacrumbs.shedlock:shedlock-provider-jdbc-template`. 별도 `spring-boot-starter-jdbc` 는 불필요하다 — `starter-data-jpa` 가 `spring-jdbc`(JdbcTemplate)를 전이 제공한다.
- **락 테이블**: Flyway 마이그레이션은 **반드시 순수 SQL** 로 작성한다. V20(`member` HMAC 백필)처럼 Java 마이그레이션으로 만들면 `@DataJpaTest` 슬라이스에서 resolve 되지 않는다. test 프로파일의 `out-of-order: true` + `ignore-migration-patterns: "*:missing"` 덕에 순수 SQL 추가는 슬라이스를 깨지 않는다. 컨벤션은 InnoDB / utf8mb4 / utf8mb4_unicode_ci. 마이그레이션 번호는 작업 시점에 V33 까지 진행돼 있어 **`V34__init_shedlock.sql`** 로 배정했다(설계 노트의 `V31` 예고와 어긋난 사례).

  ```sql
  CREATE TABLE shedlock (
      name       VARCHAR(64)  NOT NULL,
      lock_until TIMESTAMP(3) NOT NULL,
      locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
      locked_by  VARCHAR(255) NOT NULL,
      PRIMARY KEY (name)
  ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
  ```

- **활성화**: `SchedulingConfig` 에 `@EnableSchedulerLock` + `LockProvider` 빈. ShedLock 은 Spring Boot 4.x 호환 라인인 **7.7.0** 으로 고정한다.

  ```java
  @Bean
  LockProvider lockProvider(DataSource dataSource) {
      return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
          .withJdbcTemplate(new JdbcTemplate(dataSource))
          .usingDbTime()   // 노드 간 시계 차이 제거 — DB 시간으로 락 판정
          .build());
  }
  ```

- **각 배치에 `@SchedulerLock`**: `@Scheduled` 메서드 10개 전부에 `@SchedulerLock(name, lockAtMostFor, lockAtLeastFor)` 를 단다. 외부 부수효과가 있는 배치가 우선이다 — `PaymentReconciliationScheduler`(PG 폴링), `ClaimProgressScheduler`(PG 환불), `OutboxRelayScheduler`(at-least-once 디스패치), `ShippingProgressScheduler`(상태 전이 경합). 나머지(만료·정리·익명화·배송 보충)는 조건/`*_at IS NULL`/UK 로 멱등하지만 일관성을 위해 함께 단다. 타임아웃은 `${groove.*}` 인라인 기본값으로 외부화했다.
- **테스트 영향**: 기존 스케줄러 테스트는 메서드를 직접 호출(AOP 프록시 우회)하므로 락 어드바이스가 끼지 않아 그대로 통과한다. Testcontainers MySQL 에 `shedlock` 테이블만 있으면 컨텍스트 로딩도 정상이다.

근거: [ShedLock](https://github.com/lukas-krecan/ShedLock) — `@EnableSchedulerLock` / `@SchedulerLock` / `JdbcTemplateLockProvider(.usingDbTime())` / 표준 `shedlock` 스키마.

### ① rate-limit 분산 = Bucket4j-Lettuce (Redis)

버킷 저장소를 Bucket4j `ProxyManager<String>` 로 추상화해 두 경로를 같은 코드로 태운다. Caffeine 경로도 손수 만든 `Cache<String,Bucket>` 대신 `CaffeineProxyManager`(bucket4j-caffeine)를 써서 `getProxy(key, Supplier<BucketConfiguration>)` 한 줄로 동작한다. 토글은 `groove.rate-limit.store`(caffeine 기본 · docker/prod=redis)로, 카탈로그 캐시의 `spring.cache.type` 과 같은 방식이다.

- **인프라**: `docker-compose.yml` 에 `redis:7-alpine`(healthcheck `redis-cli ping`) + `app.depends_on`/`REDIS_HOST` 주입, base `application.yaml` 에 `spring.data.redis.host/port`, `TestcontainersConfig` 에 Redis 싱글턴 컨테이너 + `@ServiceConnection`. `build.gradle.kts` 에 bucket4j lettuce/caffeine 모듈을 추가한다. 아티팩트명은 `bucket4j_jdk17-lettuce`(+ `bucket4j_jdk17-caffeine`)다.
- **교체 지점**: 두 곳뿐이다.
  - 버킷 생성·저장 = `RateLimitRegistry` 의 Caffeine `Cache<String, Bucket>` 를 `ProxyManager` 로 교체:

    ```java
    LettuceBasedProxyManager<String> proxyManager = Bucket4jLettuce.casBasedBuilder(connection)
        .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(...))
        .build();
    Bucket bucket = proxyManager.getProxy(bucketKey, () -> bucketConfiguration);   // 키 + lazy config
    ```
  - 정책 설정 = `RateLimitPolicy.bucketFactory()` 의 반환을 `Supplier<Bucket>` → `Supplier<BucketConfiguration>` 로 바꾼다(`Bucket.builder()` → `BucketConfiguration.builder().addLimit(...)`). 정책 7종(auth·결제·주문·쿠폰 발급 등 6 + payment-webhook)과 테스트의 `Bucket.builder()` 호출이 영향받는다.
- **소비부는 거의 무변경**: `RateLimitFilter` 의 `tryConsumeAndReturnRemaining(1)` 은 분산 프록시 버킷도 같은 `Bucket` 인터페이스라 그대로 동작한다. `MatchedBucket`·`RateLimitKeyResolver`(`ip:`/`member:` 키)·`appliesTo` 경로 매칭·429 응답 포맷은 불변이다.
- **원격 장애 정책(하이브리드)**: `RateLimitPolicy.failOpen()`(기본 true)으로 갈랐다. auth·결제·주문·웹훅은 fail-open(Redis 장애 시 한도 미적용 통과 — 가용성 우선), 쿠폰 발급만 fail-closed(429 — 한정 수량 사재기 억제 우선).
- **검증**: 노드 간 공유는 `RateLimitRedisStoreTest`(단일 JVM 2-프록시), e2e 는 `loadtest/rate-limit-distributed.sh`(scale=2).

근거: [Bucket4j 분산](https://bucket4j.com/) — `Bucket4jLettuce.casBasedBuilder` / `proxyManager.getProxy(key, supplier)` / `BucketConfiguration`.

---

## Considered Options

### 스케줄러 분산락

- **ShedLock (MySQL) ✅ 채택** — 배치 메서드 단위 행 락. 기존 MySQL 에 락 테이블 하나만 추가하면 되고, Quartz 보다 경량이다.
- **Quartz 클러스터 모드 ⚠️** — 잡 스케줄링·영속화까지 제공하지만, 이미 `@Scheduled` 로 충분한 단순 배치에 스케줄러 프레임워크를 통째로 들이는 건 과하다.

### rate-limit 분산

- **Bucket4j-Lettuce (Redis) ✅ 채택** — Redis 에 버킷을 두고 CAS 로 노드 간 한도를 원자적으로 공유한다. 소비부 코드가 거의 그대로다.
- **Bucket4j-MySQL (SQL ProxyManager) ⚠️** — 기존 MySQL 을 재사용하지만, 로그인·회원가입 같은 auth 핫 패스마다 `SELECT … FOR UPDATE` DB 왕복이 생겨 rate-limit 이 핫 경로 지연·DB 부하의 원천이 된다.
- **게이트웨이/WAF 이관 ⚠️** — nginx/게이트웨이 계층에서 IP 한도를 걸면 앱에 Redis 없이도 분산 한도를 보장할 수 있다. 다만 회원 단위(`member:`) 한도는 앱이 알아야 해서, 엣지(IP)+앱(회원)으로 갈리는 하이브리드가 된다. 향후 엣지 인프라가 갖춰지면 IP 한도를 이관할 여지가 있다.

---

## 비교 요약

| 기준 | ShedLock(MySQL) | Bucket4j-Lettuce(Redis) | Bucket4j-MySQL | 게이트웨이 이관 |
|---|---|---|---|---|
| 신규 인프라 | 없음 | Redis | 없음 | 엣지 LB |
| hot path 비용 | — | 낮음 | 높음(DB 왕복) | 낮음 |
| 소비부 변경량 | 적음 | 적음 | 적음 | 회원 한도는 앱 유지 |
| 채택 | ✅ | ✅ | ✗ | 향후 보완 |

---

## Consequences

**긍정적**

다중화에서 배치가 노드 간 1회만 실행되고, rate-limit 한도가 인스턴스 수와 무관하게 유지된다. 단일 인스턴스(base/local)는 Caffeine·단일 스케줄러로 그대로 동작하므로 Redis 없이도 로컬·데모 환경이 깨지지 않는다. 스케줄러 분산락(MySQL)과 rate-limit 분산(Redis)의 비대칭 비용을 분리해, 필요할 때 한쪽만 켤 수 있다.

**부정적 / 트레이드오프**

rate-limit 분산은 Redis 의존을 더한다. `CacheErrorHandler`/`failOpen()` 으로 장애 시 fail-open|closed 를 정했지만, Redis 가용성이 보안 통제의 일부가 된다. 또 `RateLimitPolicy.bucketFactory()` 반환 타입 변경이 정책 7종과 테스트에 동시에 퍼져, 전환 시 한 번에 손대야 했다.

---

## References

- 공식: [ShedLock](https://github.com/lukas-krecan/ShedLock), [Bucket4j 분산 백엔드](https://bucket4j.com/)
- 코드: [`RateLimitRegistry.java`](../../backend/src/main/java/com/groove/common/ratelimit/RateLimitRegistry.java), [`RateLimitStoreConfig.java`](../../backend/src/main/java/com/groove/common/ratelimit/RateLimitStoreConfig.java), [`RateLimitPolicy.java`](../../backend/src/main/java/com/groove/common/ratelimit/RateLimitPolicy.java), [`SchedulingConfig.java`](../../backend/src/main/java/com/groove/common/scheduling/SchedulingConfig.java)
- 개선 노트: `docs/improvements/scheduler-shedlock.md`, `docs/improvements/rate-limit-distributed.md`, `docs/improvements/multi-instance-scaling.md`(로컬)
- 관련 결정: [concurrency-control.md](./concurrency-control.md), ARCHITECTURE [§10.5](../ARCHITECTURE.md) · §11.1 · §11.3 · §12 #2·#3·#5
