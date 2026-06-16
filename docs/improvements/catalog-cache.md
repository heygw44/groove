# 카탈로그 조회 캐시 — Caffeine + TTL·무효화·스탬피드 대응 (#236)

> 이슈 [#236](https://github.com/heygw44/groove/issues/236) · 마일스톤 M16(Flow Hardening) · 도메인 catalog/common
> 설계 배경(왜 Redis 대신 Caffeine): `docs/portfolio/caffeine-cache-adr.md`(로컬, gitignore)
> 선행: [#203 N+1 제거](./n-plus-one.md) · [#235/#244 keyset 커버링 인덱스](./keyset-index-coverage.md)

## 1. 문제 정의

상품 상세/목록 등 **읽기 경로에 캐시가 없다**. 카탈로그는 읽기 비중이 높아(상세·랜딩이 핫경로) 동일 응답을
요청마다 DB 왕복으로 재생성한다. #203(N+1 제거)·#235/#244(keyset 커버링 인덱스)로 쿼리 단가는 이미 낮췄지만,
가장 반복적인 **앨범 상세 단건**과 **공개 기본 랜딩 첫 페이지**는 캐시로 DB 왕복 자체를 제거할 수 있다.

## 2. 설계

| 결정 | 선택 | 근거 |
|---|---|---|
| 캐시 제공자 | **Caffeine(in-process)** | Redis 는 #210 에서 컷. 단일 인스턴스라 분산 캐시 불요 — ADR 참조 |
| 자동구성 | `spring-boot-starter-cache` → `CaffeineCacheManager` | context7 확인(Spring Boot 4.0 caching). `spring.cache.caffeine.spec` 로 size/TTL 주입 |
| 캐시 범위 | 상세(`albumDetail`, key=id) + **공개 기본 랜딩만**(`albumLandingList`, 단일 엔트리) | 필터/커서 검색은 조합 폭발로 적중률↓·무효화↑ → 제외(keyset 은 이미 인덱스로 충분) |
| 스탬피드 | `@Cacheable(sync=true)` | Caffeine 키별 단일 로딩(single-flight). 전역 `CacheLoader`/`refreshAfterWrite`(모든 캐시 일괄)보다 메서드 단위로 적합 |
| 정합성 | **짧은 TTL(`expireAfterWrite=60s`) + admin 쓰기만 즉시 evict** | catalog 밖(주문·리뷰) 변경은 TTL 로 수렴. 캐시는 표시용, 결제는 비관락 재검증이라 오버셀 불가 |
| 트랜잭션 순서 | `@EnableCaching(order = LOWEST_PRECEDENCE - 1)` | 캐시 advice 를 `@Transactional` 바깥에 — **적중 시 DB 트랜잭션을 아예 열지 않음** |

**랜딩 가드**(`AlbumCaches.isLandingRequest`): 필터 전무 + `status=SELLING` + 기본 페이지(page0, size20,
`createdAt DESC`)일 때만 단일 키로 캐시한다. 필터 검색·비기본 정렬·admin 목록(status≠SELLING)은 가드
불일치로 자연히 캐시를 우회한다 — 잘못된 키 충돌 없이 핫경로 하나만 캐시.

설정(application.yaml, `${ENV:default}` 로 운영 튜너블):
```yaml
spring.cache:
  type: caffeine
  cache-names: albumDetail,albumLandingList
  caffeine.spec: ${CACHE_ALBUM_CAFFEINE_SPEC:maximumSize=2000,expireAfterWrite=60s,recordStats}
```

## 3. 무효화 정책

| 변경 경로 | 캐시 처리 | 비고 |
|---|---|---|
| `create`(등록) | `albumLandingList` 전체 clear | 신규 앨범이 랜딩에 등장 가능. 상세 엔트리는 아직 없음 |
| `update`(수정) | `albumDetail[id]` evict + `albumLandingList` clear | |
| `adjustStock`(재고) | 〃 | summary 에 stock 포함되므로 랜딩도 clear |
| `delete`(삭제) | 〃 | 이후 상세 조회는 404(stale 서빙 금지) |
| 주문 결제 차감(`OrderService`) | **evict 안 함 → 60s TTL 로 수렴** | 결제는 `findByIdForUpdate` 비관락으로 재검증(표시값만 stale) |
| 재고 복원(`StockRestorer` 벌크 UPDATE) | 〃 | 영속성 컨텍스트 우회 경로라 의도적으로 TTL 수렴 |
| 리뷰 작성(review 도메인) | 〃 | 평점·리뷰 수는 60s 내 수렴 |

> catalog 밖 도메인(order/review)에 evict 훅을 박지 않은 것은 **결합 최소화**를 위한 의도적 선택이다.
> 더 엄밀한 즉시 정합성이 필요하면 해당 경로에 `@CacheEvict` 또는 `CacheManager` 직접 호출을 추가할 수
> 있으나, 표시용 캐시 + 짧은 TTL + 결제 비관락 재검증 조합으로 오버셀·과금 오류는 발생하지 않는다.

## 4. 결정적 증거 (환경 독립적 — `AlbumCacheTest`)

절대 p95(§5)는 측정 환경(특히 OrbStack amd64 에뮬레이션, [search-index.md §4.2](./search-index.md) 참조)에
종속되므로, 캐시 효과의 **신뢰 가능한 증거는 DB 왕복 제거 자체**다 — `AlbumCacheTest` 가 코드로 고정한다.

| 검증 | 단언 |
|---|---|
| 상세 적중 | `findDetail(id)` 2회 호출 → `albumRepository.findById(id)` **1회만**(2회차 캐시 서빙) |
| 랜딩 적중 | `search(landing)` 2회 → `findAll(spec, pageable)` **1회만** |
| 상세 evict | `update`/`adjustStock` 후 재조회 시 변경값 즉시 반영, `delete` 후 404 |
| 랜딩 evict | `create` 후 총건수 정확히 +1(미evict 면 캐시값 고정) |
| 랜딩 가드 | 필터(`artistId`) 검색 2회 → `findAll` **2회**(캐시 우회) |

즉 적중 경로는 본 쿼리(findById/findAll)를 **0회** 실행하고(§2 의 트랜잭션 순서로 트랜잭션도 미개시),
admin 쓰기는 즉시 무효화된다. 이는 머신 성능과 무관한 결정적 사실이다.

## 5. Before / After 측정 절차 (HTTP 레벨)

k6 `loadtest/catalog-cache.js` 가 상세(`/albums/{id}`)·랜딩(`/albums`) 핫경로의 p95(`detail_latency`/
`landing_latency`)를 잰다. 같은 스크립트를 **캐시 OFF vs ON** 앱에 돌려 Before/After 를 박제한다.

```bash
# (공통) MySQL + 시드 기동은 loadtest/README.md §실행 절차 참고. 적중률 메트릭 노출은 env 로 주입한다
# (application-local.yaml 은 gitignore 라 머신 종속 — 아래 env 로 재현 가능하게 둔다).
export MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,caches,metrics

# Before — 캐시 OFF
SPRING_CACHE_TYPE=none ./gradlew bootRun     # (또는 캐시 도입 전 커밋)
k6 run loadtest/catalog-cache.js             # detail_latency / landing_latency p95 = Before

# After — 캐시 ON (기본)
./gradlew bootRun
k6 run loadtest/catalog-cache.js             # = After. teardown 이 cache.gets hit/miss·적중률 로그
```

| 지표 | Before (캐시 OFF) | After (캐시 ON) |
|---|---|---|
| detail p95 (`/albums/{id}`) | _(실행 시 기록)_ | _(실행 시 기록, 캐시 적중으로 DB 왕복 제거)_ |
| landing p95 (`/albums`) | _(실행 시 기록)_ | _(실행 시 기록)_ |
| cache.gets 적중률 | n/a(캐시 없음) | _(teardown 로그: `hitRatio=…%`)_ |

> 적중률은 워밍 후 동일 키(고정 핫 id + 단일 랜딩)에 집중되므로 높게 수렴한다. 절대 p95 는 환경 종속이라,
> 캐시의 핵심 효과는 §4 의 "적중 시 DB 왕복 0회" 로 본다(검색 인덱스 개선에서 EXPLAIN 을 우선한 것과 동형).

## 6. 주의 / 메모

- **stale 허용 범위**: 주문 결제·재고 복원·리뷰 작성이 바꾸는 stock·평점은 최대 `expireAfterWrite`(60s)
  동안 stale 가능. 표시용이며 결제 확정은 비관락 재검증이라 정합성 사고는 없음. TTL 은 `CACHE_ALBUM_CAFFEINE_SPEC` 로 조정.
- **단일 spec 공유**: 두 캐시가 같은 spec 을 쓴다(랜딩은 핫엔트리 1개라 무방). 캐시별 TTL 분리가 필요하면
  커스텀 `CaffeineCacheManager` 빈으로 확장.
- **테스트 격리**: 기본은 `spring.cache.type=none`(공유 Testcontainers DB 교차오염 방지). 캐시 동작 검증
  (`AlbumCacheTest`)만 `caffeine` 으로 override.
- **후속 옵션**: `@CacheEvict` 는 메서드 성공 후·커밋 직전 실행이라 admin 저동시성에선 무해하나, 엄밀화가
  필요하면 `TransactionSynchronization` 으로 AFTER_COMMIT evict 로 전환 가능.
