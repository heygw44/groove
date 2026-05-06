# Groove

LP 전문 이커머스 백엔드 — 인증, 카탈로그, 주문/결제/배송 전 흐름을 담은 Spring Boot 포트폴리오 프로젝트.

> 상세 성능 개선 사례 및 시연 자료는 W12 완성 후 추가 예정.

## 기술 스택

| 분류 | 사용 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 4.0 |
| Security | Spring Security + JWT (JJWT) |
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

> 시드 데이터 주입은 W8 완료 후 `./scripts/seed.sh` 추가 예정.

## 프로젝트 문서

| 문서 | 내용 |
|------|------|
| [docs/PRD.md](docs/PRD.md) | 제품 요구사항 |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 시스템 아키텍처 |
| [docs/ERD.md](docs/ERD.md) | 데이터 모델 |
| [docs/API.md](docs/API.md) | API 명세 |
| [docs/MILESTONE.md](docs/MILESTONE.md) | 마일스톤 & 이슈 가이드 |

## 진행 현황

```
Progress: ▓▓░░░░░░░░░░ 2/12 weeks
```

GitHub Milestones 페이지에서 상세 진행률 확인 가능.
