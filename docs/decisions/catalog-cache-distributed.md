# ADR: 카탈로그 캐시 분산 전환 — 멀티 인스턴스 Caffeine→Redis

| 항목 | 값 |
|---|---|
| 상태 | Accepted |
| 날짜 | 2026-06-30 |
| 연관 이슈 | #366 (PR #377) · 선행 #236(Caffeine 도입)·#375(멀티 인스턴스 토대) |
| 작성자 | ParkGunWoo |
| 관련 문서 | [ARCHITECTURE.md](../ARCHITECTURE.md) §5.8(ADR-14·17) · [horizontal-scaling.md](./horizontal-scaling.md) · [concurrency-control.md](./concurrency-control.md) |

---

## Context

[#236](../ARCHITECTURE.md)에서 카탈로그 read(앨범 상세·기본 랜딩)에 **Caffeine 로컬(in-process) 캐시**를
도입했다(ADR-14). 단일 인스턴스 전제(ARCHITECTURE §1 비목표: 분산 시스템)에선 evict 와 read 가 같은 힙이라
완전하다.

[#375](./horizontal-scaling.md)로 수평 확장 토대(Redis·app 다중화·nginx 라운드로빈 LB)를 깔자 전제가 깨졌다.
app 을 N대로 늘리면 **Caffeine 캐시가 노드마다 독립**이다. admin write(`update`/`adjustStock`/`delete`)는
`@CacheEvict` 로 **요청을 받은 노드의 Caffeine 만** 비운다 — nginx LB 가 read 를 다른 노드로 보내면 그 노드의
로컬 캐시는 비워지지 않아 **최대 TTL(60s) 동안 stale** 한 stock·가격·상태를 서빙한다.

정합성 사고는 아니다 — 결제 확정은 `findByIdForUpdate` 비관락 재검증([#205](./concurrency-control.md))이라
표시값이 stale 해도 오버셀·과금 오류는 없다. 문제는 **표시 일관성**(노드마다 다른 재고가 보이는 UX)이다.
ARCHITECTURE §11.1 은 이 상황("카탈로그 캐시 멀티 인스턴스 일관성 필요")을 Redis 전환 트리거로 미리 명시했고,
#375 로 트리거가 실제로 떴다.

---

## Decision

**카탈로그 캐시 저장소를 `spring.cache.type` 으로 토글한다 — base/local=Caffeine, docker/prod 다중화=Redis 분산.**
코드는 Spring Cache 추상화(`@Cacheable`/`@CacheEvict`)라 무변경, yaml 토글만으로 전환한다.

- **RedisCacheManager 는 Boot 자동구성**에 맡긴다. `RedisCacheConfiguration` 빈을 정의하면 `spring.cache.redis.*`
  yaml 이 무시되므로(빈 우선), **TTL·key-prefix 는 yaml 단일 소스**로 둔다(`CacheConfig` 는 `CacheErrorHandler` 만 제공).
- **직렬화는 Boot 기본 JDK**. `Page`=`PageImpl` 은 JSON 역직렬화가 깨지므로, 캐시 대상 DTO record
  (`AlbumDetailResponse`·`AlbumSummaryResponse`)를 `Serializable` + `serialVersionUID` 로 만든다.
- **key-prefix `groove:cache:v1:`** — `v1` 은 직렬화 스키마 버전. 호환이 깨지면 `v2` 로 올려 롤링 배포 중
  cross-version 역직렬화를 키 공간 수준에서 격리한다.
- **장애 강등(`CacheErrorHandler`)**: Redis 장애·역직렬화 실패를 **`get`→DB 폴백(미스 강등), `put`→무시,
  `evict`/`clear`→전파(throw)** 로 처리한다. 캐시가 read 가용성의 단일점이 되지 않게 하되(get/put fail-open),
  무효화 실패는 조용히 삼키지 않는다(evict/clear fail-loud — stale 을 운영에 표면화).
- **prod 가드**: prod 는 `type: redis` 고정(env override 금지) — 잘못된 값 하나로 node-local 부팅 시 노드 간
  무효화가 깨진다. rate-limit 의 `RateLimitStoreProdGuard`(#367)와 동형 의도.

```yaml
# application-docker.yaml (prod 는 type: redis 고정, loadtest 토글 없음)
spring:
  cache:
    type: ${SPRING_CACHE_TYPE:redis}   # base=caffeine, docker/prod=redis
    redis:
      time-to-live: 60s
      key-prefix: "groove:cache:v1:"
      use-key-prefix: true
```

---

## Considered Options

### A — Caffeine 로컬 유지(노드 간 stale 감수) ❌

전환 비용 0 이지만, 다중화 시 admin write 후 다른 노드가 최대 TTL(60s) 동안 stale 을 서빙한다. ARCHITECTURE
§11.1 이 명시한 트리거가 이미 떴으므로 유지는 결정 회피다.

### B — TTL 을 0 에 가깝게 낮춰 stale 창 축소 ⚠️

로컬 캐시를 유지하되 TTL 을 매우 짧게 둔다. stale 창은 줄지만 캐시 효용(적중률)도 같이 무너져 캐시를 두는 의미가
사라진다. 근본 해결(노드 간 무효화 전파)이 아니다.

### C — Redis 분산 캐시 토글 ✅ (채택)

저장소만 Redis 로 바꿔 노드 간 `@CacheEvict` 를 일관시킨다. 코드 무변경(Spring Cache 추상화), 단일 인스턴스는
Caffeine 유지(데모는 Redis 불요). rate-limit 분산(#367)·#375 토대와 같은 토글 패턴이라 운영 모델이 일관된다.

### D — 무캐시로 회귀 ❌

분산 일관성 문제는 사라지나 #236 이 흡수한 read 부하가 되돌아온다. 핫경로(상세·랜딩) DB 왕복이 복원돼 후퇴다.

---

## 비교 요약

| 기준 | A Caffeine 유지 | B TTL 축소 | **C Redis 토글** | D 무캐시 |
|---|---|---|---|---|
| 노드 간 무효화 일관 | ✗(최대 60s stale) | △(stale 축소) | **✓** | ✓(캐시 없음) |
| 캐시 효용(적중률) | ✓ | ✗ | **✓** | ✗ |
| 코드 변경 | 없음 | 없음 | **없음(yaml 토글)** | 제거 |
| 신규 인프라 | 없음 | 없음 | **Redis(#375 기도입)** | 없음 |
| 단일 인스턴스 영향 | — | TTL 후퇴 | **없음(caffeine 유지)** | read 부하 복원 |

---

## Consequences

**긍정적**

다중화에서 admin write 가 노드 간 즉시 일관되게 반영된다(노드 직접 타깃 측정 `loadtest/cache-consistency.sh`
Before=stale/After=즉시 일관). 코드는 무변경이라 #236 의 `AlbumCacheTest`(DB 왕복 0회)가 그대로 회귀 가드로
남고, 단일 인스턴스 데모는 Caffeine 으로 Redis 없이 동작한다. rate-limit(#367)·캐시(#366)가 같은 토글 패턴이라
운영 일관성이 높다.

**부정적 / 트레이드오프**

Redis 의존이 read 경로에 들어온다 — `CacheErrorHandler` 로 get 실패를 DB 폴백시켜 가용성 단일점은 피했지만,
Redis 장애 시 DB 부하가 캐시 적중분만큼 복원된다. 직렬화가 JDK 라 DTO 스키마 변경 시 `serialVersionUID`
불일치로 역직렬화가 깨질 수 있는데, 이는 미스 강등 + key-prefix `v1→v2` 격리로 흡수한다. `sync=true`
single-flight 는 노드 로컬이라 노드 간 동시 미스는 각자 한 번씩 로딩한다(표시용이라 무해). 분산락(Redisson)은
여전히 미도입 — 스케줄러는 ShedLock(MySQL, [horizontal-scaling.md](./horizontal-scaling.md)), 한정반은 MySQL
비관락([#205](./concurrency-control.md))으로 충분하다는 판단을 유지한다.

---

## References

- 코드: [`CacheConfig.java`](../../backend/src/main/java/com/groove/common/cache/CacheConfig.java), [`AlbumDetailResponse.java`](../../backend/src/main/java/com/groove/catalog/album/api/dto/AlbumDetailResponse.java), [`AlbumSummaryResponse.java`](../../backend/src/main/java/com/groove/catalog/album/api/dto/AlbumSummaryResponse.java), `application-docker.yaml`·`application-prod.yaml`(`spring.cache`)
- 측정: `loadtest/cache-consistency.sh`(scale=2 노드 직접 타깃), 개선 노트 `docs/improvements/redis-cache.md`(로컬)
- 관련 결정: [horizontal-scaling.md](./horizontal-scaling.md)(#375 토대·#367 rate-limit 분산), [concurrency-control.md](./concurrency-control.md)(비관락 재검증), ARCHITECTURE [§5.8](../ARCHITECTURE.md)·§11.1·§12 #5(ADR-14·ADR-17)
