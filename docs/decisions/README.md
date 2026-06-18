# 아키텍처 의사결정 기록 (ADR)

프로젝트를 진행하며 내린 주요 기술 결정을 ADR(Architecture Decision Record)로 모아 둔 곳이다. 코드만 봐서는 잘 드러나지 않는 "무엇을 골랐고(Decision), 어떤 대안을 왜 버렸으며(Considered Options), 그 대신 어떤 트레이드오프를 받아들였는지(Consequences)" 를 각 문서가 기록한다.

## 공통 형식

| 섹션 | 내용 |
|---|---|
| 메타테이블 | 상태 · 날짜 · 연관 이슈 · 작성자 · 관련 문서 |
| Context | 배경 · 문제 · 제약 |
| Decision | 채택한 결정 (근거 코드 인용) |
| Considered Options | 검토한 대안들 (✅ 채택 / ⚠️ 보류 / ❌ 탈락) |
| 비교 요약 | 기준별 옵션 비교표 |
| Consequences | 긍정적 효과 / 부정적·트레이드오프 |
| References | 코드 경로 · 관련 문서 · 외부 링크 |

## 목록

| ADR | 주제 | 상태 |
|---|---|---|
| [payment-gateway-mock.md](./payment-gateway-mock.md) | PG 연동 모킹 — 인터페이스 + 콜백형 Mock(실 PG 교체 가능) | Accepted |
| [concurrency-control.md](./concurrency-control.md) | 동시성 제어(상위) — 원자적 UPDATE·비관적 락·원자적 가산 혼용, Redis 미도입 | Accepted |
| [horizontal-scaling.md](./horizontal-scaling.md) | 수평 확장 대비 — 스케줄러 분산락(ShedLock/MySQL)·rate-limit 분산(Bucket4j-Redis) 유보 + 실행 설계 | Accepted (유보) |
| [coupon-concurrency.md](./coupon-concurrency.md) | 선착순 쿠폰 발급 동시성 — 베이스라인→비관적 락→원자적 조건부 UPDATE | Accepted |
| [domain-events-and-outbox.md](./domain-events-and-outbox.md) | 비동기 처리 — 인메모리 이벤트 + Outbox(vs Kafka/RabbitMQ) | Accepted |
| [package-structure.md](./package-structure.md) | 패키지 구조 — 도메인 우선 + 경량 레이어(vs 레이어 우선/헥사고날) | Accepted |
| [testing-strategy.md](./testing-strategy.md) | 테스트 전략 — 피라미드 + MySQL Testcontainers + JaCoCo 게이트 | Accepted |
| [jwt-library.md](./jwt-library.md) | JWT 라이브러리 선정 — JJWT(vs Nimbus/OAuth2 Resource Server) | Accepted |
| [seed-data.md](./seed-data.md) | 시드 데이터 출처 및 수급 — Discogs(vs Faker) | Accepted |

> 성능 개선의 Before/After 측정은 [`../improvements/`](../improvements/), 동시성 결함 재현 베이스라인은 [`../troubleshooting/`](../troubleshooting/) 에 있다. 캐시 선택(Caffeine vs Redis)은 [`../improvements/catalog-cache.md`](../improvements/catalog-cache.md) 를 참고하면 된다.
