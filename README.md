# Groove

[![CI](https://github.com/heygw44/groove/actions/workflows/ci.yml/badge.svg)](https://github.com/heygw44/groove/actions/workflows/ci.yml)

LP 전문 이커머스 백엔드 — 인증, 카탈로그, 주문/결제/배송 전 흐름을 담은 Spring Boot 포트폴리오 프로젝트.

> 상세 성능 개선 사례 및 시연 자료는 W12 완성 후 추가 예정.

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

> IDE / 터미널에서 `./gradlew bootRun` 으로 직접 실행하려면 (local 프로파일):
> ```bash
> cp src/main/resources/application-local.yaml.example src/main/resources/application-local.yaml
> ```
> 그러면 `DB_PASSWORD` / `JWT_SECRET` 환경 변수가 없어도 dev fallback 으로 부팅된다.
> 운영(docker) 프로파일은 환경 변수가 반드시 주입되어야 기동된다 — 부팅 시 누락은 의도된 fail-fast.

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
Progress: ▓▓▓▓▓░░░░░░░ 5/12 weeks
```

**현재 단계**: W5 카탈로그(Album/Artist/Genre/Label) 완료 → W6 장바구니/주문 진입 직전.

- W3: 인프라/스켈레톤 (G1 게이트 통과)
- W4: 인증/회원 (회원가입·로그인·JWT·Refresh Rotation·Rate Limit·E2E·JaCoCo 80% 게이트)
- W5: 카탈로그 (관리자 CRUD + Public 목록/상세/검색 + 의도적 N+1 보존 — W10 시연 자료)

GitHub Milestones 페이지에서 상세 진행률 확인 가능.
