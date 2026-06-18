# ADR: 수평 확장 대비 — 스케줄러 분산락 · rate-limit 분산 (유보)

| 항목 | 값 |
|---|---|
| 상태 | Accepted (유보 — 트리거 도달·구현 시 Superseded) |
| 날짜 | 2026-06-18 |
| 연관 이슈 | #274 (#270 §10.6 에서 분리), #210, #164 |
| 작성자 | ParkGunWoo |
| 관련 문서 | [concurrency-control.md](./concurrency-control.md), [ARCHITECTURE.md](../ARCHITECTURE.md) §10.5 · §11 · §12 |

---

## Context

현 배포는 **단일 인스턴스를 전제**한다(ARCHITECTURE §1 비목표: 분산 시스템). 단일 인스턴스면 무해하지만, 로드밸런서 뒤로 수평 확장(`deploy.replicas > 1`)하면 두 메커니즘이 무력화된다.

- **rate-limit** — 버킷이 인스턴스 로컬 Caffeine 캐시(`common/ratelimit/RateLimitRegistry.java`)에 저장된다. N대로 늘리면 동일 IP/회원의 실효 한도가 N배가 되어 로그인 무차별 대입·계정 열거·비밀번호 변경·쿠폰 사재기 억제력이 인스턴스 수에 비례해 약화된다.
- **스케줄러** — 단일 `@EnableScheduling`(`common/scheduling/SchedulingConfig.java`)으로 배치 10종이 구동된다. 다중화하면 각 배치가 노드마다 동시에 돈다.

다만 두 가지 사실이 "지금 당장 손대야 하는가"의 답을 가른다.

