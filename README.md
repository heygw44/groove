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
# 1. 인프라 (MySQL → localhost:3306, Redis → localhost:6379)
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

## 성능

상품 목록 API(`GET /api/v1/products`)를 k6로 50 VU, 30초 동안 부하를 걸어 p95 응답 시간을 측정한다. 목표는 p95 300ms 이하다. `bootRun`은 C1 JIT만 쓰는 옵션(`-XX:TieredStopAtLevel=1`)이 붙어 실제보다 느리게 나오므로 jar로 실행해 측정한다.

```bash
docker compose up -d
cd backend && ./gradlew bootJar -x test
SPRING_PROFILES_ACTIVE=local SPRING_JPA_SHOW_SQL=false LOGGING_LEVEL_COM_GROOVE=INFO LOGGING_LEVEL_ORG_HIBERNATE_SQL=INFO \
  java -jar backend/build/libs/groove-backend-0.0.1-SNAPSHOT.jar
k6 run --summary-export scripts/k6/results/product-list.json scripts/k6/product-list.js
```

측정 조건

| 항목 | 값 |
|---|---|
| 환경 | Apple M4, 16GB, macOS (앱·DB·k6 동일 머신) |
| DB/Redis | MySQL 8.0 / Redis 7 (Docker) |
| 데이터 | local 프로파일 시드 상품 51건 (목록 노출 50건) |
| 로깅 | show-sql·DEBUG OFF |
| 부하 | 50 VU, 30s, 요청 간 sleep 100ms |
| 워밍업 | 10 VU × 10s 1회 후 측정 |

결과

| 지표 | 값 |
|---|---|
| 총 요청 수 | 13,853 (약 461 req/s) |
| 실패율 | 0% |
| p50 | 7.1ms |
| p95 | 15.4ms |
| p99 | 23.4ms |
| 목표 충족 | 충족 (300ms 대비 약 5%) |

요청 변형별 p95

| 요청 | p95 |
|---|---|
| list-default | 15.3ms |
| list-page | 14.9ms |
| list-keyword | 15.3ms |
| list-genre | 15.3ms |
| list-artist | 14.5ms |
| list-price | 15.5ms |
| list-sort | 15.6ms |
| list-combined | 15.8ms |

같은 조건으로 두 번 측정했고 p95는 15.5ms·15.4ms로 차이가 없었다. 데이터가 50건이라 필터 종류에 따른 차이는 드러나지 않았고, 대부분의 시간은 쿼리보다 요청 처리 오버헤드다. 데이터 규모를 키운 재측정은 운영 환경 부하 테스트에서 다룬다.

## 구조

```
groove/
├── backend/      # Spring Boot (com.groove.{global, member, auth, product, ...})
├── frontend/     # React + Vite
├── scripts/k6/   # 부하 테스트 스크립트
├── docker-compose.yml
└── .github/workflows/ci.yml
```
