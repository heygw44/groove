# GROOVE — LP(바이닐) 전문 이커머스

Java 17 / Spring Boot 3.5 + React 18 풀스택 포트폴리오 프로젝트.
한정반(Limited Drop) 선착순 판매의 동시성 제어, JPA + MyBatis 역할 분담, JWT 무상태 인증, Toss Payments 결제, AWS 배포까지 다룬다.

## 기술 스택

| 영역 | 스택 |
|---|---|
| Backend | Java 17, Spring Boot 3.5, Spring Security 6.5, Spring Data JPA, MyBatis 3.0, Spring Data Redis, springdoc-openapi |
| Frontend | React 18, TypeScript, Vite, React Router v6, TanStack Query v5, Zustand, Tailwind CSS |
| Infra | MySQL 8.0, Redis 7, Docker Compose, GitHub Actions, AWS EC2 + DuckDNS + Nginx + Let's Encrypt |

## 로컬 실행

```bash
# 1. 인프라 (MySQL → localhost:3307, Redis → localhost:6380)
docker compose up -d

# 2. 백엔드 (http://localhost:8080, Swagger: /swagger-ui.html)
cd backend && ./gradlew bootRun

# 3. 프론트엔드 (http://localhost:5173)
cd frontend && npm install && npm run dev
```

백엔드까지 컨테이너로 띄우려면 `docker compose --profile full up --build`.

## 테스트

```bash
cd backend && ./gradlew test   # 통합 테스트는 Testcontainers(MySQL/Redis) 사용, Docker 필요
```

## 구조

```
groove/
├── backend/      # Spring Boot (com.groove.{global, member, auth, product, ...})
├── frontend/     # React + Vite
├── docker-compose.yml
└── .github/workflows/ci.yml
```