1. **도입 트리거가 발생하지 않았다.** 운영은 단일 인스턴스이고, rate-limit "한도 N배"는 다중 인스턴스에서만 생기는 문제다(§10.5). 즉 현재 환경에 존재하지 않는 문제다.
2. **스케줄러 중복의 현실 위험은 이미 멱등 안전망이 막는다.** 다중화의 유일한 단일-인스턴스 근접 시나리오는 롤링 배포 중 잠깐 두 인스턴스가 겹치는 구간인데, 외부 부수효과가 있는 배치(결제 reconciliation·반품 환불·아웃박스 릴레이)는 `IdempotencyService`·UK 제약·상태 기반 전이로 중복을 이미 흡수한다(자세히는 §12 #2 보충, [concurrency-control.md](./concurrency-control.md)).

이 프로젝트는 [concurrency-control.md](./concurrency-control.md)에서 "**새 인프라 없이 DB 로 푼다**"는 방침을 세웠고, 분산락(Redis)은 **#210에서 한계 효용이 낮아 NOT_PLANNED 로 컷**했다(ARCHITECTURE §12 #5·§15). §12 도입부는 이런 확장·운영 강화를 "**측정된 필요가 생길 때까지 의도적으로 유보**"한다고 명문화한다. 본 문서는 #274가 다루는 수평 확장 대비를 같은 규율 위에서 한 결정으로 못 박고, **트리거가 떴을 때 즉시 실행할 설계를 공식 문서 근거와 함께** 남긴다 — 누락이 아니라 의식적 유보임을 코드 없이 기록한다.

---

## Decision

**지금은 유보한다.** ShedLock·분산 rate-limit 어느 것도 구현하지 않는다.

- **도입 트리거**: 수평 확장 결정(`deploy.replicas > 1`) 또는 측정된 부하/보안 요구. 그 전까지는 단일 인스턴스 + 기존 멱등 안전망으로 충분하다.
- **근거**: ① 트리거 미발생(존재하지 않는 문제) ② Redis 미도입 규율(#210 NOT_PLANNED, §12) ③ 스케줄러 중복의 현실 위험은 멱등 안전망이 이미 커버 — 즉 ShedLock조차 현 시점 한계 효용이 낮다.

트리거가 발생하면 아래 "실행 설계"대로 옮긴다. 두 작업은 인프라 비용이 비대칭이다 — **스케줄러 분산락은 기존 MySQL 로(새 인프라 0), rate-limit 분산은 Redis 도입을 동반**한다. 그래서 둘은 같은 이슈로 묶였더라도 **서로 다른 트리거에서 독립적으로** 도입될 수 있다(스케줄러 다중화 먼저, rate-limit 분산은 더 뒤).

---

## 실행 설계 (트리거 도달 시)

### ② 스케줄러 분산락 = ShedLock (기존 MySQL, 새 인프라 0)

§12 #2·§11.3이 지정한 정공법. 락을 **기존 MySQL** 에 두므로 새 인프라가 필요 없다.

- **의존성**: `net.javacrumbs.shedlock:shedlock-spring` + `net.javacrumbs.shedlock:shedlock-provider-jdbc-template`. 별도 `spring-boot-starter-jdbc` 불필요 — `starter-data-jpa`가 `spring-jdbc`(JdbcTemplate)를 전이 제공한다.
- **락 테이블**: Flyway `V31__init_shedlock.sql` — **반드시 순수 SQL** 로 작성한다. V20(`member` HMAC 백필)처럼 Java 마이그레이션으로 만들면 `@DataJpaTest` 슬라이스에서 resolve 안 되는 함정에 빠진다. 기존 test 프로파일의 `out-of-order: true` + `ignore-migration-patterns: "*:missing"` 덕에 순수 SQL 추가는 슬라이스를 깨지 않는다. 컨벤션은 InnoDB / utf8mb4 / utf8mb4_unicode_ci.

  ```sql
  CREATE TABLE shedlock (
      name       VARCHAR(64)  NOT NULL,
      lock_until TIMESTAMP(3) NOT NULL,
      locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
      locked_by  VARCHAR(255) NOT NULL,
      PRIMARY KEY (name)
  ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
  ```

- **활성화**: `SchedulingConfig`(현재 `@EnableScheduling` 위치)에 `@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")` 추가 + `LockProvider` 빈.

  ```java
  @Bean
  LockProvider lockProvider(DataSource dataSource) {
      return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
          .withJdbcTemplate(new JdbcTemplate(dataSource))
          .usingDbTime()   // 노드 간 시계 차이 제거 — DB 시간으로 락 판정
          .build());
  }
  ```

- **각 배치에 `@SchedulerLock`**: `@Scheduled` 메서드 10개 전부에 `@SchedulerLock(name, lockAtMostFor, lockAtLeastFor)`. **우선순위 高 = 외부 부수효과 보유분** — `PaymentReconciliationScheduler`(PG 폴링), `ClaimProgressScheduler`(PG 환불), `OutboxRelayScheduler`(at-least-once 디스패치), `ShippingProgressScheduler`(상태 전이 경합). 나머지(만료·정리·익명화·배송 보충)는 조건/`*_at IS NULL`/UK로 멱등하지만 일관성을 위해 함께 단다.
- **테스트 영향 최소**: 기존 스케줄러 테스트는 메서드를 **직접 호출**(AOP 프록시 우회)하므로 락 어드바이스가 끼지 않아 그대로 통과한다. Testcontainers MySQL에 `shedlock` 테이블만 있으면 컨텍스트 로딩도 정상.

근거: [ShedLock](https://github.com/lukas-krecan/ShedLock) — `@EnableSchedulerLock` / `@SchedulerLock` / `JdbcTemplateLockProvider(.usingDbTime())` / 표준 `shedlock` 스키마.

### ① rate-limit 분산 = Bucket4j-Lettuce (Redis) — 대안: 게이트웨이/WAF 이관

§10.5·§11.1·§11.3이 지정한 경로. 이쪽이 **Redis 신규 인프라 도입 트리거**다.

- **인프라**: `docker-compose.yml`에 `redis:7-alpine`(healthcheck `redis-cli ping`) + `app.depends_on`/`REDIS_HOST` 주입, base `application.yaml`에 `spring.data.redis.host/port`, `TestcontainersConfig`에 Redis 싱글턴 컨테이너 + `@ServiceConnection` 추가. `build.gradle.kts`에 bucket4j redis/lettuce 모듈 추가.
- **추상화 경계(조사로 확정)**: 교체 지점은 두 곳뿐이다.
  - 버킷 생성·저장 = `RateLimitRegistry`의 Caffeine `Cache<String, Bucket>` + `buckets.get(key, k -> factory.get())`. 이를 `LettuceBasedProxyManager`로 교체:

    ```java
    LettuceBasedProxyManager<String> proxyManager = Bucket4jLettuce.casBasedBuilder(connection)
        .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(...))
        .build();
    Bucket bucket = proxyManager.getProxy(bucketKey, () -> bucketConfiguration);   // 키 + lazy config
    ```
  - 정책 설정 = `RateLimitPolicy.bucketFactory(): Supplier<Bucket>` → `Supplier<BucketConfiguration>`(로컬 `Bucket.builder()` → `BucketConfiguration.builder().addLimit(...)`). **6개 정책 + 단위/통합 테스트의 `Bucket.builder()` 호출 전부 영향**.
- **거의 무변경**: 소비부 `RateLimitFilter`의 `tryConsumeAndReturnRemaining(1)`은 분산 프록시 버킷도 동일 `Bucket` 인터페이스라 그대로 동작한다(원격 장애 시 fallback/fail-open|closed 정책만 신규 결정). `MatchedBucket`·`RateLimitKeyResolver`(`ip:`/`member:` 키)·`appliesTo` 경로 매칭·429 응답 포맷은 불변.
- **대안**: nginx/게이트웨이/WAF 계층 rate limit 으로 이관하면 앱에서 Redis를 들이지 않고도 분산 환경 한도를 보장할 수 있다(엣지에서 IP 한도, 회원 단위는 앱 유지 등 하이브리드).

근거: [Bucket4j 분산](https://bucket4j.com/) — `LettuceBasedProxyManager`(`Bucket4jLettuce.casBasedBuilder`) / `proxyManager.getProxy(key, supplier)` / `BucketConfiguration`.

---

## Considered Options

### A — 지금 둘 다 구현(Redis 도입) ❌

트리거가 발생하지 않은 상태에서 Redis까지 들여 양쪽을 완성한다. 분산 역량은 시연되지만, 존재하지 않는 문제를 위한 과투자이고 #210 NOT_PLANNED·§12 유보 규율을 정면으로 뒤집는다. 인터페이스 변경·테스트 대거 수정·운영 복잡도가 따라온다.

### B — rate-limit을 Redis 없이 MySQL 분산 ⚠️

Bucket4j의 SQL 기반 ProxyManager로 기존 MySQL에 버킷을 둔다. "새 인프라 0" 철학과는 일관되지만, 로그인·회원가입 같은 **auth hot path마다 `SELECT … FOR UPDATE` DB 왕복**이 생겨 rate-limit이 핫 경로 지연·DB 부하의 원천이 된다. 문서가 예고한 정공법(Lettuce/Redis)과도 어긋난다.

### C — ShedLock만 지금 구현 ⚠️

작업②만 MySQL 기반으로 먼저 한다. 새 인프라 0·저비용이라 여지는 있으나, 단일 인스턴스엔 락 걸 상대가 없고 롤링 배포 overlap은 이미 멱등 안전망이 막아 **현 시점 한계 효용이 낮다**. "측정된 필요 전 유보" 기준에 비추면 아직이다.

### D — 유보 + 실행설계 문서화 ✅ (채택)

코드/인프라는 두지 않고, 트리거·유보 근거·양쪽 실행 설계를 본 ADR로 못 박는다. 비용 0, 유보 서사 일관, 트리거가 뜨면 이 문서대로 즉시 실행 가능. #274는 "결정 기록 + 실행 준비 완료"로 종결한다.

---

## 비교 요약

| 기준 | A 둘 다(Redis) | B rate-limit MySQL | C ShedLock만 | **D 유보+설계** |
|---|---|---|---|---|
| 현 시점 필요(트리거) | 없음 | 없음 | 낮음 | **해당없음** |
| 신규 인프라 | Redis | 없음 | 없음 | **없음** |
| hot path 비용 | 낮음 | 높음(DB 왕복) | — | **없음** |
| 유보 규율 일관 | ✗ | △ | △ | **✓** |
| 트리거 시 실행 속도 | — | — | — | **빠름(설계 완비)** |

---

## Consequences

**긍정적**

새 인프라·코드 없이 #274를 종결하면서, 트리거가 떴을 때 옮길 경로를 양쪽 모두 공식 문서 근거와 함께 확보했다. "단일 인스턴스 가정은 누락이 아니라 범위 결정"이라는 프로젝트 서사가 일관되게 유지되고, 스케줄러 분산락(MySQL)과 rate-limit 분산(Redis)의 비대칭 비용·독립 트리거가 명시돼 향후 도입 순서 판단이 쉬워진다.

**부정적 / 트레이드오프**

설계만 적어 두면 실제 구현 시점에 코드와 드리프트할 수 있다 — ShedLock·Bucket4j의 API나 본 코드의 추상화 경계(`RateLimitPolicy` 인터페이스 등)가 바뀌면 이 문서를 갱신·재검증해야 한다. 또한 트리거가 떴을 때 rate-limit 분산은 인터페이스 변경 + 테스트 대거 수정이 일시에 필요하므로, 그 시점의 작업량은 작지 않다(본 문서가 그 범위를 미리 한정해 둔다). 도입이 실제로 이뤄지면 이 ADR은 Superseded로 전환한다.

---

## References

- 공식: [ShedLock](https://github.com/lukas-krecan/ShedLock), [Bucket4j 분산 백엔드](https://bucket4j.com/)
- 코드: [`RateLimitRegistry.java`](../../backend/src/main/java/com/groove/common/ratelimit/RateLimitRegistry.java), [`RateLimitPolicy.java`](../../backend/src/main/java/com/groove/common/ratelimit/RateLimitPolicy.java), [`RateLimitFilter.java`](../../backend/src/main/java/com/groove/common/ratelimit/RateLimitFilter.java), [`SchedulingConfig.java`](../../backend/src/main/java/com/groove/common/scheduling/SchedulingConfig.java)
- 관련 결정: [concurrency-control.md](./concurrency-control.md) Option E(Redis 분산락 미래안), ARCHITECTURE [§10.5](../ARCHITECTURE.md) · §11.1 · §11.3 · §12 #2·#3·#5
