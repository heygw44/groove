<div align="center">

# Groove

**LP(바이닐) 음반을 파는 온라인 셀렉트샵 백엔드**

[![CI](https://github.com/heygw44/groove/actions/workflows/ci.yml/badge.svg)](https://github.com/heygw44/groove/actions/workflows/ci.yml)

</div>

---

## 소개

LP(바이닐) 음반을 다루는 온라인 셀렉트샵입니다. 회원가입과 상품 탐색, 장바구니, 주문, 결제, 배송, 리뷰까지 이커머스의 한 흐름을 실제로 이어 붙였습니다.

LP라는 소재를 고른 데는 이유가 있습니다. 한정반, 단일 재고, 음반마다 따라붙는 메타데이터 같은 특성이 동시성이나 검색 같은 주제를 자연스럽게 불러옵니다. 한정반 한 장에 주문이 몰릴 때 재고가 어떻게 어긋나는지, 5만 장짜리 카탈로그에서 키워드 검색이 왜 느려지는지 같은 문제를 일부러 찾지 않아도 마주치게 됩니다.

그래서 화면에 보이는 기능만큼이나 그 아래에서 벌어지는 일에 초점을 뒀습니다. 동작하는 흐름을 먼저 만든 뒤, 부하를 줘서 깨지는 곳을 찾고 수치로 확인하며 고쳤습니다.

## 기술 스택

**Backend**

![Java](https://img.shields.io/badge/Java_21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_4.0-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security_7-6DB33F?style=flat-square&logo=springsecurity&logoColor=white)
![JPA](https://img.shields.io/badge/Spring_Data_JPA-59666C?style=flat-square&logo=hibernate&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL_8.4-4479A1?style=flat-square&logo=mysql&logoColor=white)
![Flyway](https://img.shields.io/badge/Flyway-CC0200?style=flat-square&logo=flyway&logoColor=white)

**Infra / Quality**

![Gradle](https://img.shields.io/badge/Gradle-02303A?style=flat-square&logo=gradle&logoColor=white)
![Docker](https://img.shields.io/badge/Docker_Compose-2496ED?style=flat-square&logo=docker&logoColor=white)
![JUnit5](https://img.shields.io/badge/JUnit_5-25A162?style=flat-square&logo=junit5&logoColor=white)
![Testcontainers](https://img.shields.io/badge/Testcontainers-291A38?style=flat-square&logo=testcontainers&logoColor=white)
![k6](https://img.shields.io/badge/k6-7D64FF?style=flat-square&logo=k6&logoColor=white)

**Frontend (시연용)**

![Vue](https://img.shields.io/badge/Vue_3-4FC08D?style=flat-square&logo=vuedotjs&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-646CFF?style=flat-square&logo=vite&logoColor=white)
![Tailwind](https://img.shields.io/badge/Tailwind_CSS-06B6D4?style=flat-square&logo=tailwindcss&logoColor=white)

## 주요 기능

- **회원 / 인증** — JWT 로그인(Access + Refresh Token Rotation), 탈퇴 시 개인정보 익명화
- **카탈로그 / 검색** — 동적 검색, FULLTEXT 키워드 검색, keyset(커서) 페이징
- **주문 / 결제** — 회원·게스트 주문, 재고 비관적 락, Mock PG 비동기 결제 + 멱등성 처리
- **배송 / 리뷰** — 결제 완료 후 배송 자동 진행, 배송 완료 후 리뷰 작성
- **쿠폰** — 선착순 원자적 발급, 할인 적용/복원
- **관리자** — 상품·주문·쿠폰 관리, 재고 조정

## 시스템 구조

외부 의존(PG·메일·운송장)을 모두 in-process Mock으로 처리하는 단일 인스턴스 구조입니다.

```mermaid
flowchart TB
    subgraph clients["클라이언트 · 검증 도구"]
        Vue["Vue 3 SPA"]
        Bruno["Bruno"]
        k6["k6 부하 테스트"]
    end

    subgraph app["Spring Boot App · 단일 JAR (Docker Compose)"]
        direction TB
        Sec["Security · JWT · Rate Limit"]
        Domains["도메인 모듈<br/>auth · member · catalog · cart · order<br/>payment · shipping · review · coupon"]
        MockPG["Mock PG<br/>(비동기 웹훅)"]
        Sched["Scheduler<br/>결제 폴링 · 배송 진행"]
    end

    DB[("MySQL 8.4<br/>+ Flyway")]

    clients -->|"HTTP /api/v1"| Sec
    Sec --> Domains
    Domains --> MockPG
    MockPG -.->|"웹훅 콜백"| Domains
    Sched --> Domains
    Domains --> DB
```

## 실행 방법

```bash
git clone https://github.com/heygw44/groove.git
cd groove

# .env 의 비밀번호·시크릿을 고유값으로 교체
# (플레이스홀더 그대로면 기동이 거부됩니다)
cp .env.example .env

# MySQL + 앱(단일 JAR) 기동 — Docker 필요
docker-compose up -d

# 헬스체크
curl http://localhost:8080/actuator/health

# API 문서 (Swagger UI)
# docker 프로파일은 기본 비공개 — .env 에 SPRINGDOC_ENABLED=true 설정 시 노출
open http://localhost:8080/swagger-ui.html
```
