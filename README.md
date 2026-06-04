# Groove

[![CI](https://github.com/heygw44/groove/actions/workflows/ci.yml/badge.svg)](https://github.com/heygw44/groove/actions/workflows/ci.yml)

LP 전문 이커머스 백엔드 — 인증, 카탈로그, 주문/결제/배송 전 흐름을 담은 Spring Boot 포트폴리오 프로젝트.

> 핵심 흐름(W1~W7)과 확장(쿠폰 M13 · Vue 프론트 M15) 완료. 종합 측정·문서화(W8~W12)는 진행 예정.

## 기술 스택

| 분류 | 사용 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 4.0 |
| Security | Spring Security + JWT ([JJWT](docs/decisions/jwt-library.md)) |
| DB | MySQL 8 + Flyway |
| ORM | Spring Data JPA (Hibernate) |
| Build | Gradle (Kotlin DSL) |
| Infra | Docker Compose |
| Test | JUnit 5, Testcontainers, JaCoCo |

## 빠른 시작

```bash
# 1. 환경변수 설정
cp .env.example .env
# .env 파일에서 DB_PASSWORD, JWT_SECRET 수정

# 2. 실행 (Docker 필요)
docker-compose up -d

# 3. 헬스체크
curl http://localhost:8080/actuator/health
```

> IDE / 터미널에서 `./gradlew bootRun`(백엔드는 `backend/` 하위, #131)으로 직접 실행하면 **local 프로파일이 자동 주입**된다(`build.gradle.kts`):
> ```bash
> cd backend
> cp src/main/resources/application-local.yaml.example src/main/resources/application-local.yaml
> ./gradlew bootRun   # SPRING_PROFILES_ACTIVE 미설정 시 자동으로 local
> ```
> 그러면 `DB_PASSWORD` / `JWT_SECRET` 환경 변수가 없어도 dev fallback 으로 부팅된다. 다른 프로파일로 띄우려면
> `SPRING_PROFILES_ACTIVE=docker ./gradlew bootRun` 또는 `./gradlew bootRun --args='--spring.profiles.active=docker'`.
> 운영(docker) 프로파일은 환경 변수가 반드시 주입되어야 기동된다 — 부팅 시 누락은 의도된 fail-fast.
> 또한 application.yaml 에 프로파일 기본값 폴백이 없어 미설정 시 `default`(데모 시드 비활성)로 뜨며, 비-local
> 프로파일로 기동 중 DB 에서 데모 계정이 감지되면 `ProductionSeedGuard` 가 기동을 중단한다(이슈 #128).
>
> ⚠️ IDE 에서 `GrooveApplication` 의 main() 을 bootRun 없이 직접 실행할 때는 폴백이 없으므로 run config 의
> 활성 프로파일을 `local` 로 지정해야 한다 — 미지정 시 `default` 로 떠 Mock PG 빈 부재로 부팅에 실패한다.

> 시드 데이터 주입은 W8 완료 후 `./scripts/seed.sh` 추가 예정.

## 프로젝트 문서

| 문서 | 내용 |
|------|------|
| [docs/PRD.md](docs/PRD.md) | 제품 요구사항 |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 시스템 아키텍처 |
| [docs/ERD.md](docs/ERD.md) | 데이터 모델 |
| [docs/API.md](docs/API.md) | API 명세 |
| [docs/MILESTONE.md](docs/MILESTONE.md) | 마일스톤 & 이슈 가이드 |

## 성능·동시성 개선 사례

단일 행 동시 갱신의 **lost-update** 를 도메인 특성에 맞는 제어로 해소한 사례.

- **선착순 쿠폰 발급 — Before→After 완료 (#90·#93)**: 락 없는 베이스라인은 lost-update(`issued_count 24 ≠ 발급 48`) + 락 경합 실패 84%(`CannotAcquireLockException`)로 붕괴. **원자적 조건부 UPDATE**(`WHERE issued_count < total_quantity`)로 전환해 250 VU 스파이크(6,500+ 요청)에도 **정확히 100장, 초과발급 0**(k6 HTTP 실측). → [트러블슈팅](docs/troubleshooting/coupon-issuance-concurrency.md) · [loadtest/](loadtest/)
- **재고 차감 오버셀 — Before 박제 (#46)**: 락 없는 재고 차감의 동형(同型) 결함을 baseline 으로 보존. 비관적 락 적용(After)은 W10 예정. → [트러블슈팅](docs/troubleshooting/overselling-baseline.md)

> **같은 lost-update, 두 도메인, 두 제어** — 재고는 비관적 락, 쿠폰은 원자적 조건부 UPDATE 로 해소해 제어 기법 선택의 트레이드오프를 대비시킨다.

## 진행 현황

```
핵심 로드맵: ▓▓▓▓▓▓▓░░░░░ W1~W7 완료 (G2 통과)
확장:        쿠폰(M13) · Vue 프론트(M15) 완료
```

**현재 단계**: 핵심 흐름(회원가입~결제~배송~리뷰) 완료 + 확장(쿠폰·프론트) 완료. 다음은 **W8~W12 — 시드·k6 측정(G3)·CS 개선·문서화(G4)**.

- W3: 인프라/스켈레톤 (G1 게이트 통과)
- W4: 인증/회원 (회원가입·로그인·JWT·Refresh Rotation·Rate Limit·JaCoCo 80% 게이트)
- W5: 카탈로그 (관리자 CRUD + Public 목록/상세/검색 + 의도적 N+1 보존 — W10 시연 자료)
- W6: 장바구니 + 주문 (회원/게스트, 재고 차감 — 오버셀 baseline 박제)
- W7: 결제 + 배송 + 리뷰 (Mock PG·멱등성·관리자 주문 조작 — **G2 게이트 통과**)
- 확장 M13: 쿠폰 (선착순 발급 동시성 + k6 Before/After)
- 확장 M15: Vue 3 데모 프론트엔드 (전 기능 시연 UI)
- 남은: W8 시드/통합 · W9 측정(G3) · W10~W11 개선 · W12 문서화(G4)

GitHub [Milestones](https://github.com/heygw44/groove/milestones) 페이지에서 상세 진행률 확인 가능.
