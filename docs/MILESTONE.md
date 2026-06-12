# 마일스톤 & 이슈 가이드: Groove

| 항목 | 값 |
|---|---|
| 버전 | 1.3 (W8~W11 완료 동기화) |
| 작성일 | 2026-05-05 |
| 최종 수정일 | 2026-06-12 (W8~W11 완료 반영 — M8(#69·#139~#143)·M9(#192~#197)·M10(#203~#206)·M11(#207~#211) 이슈 전부 머지/종료. §1 표 W8·W10·W11 ✅, §3 W8-1/2/4/5·W9·W10·W11 체크 갱신, 검색인덱스 V6→**V21** 정정, Redis(#210)·Virtual Threads(#211)는 측정 우선순위 밀려 NOT_PLANNED(컷), §6 진행 바·M8~M11 closed 정책 갱신) — 이전: 2026-06-10 (#197 W9-6 — W10/W11 개선 이슈 9건 발급 GH #203~#211, §3 헤더에 발급 번호 매핑·#W9-6 완료 갱신) — 2026-06-10 (G3 측정 게이트 통과 — #196 베이스라인 정산: `docs/measurement/baseline.md` 5종 통합 표·문제 4건·W10~W11 계획 확정, §2 G3 체크리스트·진행표·#W9-5 갱신) — 이전: 2026-06-05 (W8 정합성 동기화: #W8-4 Postman·#W8-5 README 산출물 저장소 확인 → 부분 완료(◐)로 갱신. 잔여=전체 엔드포인트·Rate Limit 엣지·응답검증 / 아키텍처 다이어그램 임베드 / #W8-3 대규모 시드) |
| 진행 기간 | 12주 (단독, 전업) + 확장(쿠폰·프론트) |
| 주당 가용 시간 | 35~45시간 |
| 표기 방식 | 상대 (W1 ~ W12) + 확장 M13~M15 |
| 관련 문서 | PRD.md, ARCHITECTURE.md, ERD.md, API.md, glossary.md |

---

## 0. 문서 사용 가이드

본 문서는 **GitHub Milestones + Issues** 운영을 위한 작업 분해 명세서입니다.

### 0.1 GitHub 셋업 권장
1. **Milestone 12개 생성**: `M1 - W1 Design 1` ~ `M12 - W12 Documentation`
2. **Labels** 사전 등록 (§0.2)
3. **Issue Template** 1개 등록 (§0.4)
4. 이 문서의 각 이슈 블록을 그대로 GitHub Issue로 복사

### 0.2 Label 체계

**type (필수 1개)**
| 라벨 | 색상 권장 | 용도 |
|---|---|---|
| `type:feature` | 초록 | 새 기능 구현 |
| `type:infra` | 파랑 | 인프라·셋업·DevOps |
| `type:test` | 보라 | 테스트 코드 |
| `type:improvement` | 노랑 | 측정 후 개선 작업 (W10+) |
| `type:docs` | 회색 | 문서 |
| `type:chore` | 흰색 | 리팩토링, 의존성 업데이트 |

**domain (선택, 도메인성 작업에만)**
`auth`, `member`, `catalog`, `cart`, `order`, `payment`, `shipping`, `review`, `admin`, `common`

**effort (필수)**
| 라벨 | 의미 |
|---|---|
| `S` | ≤4시간 |
| `M` | 0.5~1일 |
| `L` | 1~2일 |
| `XL` | 2일+ (가능하면 쪼갤 것) |

**priority (선택)**
| 라벨 | 의미 |
|---|---|
| `priority:blocker` | 다른 이슈를 막고 있음 |
| `priority:optional` | 시간 여유 시 진행 |

### 0.3 브랜치 / PR 규칙
- 브랜치명: `feat/{이슈번호}-{짧은-설명}` (예: `feat/12-jwt-provider`)
- PR 제목에 `Closes #N` 포함 → 머지 시 이슈 자동 종료
- 1 이슈 = 1 PR 원칙. 작업이 커지면 이슈를 쪼갬

### 0.4 Issue Template 권장

```markdown
## 목적
(1~2문장)

## 작업 내용
- [ ] 항목 1
- [ ] 항목 2

## 완료 조건 (DoD)
- [ ] 조건 1
- [ ] 조건 2

## 선행 / 관련
- Depends on: #
- Related: #

## 비고
```

---

## 1. 전체 일정 한눈에

| 단계 | 주차 | 핵심 산출물 | 게이트 | 상태 |
|---|---|---|---|---|
| 설계 | W1~W2 | PRD·아키텍처·ERD·API 명세 확정 | — | ✅ 완료 |
| 인프라/스켈레톤 | W3 | Docker Compose, 패키지·Security 베이스 | **G1** | ✅ 통과 |
| 도메인 구현 | W4~W7 | 인증→카탈로그→장바구니/주문→결제/배송/리뷰 | **G2** | ✅ 통과 (2026-05-13) |
| 통합/시드 | W8 | 통합 테스트, 시드 데이터, API 컬렉션(Bruno) | — | ✅ 완료 (M8 #69·#139~#143 — 커버리지 게이트·단위 테스트·5만건 시드·Bruno 63/63·README 다이어그램) |
| 측정 | W9 | k6 시나리오, 베이스라인, 문제 식별 | **G3** | ✅ 통과 (2026-06-10, M9 #192~#197) |
| CS 개선 | W10~W11 | N+1·인덱스·동시성·멱등성 + Before/After | — | ✅ 완료 (M10 #203~#206 · M11 #207~#209; Redis #210·VT #211 측정 후 컷) |
| 문서화 | W12 | README, 트러블슈팅, 시연 자료 | **G4** | ⏳ 예정 (M12 — 유일한 잔여 로드맵) |
| 확장: 쿠폰 | M13 | 쿠폰 시스템 + 선착순 동시성 + k6 Before/After | — | ✅ 완료 (~2026-06-01) |
| 확장: 데모 프론트 | M14 | 정적 Bootstrap 시연 UI | — | ✅ 완료 → M15 로 대체 |
| 확장: Vue 프론트 | M15 | Vue 3 + Vite 전 기능 시연 UI | — | ✅ 완료 (2026-06-03) |

> **로드맵 분기**: 본 문서의 W1~W12 로드맵에서 W1~W7(핵심 흐름)을 완료(G2 통과)한 뒤, **확장 도메인으로 쿠폰(M13)과 데모 프론트엔드(M14 바닐라 → M15 Vue 전환)** 를 먼저 진행했다. 이후 통합/시드(W8)·측정(W9, G3)·CS 개선(W10~W11)까지 모두 완료했고, **남은 로드맵은 문서화/시연(W12, G4) 단 하나**다. 쿠폰 확장이 그중 DoD #4(k6)·#5(Before/After) 일부를 선충족했다.
>
> **확장 마일스톤**: M13 상세 이슈는 §3 끝의 「확장 — 쿠폰 시스템」 절([plans/coupon-system.md](plans/coupon-system.md)). M14/M15 프론트엔드는 백엔드 시연용 UI로, M14(정적 Bootstrap)를 폐기하고 M15(Vue 3 + Vite + Pinia + Tailwind, History 라우팅 → `SpaForwardConfig`, node-gradle 통합 빌드)로 재구축했다.

---

## 2. 마일스톤 게이트 (자가 점검)

각 게이트는 **다음 단계 진입 가능 여부**를 판단하는 시점이다.

### G1 — 인프라 게이트 (W3 말) ✅ 통과 (2026-04 무렵)
- [x] `docker-compose up -d` 한 번으로 앱 + MySQL 정상 기동
- [x] `/actuator/health` 200 OK 응답
- [x] Spring Security FilterChain 동작 (보호된 엔드포인트 401 응답 확인)
- [x] Flyway 초기 마이그레이션 적용됨
- [x] GlobalExceptionHandler가 ProblemDetail로 응답

### G2 — 기능 완성 게이트 (W7 말) ★ 가장 중요 — ✅ 통과 (2026-05-13)
- [x] 회원가입 → 로그인 → 토큰 갱신 동작
- [x] 상품 검색·필터·페이징 동작
- [x] 장바구니 → 주문 → 결제 → 웹훅 → 배송 → 리뷰 E2E 흐름 통과 (`FullPurchaseJourneyE2ETest`)
- [x] 게스트 주문 흐름 동작
- [x] 관리자 상품 조작 동작 (Album/Artist/Genre/Label CRUD) / 관리자 **주문** 조작(조회·강제 전환·환불)은 #69 로 분리, PR #71 에서 완료
- [x] 결제 실패·보상 트랜잭션 동작

> 참고: W7 마일스톤 이슈(#W6-4 ~ #W7-8)에는 관리자 주문 조작이 포함돼 있지 않았다. PRD §6.9 기준 핵심 기능이라 #69 로 신규 등록해 W8 진입 직전에 완료.

### G3 — 측정 게이트 (W9 말) ✅ 통과 (2026-06-10)
- [x] k6 시나리오 4개 작성 및 동작 (search·order·payment·flash-sale, +coupon 보너스)
- [x] 모든 시나리오의 베이스라인 수치 기록 (`docs/measurement/baseline.md` §1~§2)
- [x] 발견된 문제 3건 이상 명시 (오버셀·N+1·풀스캔·5xx 4건, §3)
- [x] W10~W11 개선 작업 계획 확정 (§4)

### G4 — 최종 게이트 (W12 말)
- [ ] PRD §11 산출물 정의(DoD) 모든 항목 충족
- [ ] 측정 데이터 기반 Before/After 비교 사례 1건 이상 README 기재
- [ ] 모든 설계 문서 최신화 완료

---

## 3. 주차별 이슈 목록

각 이슈는 **GitHub Issue로 그대로 복사 가능**한 형태로 작성되어 있다. 선행(Depends on) 표기는 같은 주차 내 이슈 또는 이전 주차 이슈를 가리킨다.

---

## W1 — 설계 1단계 (M1)

**주차 목표**: 도메인 모델 식별 완료, ERD 초안

### #W1-1 [docs] 도메인 용어집(Glossary) 작성
**라벨**: `type:docs`, `S`

**목적**: 프로젝트 내 도메인 용어 정의 일원화

**작업 내용**
- [ ] `docs/glossary.md` 신규 작성
- [ ] 핵심 도메인 용어 정의 (Album, Artist, Genre, Label, Cart, Order, Payment, Shipping, Review)
- [ ] 상태 enum 의미 정리 (OrderStatus, PaymentStatus, ShippingStatus 등)
- [ ] 영문/한글 표기 통일 규칙

**완료 조건**
- [ ] 모든 핵심 도메인이 1줄 이상 정의됨
- [ ] PRD/ERD에서 사용된 용어와 일치 검증

---

### #W1-2 [docs] ERD 초안 검토 + 인덱스 전략 확정
**라벨**: `type:docs`, `S`

**목적**: ERD 문서가 실제 구현에 충분한 정보를 담고 있는지 검토

**작업 내용**
- [ ] ERD.md 정독 + 누락 컬럼 보강
- [ ] W5 시점 인덱스(최소) vs W10 시점 인덱스(추가) 명확히 분리되어 있는지 검증
- [ ] 각 도메인의 비즈니스 룰이 DB 제약과 애플리케이션 검증 중 어디에 있는지 표시

**완료 조건**
- [ ] ERD 문서에 미해결 항목(TBD) 0건
- [ ] 인덱스 단계 구분 표 검증 완료

---

### #W1-3 [chore] 시드 데이터 출처 사전 조사
**라벨**: `type:chore`, `S`

**목적**: W8에 5~10만 건 시드 데이터를 어떤 방식으로 확보할지 결정

**작업 내용**
- [ ] 공개 음악 메타데이터 데이터셋 조사 (MusicBrainz, Discogs API 등)
- [ ] 자체 생성 스크립트 옵션 비교 (Python 스크립트 + Faker)
- [ ] 라이선스 / 사용 가능성 검토
- [ ] 결정 후 `docs/decisions/seed-data.md` 작성

**완료 조건**
- [ ] 시드 데이터 출처 + 수급 방법 결정됨
- [ ] 결정 근거가 ADR로 기록됨

---

## W2 — 설계 2단계 + 부트스트랩 (M2)

**주차 목표**: API 명세 확정, Spring Boot 프로젝트 부트스트랩

### #W2-1 [docs] API 명세서 v1 최종 검토 + Postman 환경 구성
**라벨**: `type:docs`, `M`

**목적**: 모든 엔드포인트의 요청/응답/에러 코드가 명확히 정의되어 있는 상태

**작업 내용**
- [ ] API.md 전수 점검 (요청 본문, 응답 본문, 에러 코드)
- [ ] 누락된 엔드포인트 보강
- [ ] Postman 컬렉션 환경 변수 설계 (`baseUrl`, `accessToken`, `refreshToken`)
- [ ] Postman 컬렉션 기본 폴더 구조 생성

**완료 조건**
- [ ] API.md 미해결 항목 0건
- [ ] Postman 환경 파일 export → 저장소 커밋

---

### #W2-2 [infra] Spring Boot 프로젝트 부트스트랩
**라벨**: `type:infra`, `M`

**목적**: 빈 Spring Boot 4.0.x 프로젝트가 빌드·실행되는 상태

**작업 내용**
- [ ] Spring Initializr로 프로젝트 생성 (Java 21, Gradle Kotlin DSL, Jar)
- [ ] 의존성 추가: Spring Web, Security, Data JPA, Validation, Actuator, Flyway, MySQL Driver, Lombok
- [ ] `build.gradle.kts` 정리 (그룹 ID `com.groove`)
- [ ] `.gitignore`, `.env.example`, `LICENSE`, `README.md` 초안
- [ ] `./gradlew bootRun` 실행 성공 확인
- [ ] 첫 커밋 + GitHub 푸시

**완료 조건**
- [ ] `./gradlew build` 성공
- [ ] `./gradlew bootRun` → 8080 포트 기동 성공
- [ ] GitHub 저장소 공개 또는 비공개로 생성됨

---

## W3 — 인프라 / 스켈레톤 (M3) ★ G1 게이트

**주차 목표**: Docker 환경에서 빈 앱 정상 기동 + 공통 인프라 완비

### #W3-1 [infra] Docker Compose + Spring Profiles 구성
**라벨**: `type:infra`, `M`
**선행**: #W2-2

**목적**: `docker-compose up -d` 한 번으로 전체 환경 구성

**작업 내용**
- [x] `docker-compose.yml` 작성 (app, mysql 서비스, 볼륨, 네트워크)
- [x] `Dockerfile` 작성 (multi-stage build, JRE 21)
- [x] Spring Profiles 분리: `local`, `docker`, `test`
- [x] `application.yml` (공통) + `application-{profile}.yml` 작성
- [x] 환경 변수 외부화 (`DB_PASSWORD`, `JWT_SECRET` 등)
- [x] `.env.example` 보강

**완료 조건**
- [x] `docker-compose up -d` 후 app + mysql 정상 기동
- [x] App에서 MySQL 연결 성공 (헬스체크 OK)
- [x] `.env` git ignore 확인

---

### #W3-2 [infra] Flyway 초기 마이그레이션 + 패키지 구조 생성
**라벨**: `type:infra`, `S`
**선행**: #W3-1

**목적**: 스키마 버저닝 시작 + 도메인별 패키지 골격 마련

**작업 내용**
- [x] `src/main/resources/db/migration/` 디렉토리
- [x] `V1__init.sql` 작성 (헬스체크용 빈 스키마)
- [x] Flyway 설정 (application.yml)
- [x] 패키지 구조 생성 (도메인 폴더 + `package-info.java`)
  - `auth`, `member`, `catalog/album`, `catalog/artist`, `catalog/genre`, `catalog/label`, `cart`, `order`, `payment`, `shipping`, `review`, `admin`, `common`

**완료 조건**
- [x] Flyway 적용 후 `flyway_schema_history` 테이블 생성 확인
- [x] 모든 도메인 패키지 존재

---

### #W3-3 [common] GlobalExceptionHandler + 도메인 예외 계층
**라벨**: `type:feature`, `domain:common`, `M`
**선행**: #W3-2

**목적**: ProblemDetail 기반 통합 에러 응답

**작업 내용**
- [x] `BusinessException` 추상 클래스 + 하위 (`AuthException`, `ValidationException`, `DomainException`, `ExternalException`)
- [x] `ErrorCode` enum (HTTP 상태 + 코드 매핑)
- [x] `GlobalExceptionHandler` (`@RestControllerAdvice`)
- [x] `MethodArgumentNotValidException` 등 Spring 내장 예외 매핑
- [x] ProblemDetail 응답 포맷에 `code`, `timestamp`, `traceId` 확장 필드 추가

**완료 조건**
- [x] 의도적 예외 발생 시 ProblemDetail 응답 확인 (테스트 컨트롤러 또는 단위 테스트)
- [x] 응답 Content-Type: `application/problem+json`

---

### #W3-4 [common] MDC 필터 + 로깅 설정 + Rate Limit 골격
**라벨**: `type:feature`, `domain:common`, `M`
**선행**: #W3-3

**목적**: 요청 추적 + 표준 로깅 + Rate Limit 인터페이스 준비

**작업 내용**
- [x] `MdcFilter`: `X-Request-Id` 발급/추출, MDC 주입
- [x] Logback 설정 (`logback-spring.xml`) — 패턴에 `requestId`, `userId` 포함
- [x] `RateLimitFilter` 골격 (Bucket4j 의존성 추가)
  - 정책 인터페이스 정의, 실제 정책은 도메인 구현 시 등록
- [x] 비즈니스 이벤트 표준 로그 헬퍼 (`BIZ_EVENT type=...`)

**완료 조건**
- [x] 요청 시 응답 헤더에 `X-Request-Id` 포함
- [x] 로그에 requestId가 자동 출력됨

---

### #W3-5 [security] Spring Security 베이스라인 (JWT 미연동)
**라벨**: `type:feature`, `domain:auth`, `M`
**선행**: #W3-4

**목적**: 보호된 엔드포인트 / 공개 엔드포인트 분리 동작

**작업 내용**
- [x] `SecurityConfig`: `SecurityFilterChain` 빈 등록
- [x] CORS 설정 (개발 단계는 모든 오리진 허용)
- [x] CSRF 비활성화 (Stateless API)
- [x] URL 패턴 권한 정책: `/auth/**`, `/api/v1/albums/**`(GET) 등 permitAll, 나머지 authenticated
- [x] JWT 필터는 다음 주(#W4)에 연결 — 일단 자리만 마련
- [x] 임시 테스트 컨트롤러 (`/api/v1/ping/secured`) 추가 → 401 응답 확인 후 제거

**완료 조건**
- [x] 보호된 엔드포인트 401 응답
- [x] 공개 엔드포인트 200 응답
- [x] CORS 프리플라이트 동작

---

### #W3-6 [docs] G1 게이트 점검 + W4 준비
**라벨**: `type:docs`, `S`
**선행**: #W3-1 ~ #W3-5

**목적**: G1 충족 검증 + W4 진입 준비

**작업 내용**
- [x] G1 체크리스트 모든 항목 통과 확인
- [x] README에 "현재 W3 완료" 표시
- [x] W4 인증 도메인 진입 전 의존성 검토 (jjwt 또는 Nimbus JOSE 선정)

**완료 조건**
- [x] G1 게이트 통과
- [x] JWT 라이브러리 선정됨

---

## W4 — 인증 / 회원 (M4)

**주차 목표**: 보안 흐름 완성. 가장 보안에 민감한 영역이라 우선 처리.

### #W4-1 [member] Member 엔티티 + 회원가입 API
**라벨**: `type:feature`, `domain:member`, `M`
**선행**: #W3-6

**작업 내용**
- [x] `Member` 엔티티 + Repository
- [x] `MemberRole` enum
- [x] Flyway V2 마이그레이션 (`member` 테이블)
- [x] `MemberService.signup()` (BCrypt 해싱)
- [x] `POST /api/v1/auth/signup` 컨트롤러
- [x] 입력 검증 (`@Valid`)
- [x] 단위 테스트 (이메일 중복, 비밀번호 정책)

**완료 조건**
- [x] 정상 회원가입 동작
- [x] 이메일 중복 시 409 응답
- [x] BCrypt 해시가 DB에 저장됨 (평문 X)

---

### #W4-2 [auth] JWT Provider + JwtAuthenticationFilter
**라벨**: `type:feature`, `domain:auth`, `M`
**선행**: #W4-1

**작업 내용**
- [x] `JwtProvider`: 토큰 생성·검증·파싱
- [x] Access Token (TTL 30분), Refresh Token (TTL 14일)
- [x] Secret 환경변수 분리
- [x] `JwtAuthenticationFilter`: Access Token 검증 + SecurityContext 주입
- [x] `SecurityConfig`에 필터 연결
- [x] 단위 테스트 (정상/만료/위조 토큰)

**완료 조건**
- [x] 유효한 Access Token으로 보호된 엔드포인트 접근 시 200
- [x] 만료/위조 토큰은 401

---

### #W4-3 [auth] 로그인 + 로그아웃 API
**라벨**: `type:feature`, `domain:auth`, `M`
**선행**: #W4-2

**작업 내용**
- [x] `POST /api/v1/auth/login` (이메일/비밀번호 검증, 토큰 발급)
- [x] `POST /api/v1/auth/logout` (Refresh Token 무효화)
- [x] `AuthService` 분리
- [x] 통합 테스트

**완료 조건**
- [x] 로그인 성공 시 access/refresh 응답
- [x] 잘못된 비밀번호 시 401 `AUTH_INVALID_CREDENTIALS`
- [x] 로그아웃 후 해당 refreshToken으로 갱신 시 401

---

### #W4-4 [auth] RefreshToken 엔티티 + Rotation + 갱신 API
**라벨**: `type:feature`, `domain:auth`, `L`
**선행**: #W4-3

**작업 내용**
- [x] `RefreshToken` 엔티티 (해시 저장, `replaced_by_token_id` 포함)
- [x] Flyway 마이그레이션
- [x] `RefreshTokenService`: 발급, 사용(Rotation), 무효화, 재사용 감지
- [x] `POST /api/v1/auth/refresh`
- [x] 토큰 재사용 감지 시 해당 사용자 모든 토큰 무효화 로직
- [x] 통합 테스트:
  - 정상 Rotation
  - 만료된 토큰 사용
  - 이미 사용된(revoked) 토큰 재사용 → 전체 세션 무효화

**완료 조건**
- [x] Rotation 정상 동작 (응답에 새 access/refresh 모두 포함)
- [x] 재사용 감지 시 사용자의 다른 활성 토큰까지 모두 무효화됨

---

### #W4-5 [common] 인증 엔드포인트 Rate Limit 정책 적용
**라벨**: `type:feature`, `domain:common`, `S`
**선행**: #W4-3

**작업 내용**
- [x] 정책 등록: 로그인 IP/분당 10회, 회원가입 IP/분당 3회
- [x] 초과 시 429 + `Retry-After` 헤더
- [x] 통합 테스트

**완료 조건**
- [x] 한도 초과 시 429 응답 확인

---

### #W4-6 [test] 회원 / 인증 도메인 통합 테스트 강화
**라벨**: `type:test`, `domain:auth`, `M`
**선행**: #W4-1 ~ #W4-5

**작업 내용**
- [x] Testcontainers MySQL 셋업
- [x] E2E 시나리오 테스트: 회원가입 → 로그인 → 보호된 API 접근 → 토큰 갱신 → 로그아웃
- [x] 도메인별 단위 테스트 보강
- [x] 커버리지 측정 (JaCoCo) 도입

**완료 조건**
- [x] 전체 인증 시나리오 통합 테스트 통과
- [x] 인증 도메인 라인 커버리지 80%+

---

## W5 — 카탈로그 (Album / Artist / Genre / Label) (M5)

**주차 목표**: 상품 도메인 + 검색 완성.
**주의**: 의도적으로 N+1·슬로우 쿼리 발생 가능한 코드로 작성 (W10 시연용 보존)

### #W5-1 [catalog] Genre + Label 도메인 (단순 엔티티)
**라벨**: `type:feature`, `domain:catalog`, `S`
**선행**: #W4-6

**작업 내용**
- [x] `Genre`, `Label` 엔티티 + Repository
- [x] Flyway V3 마이그레이션
- [x] 관리자 CRUD API (`/api/v1/admin/genres`, `/api/v1/admin/labels`)
- [x] Public 조회 API (`GET /api/v1/genres`, `/labels`)

**완료 조건**
- [x] CRUD 정상 동작
- [x] 중복 name 시 409

---

### #W5-2 [catalog] Artist 도메인
**라벨**: `type:feature`, `domain:catalog`, `M`
**선행**: #W5-1

**작업 내용**
- [x] `Artist` 엔티티 + Repository
- [x] Flyway 추가
- [x] 관리자 CRUD
- [x] Public 조회 (`GET /artists`, `GET /artists/{id}`)

**완료 조건**
- [x] CRUD 정상 동작
- [x] 페이징 동작

---

### #W5-3 [catalog] Album 도메인 + 관리자 CRUD + 재고 조정
**라벨**: `type:feature`, `domain:catalog`, `L`
**선행**: #W5-2

**작업 내용**
- [x] `Album` 엔티티 (FK: artist, genre, label) + `AlbumStatus`, `AlbumFormat` enum
- [x] Flyway 마이그레이션 (※ 검색용 인덱스 의도적 누락)
- [x] 관리자 CRUD (`POST/PUT/DELETE /admin/albums`)
- [x] 재고 조정 (`PATCH /admin/albums/{id}/stock`)
- [x] FK 유효성 검증 (artist/genre/label 존재)

**완료 조건**
- [x] CRUD + 재고 조정 동작
- [x] FK 잘못된 ID 시 400/404

---

### #W5-4 [catalog] Album Public API (목록 / 상세 / 검색)
**라벨**: `type:feature`, `domain:catalog`, `L`
**선행**: #W5-3

**작업 내용**
- [x] `GET /api/v1/albums` — 페이징·정렬·필터(genre/price/year/format/isLimited)
- [x] `GET /api/v1/albums/{id}` — 상세
- [x] `GET /api/v1/artists/{id}/albums` — 아티스트별
- [x] 키워드 검색 (앨범명 + 아티스트명 OR LIKE)
- [x] **의도적 N+1 발생 코드** (페치 조인 미적용 — W10 시연 자료)

**완료 조건**
- [x] 모든 필터·정렬 조합 동작
- [x] 응답에 평균 평점·리뷰 수 포함
- [x] N+1 발생이 Hibernate Statistics 또는 SQL 로그로 확인됨 (시연 자료 보존)

---

### #W5-5 [test] 카탈로그 통합 테스트
**라벨**: `type:test`, `domain:catalog`, `M`
**선행**: #W5-4

**작업 내용**
- [x] Album 검색 시나리오 테스트 (필터 조합)
- [x] 페이징 경계값 테스트
- [x] 관리자 CRUD 통합 테스트

**완료 조건**
- [x] 카탈로그 도메인 라인 커버리지 70%+

---

## W6 — 장바구니 + 주문 (M6)

**주차 목표**: 주문 생성 흐름 완성. 재고 정합성 문제는 의도적 노출 상태 유지.

### #W6-1 [cart] Cart 도메인 (엔티티 + CRUD API)
**라벨**: `type:feature`, `domain:cart`, `M`
**선행**: #W5-5

**작업 내용**
- [x] `Cart`, `CartItem` 엔티티 + Repository (`findByMemberIdWithItems` fetch join)
- [x] Flyway 마이그레이션 V7 (uk_cart_member, uk_cart_item_cart_album, ck_cart_item_quantity_positive)
- [x] `GET /api/v1/cart` (회원 자동 생성 — POST 도 lazy 자동 생성, GET 단독으로는 비영속)
- [x] `POST /api/v1/cart/items` (수량 누적, UNIQUE 동시성 충돌 1회 재시도)
- [x] `PATCH /api/v1/cart/items/{itemId}`, `DELETE /api/v1/cart/items/{itemId}`, `DELETE /api/v1/cart`
- [x] 비활성 상품 검증 (HIDDEN/SOLD_OUT 거부 — 422 ALBUM_NOT_PURCHASABLE)

**완료 조건**
- [x] 모든 CRUD 동작
- [x] 같은 상품 재추가 시 수량 누적
- [x] 비활성 상품 추가 시 422

---

### #W6-2 [order] Order 엔티티 + 상태 머신
**라벨**: `type:feature`, `domain:order`, `M`
**선행**: #W6-1

**작업 내용**
- [x] `Order`, `OrderItem` 엔티티 + Repository (회원/게스트 정적 팩토리, OrderItem 가격·제목 스냅샷)
- [x] `OrderStatus` enum + `canTransitionTo()` + `isTerminal()` 메서드
- [x] Flyway 마이그레이션 V8 (uk_orders_number, ck_orders_total_non_negative, ck_order_item_quantity_positive, ck_order_item_unit_price_non_neg, FK ON DELETE SET NULL/CASCADE/RESTRICT)
- [x] `Order.changeStatus(next, reason)` 도메인 메서드 (전이 검증 + PAID/CANCELLED 시각·사유 기록)
- [x] 단위 테스트 (8×8 전이 매트릭스 75건 + Order/OrderItem 23건)

**완료 조건**
- [x] 합법 전이만 허용, 불법 전이는 `IllegalStateTransitionException` (HTTP 409 ORDER_INVALID_STATE_TRANSITION)
- [x] 단위 테스트로 전이 표 전수 검증 (자기 자신/null/종착 상태 모두 포함)

---

### #W6-3 [order] 주문 생성 API (회원 + 게스트, 단순 재고 차감)
**라벨**: `type:feature`, `domain:order`, `L`
**선행**: #W6-2

**작업 내용**
- [x] `POST /api/v1/orders` 회원/게스트 분기
- [x] 게스트 정보 검증 (email + phone)
- [x] 다중 상품 처리 + OrderItem 가격 스냅샷
- [x] 트랜잭션 내 재고 검증 + 차감 (※ **락 없이 단순 구현** — W10 시연 시작점)
- [x] orderNumber 발급 (`ORD-YYYYMMDD-XXXXXX`, KST 일자 + 6자리 [A-Z0-9])
- [x] 통합 테스트 (정상/재고부족/비활성상품/입력검증)

**완료 조건**
- [x] 회원/게스트 모두 주문 생성 성공
- [x] 재고 부족 시 409
- [x] 단일 스레드 환경에서 정합성 OK (동시성 문제는 #W6-6에서 재현)

---

### #W6-4 [order] 주문 조회 + 취소 API
**라벨**: `type:feature`, `domain:order`, `M`
**선행**: #W6-3

**작업 내용**
- [x] `GET /orders/{orderNumber}` (회원 본인)
- [x] `POST /orders/{orderNumber}/guest-lookup` (게스트, email 매칭)
- [x] `GET /members/me/orders` (회원 주문 목록, 페이징)
- [x] `POST /orders/{orderNumber}/cancel` (PENDING 한정, 재고 복원)
- [x] 권한 검증 (타인 주문 조회 시 404)

**완료 조건**
- [x] 모든 조회 시나리오 동작
- [x] 취소 시 재고 복원 검증

---

### #W6-5 [test] 장바구니 + 주문 통합 테스트
**라벨**: `type:test`, `domain:order`, `M`
**선행**: #W6-4

**작업 내용**
- [x] 장바구니 → 주문 생성 E2E
- [x] 게스트 주문 시나리오
- [x] 주문 취소 + 재고 복원
- [x] 권한 검증

**완료 조건**
- [x] 주문 도메인 라인 커버리지 75%+

---

### #W6-6 [test] 동시성 테스트 — 오버셀 재현 (시연 자료 보존)
**라벨**: `type:test`, `domain:order`, `M`
**선행**: #W6-3

**목적**: W10 시연용 "Before" 상태 명확히 기록

**작업 내용**
- [x] CountDownLatch + ExecutorService 기반 동시 주문 테스트
- [x] 재고 100짜리 상품에 동시 200 요청 → 오버셀 발생 검증
- [x] 결과 로그 / 스크린샷 보존 (`docs/troubleshooting/overselling-baseline.md`)

**완료 조건**
- [x] 오버셀 재현 테스트가 의도적으로 실패하는 형태로 보존됨 (`@Disabled` + 주석으로 W10 개선 시 활성화 명시)
- [x] 또는 별도 시연 디렉토리에 별도 보존

---

## W7 — 결제 + 배송 + 리뷰 (M7) ★ G2 게이트

**주차 목표**: 핵심 흐름 E2E 완성. 12주 중 가장 무거운 주차.

### #W7-1 [payment] PaymentGateway 인터페이스 + Mock 구현체
**라벨**: `type:feature`, `domain:payment`, `M`
**선행**: #W6-6

**작업 내용**
- [x] `PaymentGateway` 인터페이스 (`request`, `query`, `refund`)
- [x] DTO: `PaymentRequest`, `PaymentResponse`, `RefundRequest`, `RefundResponse`
- [x] `MockPaymentGateway` 구현체 (`@Profile`로 격리)
- [x] 처리 지연 시뮬레이션 (랜덤 100~500ms)
- [x] 성공률 설정 가능 (`payment.mock.success-rate`)
- [x] `MockWebhookSimulator`: 1~5초 후 비동기 웹훅 콜백 발사

**완료 조건**
- [x] Strategy 패턴 정상 동작 (실 PG 추가 시 구현체만 교체 가능)
- [x] Mock PG 호출 후 비동기 웹훅 수신 확인

---

### #W7-2 [payment] IdempotencyService + 멱등성 레코드 테이블
**라벨**: `type:feature`, `domain:payment`, `M`
**선행**: #W7-1

**작업 내용**
- [x] `IdempotencyRecord` 엔티티 + Repository
- [x] Flyway 마이그레이션
- [x] `IdempotencyService.execute(key, supplier)` (락 + 결과 캐싱)
- [x] `Idempotency-Key` 헤더 검증 인터셉터 또는 어노테이션
- [x] TTL 정리 스케줄러 (24시간)

**완료 조건**
- [x] 동일 키 + 처리 완료 시 기존 응답 반환
- [x] 처리 중 동일 키 시 409
- [x] 키 미지정 시 400

---

### #W7-3 [payment] 결제 요청 API + 상태 조회
**라벨**: `type:feature`, `domain:payment`, `L`
**선행**: #W7-2

**작업 내용**
- [x] `Payment` 엔티티 + `PaymentStatus` enum
- [x] Flyway 마이그레이션
- [x] `POST /api/v1/payments` (Idempotency-Key 필수)
  - 주문 상태 검증 (PENDING)
  - PG 호출 → Payment(PENDING) 저장
  - 응답 202 + paymentId
- [x] `GET /api/v1/payments/{paymentId}` 상태 조회
- [x] 통합 테스트

**완료 조건**
- [x] 결제 요청 정상 처리 (PENDING 응답)
- [x] 멱등성 정상 동작

---

### #W7-4 [payment] 웹훅 콜백 + 보상 트랜잭션 + 폴링 스케줄러
**라벨**: `type:feature`, `domain:payment`, `L`
**선행**: #W7-3

**작업 내용**
- [x] `POST /api/v1/payments/webhook` 엔드포인트
- [x] 서명 검증 (Mock에서는 단순 헤더 매칭)
- [x] PAID/FAILED 분기 처리
- [x] 멱등성 (동일 webhook 중복 수신 시 무시)
- [x] 실패 시 보상 트랜잭션: Payment FAILED + Order PAYMENT_FAILED + 재고 복원
- [x] 폴링 스케줄러: PENDING 결제 N분마다 PG `query()` 호출 → 동기화

**완료 조건**
- [x] 정상 웹훅 처리 시 Order PAID 전환
- [x] 실패 웹훅 시 보상 트랜잭션 정상 동작
- [x] 중복 웹훅 무해함

---

### #W7-5 [order] OrderPaidEvent + AFTER_COMMIT 리스너
**라벨**: `type:feature`, `domain:order`, `M`
**선행**: #W7-4

**작업 내용**
- [x] `OrderPaidEvent` 정의
- [x] Order 결제 완료 처리 시 이벤트 발행
- [x] Spring Application Event + `@TransactionalEventListener(AFTER_COMMIT)`
- [x] (배송 도메인 #W7-6에서 구독)

**완료 조건**
- [x] 트랜잭션 롤백 시 이벤트 미발행 검증
- [x] AFTER_COMMIT 시점에 리스너 호출 확인

---

### #W7-6 [shipping] Shipping 도메인 + 자동 진행 스케줄러
**라벨**: `type:feature`, `domain:shipping`, `L`
**선행**: #W7-5

**작업 내용**
- [x] `Shipping` 엔티티 + `ShippingStatus` enum
- [x] Flyway 마이그레이션
- [x] `OrderPaidEvent` 구독 → Shipping(PREPARING) 생성 + 운송장 번호 발급(UUID)
- [x] `GET /api/v1/shippings/{trackingNumber}` 조회 API
- [x] `@Scheduled` 진행 스케줄러: PREPARING → SHIPPED → DELIVERED (시연용 짧은 간격)
- [x] 안전 포장 요청 플래그 처리

**완료 조건**
- [x] 결제 완료 → 배송 자동 생성
- [x] 스케줄러로 상태 자동 진행 확인

---

### #W7-7 [review] Review 도메인 (작성/조회/삭제)
**라벨**: `type:feature`, `domain:review`, `M`
**선행**: #W7-6

**작업 내용**
- [x] `Review` 엔티티 + Repository
- [x] Flyway V_n 마이그레이션
- [x] `POST /api/v1/reviews` (DELIVERED 검증, 1주문-1상품-1리뷰 제약)
- [x] `GET /api/v1/albums/{id}/reviews` (페이징, 회원명 마스킹)
- [x] `DELETE /api/v1/reviews/{reviewId}` (본인만)
- [x] Album 평균 평점 / 리뷰 수 계산 로직

**완료 조건**
- [x] 모든 API 정상 동작
- [x] 배송 완료 전 리뷰 시 422
- [x] 중복 리뷰 시 409

---

### #W7-8 [test] 결제 / 배송 E2E 통합 테스트
**라벨**: `type:test`, `domain:payment`, `L`
**선행**: #W7-7

**작업 내용**
- [x] E2E 시나리오: 회원가입 → 로그인 → 장바구니 → 주문 → 결제 → 웹훅 → 배송 → 리뷰
- [x] 게스트 주문 E2E
- [x] 결제 실패 시 보상 트랜잭션 검증
- [x] 멱등성 검증 (동일 키 동시 요청)
- [x] G2 게이트 점검

**완료 조건**
- [x] G2 게이트 모든 항목 통과 ★ (관리자 주문 조작은 #69 / PR #71 로 완료)

---

## W8 — 통합 테스트 강화 + 시드 + Postman (M8)

**주차 목표**: 신뢰성 있는 코드베이스 + 측정 가능한 데이터셋 확보

### #69 [admin] 관리자 주문 조회 / 상태 강제 전환 / 환불 API ✅
**라벨**: `domain:admin`, `domain:order`, `M`, `type:feature`
**배경**: W7 완료 검증 중 식별 — 관리자 상품 CRUD 는 W4~W7 에 완료됐으나 주문 조작 API 는 마일스톤 이슈가 없어 미구현이었다. §4 컷 정책 2번 항목을 컷하지 않고 정식 진행.

**작업 내용**
- [x] `GET /api/v1/admin/orders` — 전체 주문 목록 (페이징 + 상태/회원/기간 필터, Specification 조합)
- [x] `GET /api/v1/admin/orders/{orderNumber}` — 주문 상세 (관리자)
- [x] `PATCH /api/v1/admin/orders/{orderNumber}/status` — 상태 강제 전환 (전진 전이만 허용, 사유 필수; 취소·환불은 별도)
- [x] `POST /api/v1/admin/orders/{orderNumber}/refund` — 환불 (PG `refund()` + Payment REFUNDED + Order CANCELLED + 재고 복원, 멱등)
- [x] 권한: `/api/v1/admin/**` → ROLE_ADMIN (기존 SecurityConfig 정책 재사용)
- [x] 단위 테스트 (`Payment.markRefunded`, `AdminOrderService`) + 통합 테스트 (`AdminOrderControllerTest`)

**완료 조건**
- [x] 비관리자 접근 403 / 합법 전이만 허용(불법 409 `ORDER_INVALID_STATE_TRANSITION`)
- [x] 환불 시 Payment REFUNDED + Order CANCELLED + 재고 복원, 중복 환불 무해(`alreadyRefunded`)

---

### #72 [payment] PG 환불 멱등 키 도입 — 보상 트랜잭션 부분 실패 시 중복 환불 방지 ✅
**라벨**: `domain:payment`, `domain:admin`, `S`, `type:feature`
**선행**: #69 (PR #71 셀프 리뷰 중 식별)
**배경**: `AdminOrderService.refund` 의 PG `refund()` → DB 작업 순서에서 PG 호출 후 어떤 단계든 실패하면 트랜잭션은 롤백되지만 PG 측 환불은 유지되어, 관리자 재시도 시 PG 가 같은 결제에 두 번 환불 요청을 받을 수 있다. Mock 게이트웨이는 우연히 멱등이지만 실 PG 어댑터 도입 전 명시적 계약이 필요.

**작업 내용**
- [x] `RefundRequest.idempotencyKey` 필수 필드 도입 (Stripe `Idempotency-Key` 호환 ≤255자)
- [x] `Payment.refundIdempotencyKey()` — `"refund:{id}:{pgTransactionId}"` 결정적 derivation (영속화된 결제만 호출 가능 — 방어선)
- [x] `MockPaymentGateway.refund()` — `idempotencyKey` 기반 응답 캐시 (`computeIfAbsent`) + `refundCallCount()` 테스트 노출
- [x] `AdminOrderService.refund` — 호출 시 결정적 키 주입
- [x] 단위 테스트 — `RefundRequestTest` 신규, `PaymentTest`/`MockPaymentGatewayTest`/`AdminOrderServiceTest` 보강
- [x] 통합 테스트 — `AdminRefundIdempotencyIntegrationTest`: 보상 트랜잭션 부분 실패 후 재시도 시 PG 실호출 1회 보장

**완료 조건**
- [x] 같은 키 두 번 호출 시 PG 호출 1회로 정상화 (단위)
- [x] 보상 트랜잭션 중간 실패 강제 후 재시도가 PG 중복 환불을 일으키지 않음 (통합)
- [x] DB 변경 없음 (deterministic key — 후속 컬럼 도입은 부분 환불 도입 시점에 검토)

---

### #W8-1 (GH #139) [test] 통합 테스트 보강 (커버리지 60%+) ✅
**라벨**: `type:test`, `M`
**선행**: #W7-8

**작업 내용**
- [x] JaCoCo 리포트 분석 → 미커버 영역 식별
- [x] 핵심 도메인(주문/결제/인증) 커버리지 80% 이상
- [x] 엣지 케이스 추가 (잘못된 입력, 권한 부족, 상태 위반)

**완료 조건**
- [x] 전체 라인 커버리지 60%+
- [x] 핵심 도메인 80%+ (`build.gradle.kts` JaCoCo 게이트로 강제)

---

### #W8-2 (GH #142) [test] 단위 테스트 강화 (도메인 로직) ✅
**라벨**: `type:test`, `M`
**선행**: #W8-1

**작업 내용**
- [x] 상태 전이 메서드 단위 테스트 (Order, Payment, Shipping)
- [x] 검증 규칙 단위 테스트
- [x] BCrypt, JWT 등 보안 모듈 단위 테스트

**완료 조건**
- [x] 모든 도메인 로직 단위 테스트 존재

---

### #W8-3 [chore] 시드 데이터 스크립트 작성
**라벨**: `type:chore`, `L`
**선행**: #W1-3

**구현**: 카탈로그까지 Python + Faker 합성으로 구현(ADR의 Discogs Dump는 후속 — `docs/decisions/seed-data.md` 구현 결과 노트). `db/seed/generate_seed.py`(멀티로우 INSERT seed.sql 생성) + `scripts/seed.sh`(FK-safe TRUNCATE 후 import, `--docker`/`--yes`). 계정은 데모(@groove.dev)와 분리된 `loadtest*@groove.test`.

**작업 내용**
- [x] `db/seed/` 디렉토리
- [x] Genre 12건, Label 80건, Artist 2,000건 (env 오버라이드)
- [x] Album 5만 건 기본 (`ALBUM_COUNT`로 10만+ 상향, 다양한 가격·연도·장르·포맷 분포)
- [x] 한정반(`is_limited=true`) 40건
- [x] 단일 재고(stock=1) 8건
- [x] 테스트 회원 80명 (k6 다중 사용자용, `loadtest001..080@groove.test`)
- [x] ADMIN 계정 1개 (`loadtest-admin@groove.test`)
- [x] 시드 적용 스크립트 (`./scripts/seed.sh`, 로컬 mysql / docker exec 양쪽)

**완료 조건**
- [x] `./scripts/seed.sh` 한 번으로 시드 적용
- [x] 검색 슬로우 쿼리 재현 가능 (EXPLAIN 풀 스캔 확인)

---

### #W8-4 (GH #141) [docs] API 컬렉션 완성 — Postman → Bruno 이행 ✅
**라벨**: `type:docs`, `M`
**선행**: #W7-8
**상태**: ✅ 완료. #141 의 "전체 엔드포인트·Rate Limit 엣지·응답검증 보강" 과정에서 도구를 **Postman → Bruno 로 전면 이행**했다(#216 태그 스킴 머지, #163 쿠키 테스트 수정으로 full **63/63** 통과, #217 에서 Postman/newman/변환기 제거). 컬렉션은 `bruno/`(82 `.bru`) 가 정본이며 회원 셀프서비스·쿠폰까지 전 엔드포인트 + 엣지 케이스를 포함한다.

**작업 내용**
- [x] 모든 엔드포인트 요청 등록 (회원 셀프서비스 `/members/me/*`·쿠폰 포함 — Bruno `bruno/` 전수)
- [x] 환경 변수 자동 저장 스크립트 (로그인 응답 → accessToken/refreshToken; 재사용 시 `bru.cookies.jar().clear()` 필요)
- [x] E2E 시나리오 폴더 (회원 흐름, 게스트 흐름, 관리자 흐름)
- [x] Edge Cases 폴더 (Idempotency·재고부족 409·리뷰 422·Refresh 재사용 401·Rate Limit)
- [x] 응답 검증 스크립트 (필수 필드 확인) — Bruno 테스트 63/63
- [x] 컬렉션 → `bruno/` 커밋 (Postman 산출물은 #217 에서 제거)

**완료 조건**
- [x] Bruno 로 회원 E2E 흐름 1클릭 시연 가능 (토큰 자동 저장 + cartItemId/orderNumber/paymentId 체이닝)

---

### #W8-5 (GH #143) [docs] README v1 작성 — 아키텍처 다이어그램 임베드 ✅
**라벨**: `type:docs`, `M`
**선행**: #W8-3, #W8-4
**상태**: ✅ 완료. `README.md` 에 소개·차별화(성능/동시성 개선 사례)·빠른 시작·기술 스택·문서 링크·진행 현황 + 아키텍처 다이어그램 임베드까지 완료(#143). 시드 적용 안내(`./scripts/seed.sh`)는 #W8-3 에서 반영.

**작업 내용**
- [x] 프로젝트 한 줄 소개 + 차별화 포인트 (성능·동시성 개선 사례 섹션)
- [x] 빠른 시작 (`docker-compose up` + `./scripts/seed.sh`)
- [x] 아키텍처 다이어그램 임베드 (#143)
- [x] API 문서 링크 (Swagger / API.md)
- [x] 기술 스택 표

**완료 조건**
- [x] 처음 보는 사람이 5분 안에 프로젝트 시작 가능 (docker-compose 빠른 시작 + local 프로파일 자동 주입 안내)

---

## W9 — 측정 (M9) ★ G3 게이트

**주차 목표**: 무엇을 개선할지 데이터로 결정

### #W9-1 (GH #192) [loadtest] k6 환경 셋업 + search.js 시나리오 ✅
**라벨**: `type:test`, `M`
**선행**: #W8-5

**작업 내용**
- [x] `loadtest/` 디렉토리 + k6 Docker 실행 스크립트
- [x] 공통 헬퍼 (auth 토큰 발급, 시드 사용자 풀; 요약 JSON 토큰 비식별화 #205)
- [x] `search.js` — 다양한 필터 조합 검색 부하

**완료 조건**
- [x] `k6 run loadtest/search.js` 정상 실행 + JSON 결과 출력

---

### #W9-2 (GH #193) [loadtest] order.js + payment.js 시나리오 ✅
**라벨**: `type:test`, `M`
**선행**: #W9-1

**작업 내용**
- [x] `order.js` — 주문 생성 부하 (다중 상품)
- [x] `payment.js` — 결제 + 멱등성 검증 (동일 키 재요청)
- [x] 결과 비교용 표 양식 정의

**완료 조건**
- [x] 두 시나리오 정상 실행

---

### #W9-3 (GH #194) [loadtest] flash-sale.js 시나리오 (한정반 동시 주문) ★ ✅
**라벨**: `type:test`, `L`
**선행**: #W9-2

**작업 내용**
- [x] `flash-sale.js` — 한정반 상품(재고 100) 동시 1000 요청
- [x] 정합성 검증 로직 (성공 응답 수 + DB 최종 재고 매칭)
- [x] 다양한 부하 단계(100, 500, 1000) 시나리오 옵션
- [x] **이 시점 결과는 오버셀 발생** (시연 Before 자료 — created 211~222, lost-update 111~122)

**완료 조건**
- [x] 오버셀이 측정으로 재현됨 (성공 응답 > 100)
- [x] 결과 JSON 보존

---

### #W9-4 (GH #195) [measurement] N+1 측정 + 슬로우 쿼리 EXPLAIN ✅
**라벨**: `type:test`, `M`
**선행**: #W8-5

**작업 내용**
- [x] Hibernate Statistics 활성화 (테스트 환경)
- [x] 상품 목록 조회 시 발생 쿼리 수 측정 (예: `1+N`)
- [x] 검색 쿼리 EXPLAIN 결과 캡처 (풀 스캔 확인 — type=ALL, rows≈49,803)
- [x] 결과 정리 → `docs/measurement/baseline.md`

**완료 조건**
- [x] N+1 발생 수치 기록됨
- [x] EXPLAIN 결과 캡처 보존

---

### #W9-5 [measurement] 베이스라인 측정 결과 정리
**라벨**: `type:docs`, `M`
**선행**: #W9-3, #W9-4

**작업 내용**
- [x] 모든 시나리오 베이스라인 (TPS, p50/p95/p99, 에러율) 표 작성 (`docs/measurement/baseline.md` §1, 5종)
- [x] 발견된 문제 목록 + 우선순위 (§3, 4건 — 오버셀 P0·N+1/풀스캔 P1·5xx P2)
- [x] W10~W11 개선 작업 계획 확정 (§4)

**완료 조건**
- [x] G3 게이트 통과 (2026-06-10, #196)
- [x] 개선 작업 우선순위가 데이터로 뒷받침됨

---

### #W9-6 (GH #197) [chore] 측정 이슈 트래킹 정리 ✅
**라벨**: `type:chore`, `S`
**선행**: #W9-5

**작업 내용**
- [x] 발견된 문제별로 W10/W11 이슈 신규 생성 — GH #203~#211 (W10-1~4 → M10, W11-1~5 → M11). 각 헤더에 발급 번호 매핑(위 §W10·§W11).
- [x] 각 이슈에 베이스라인 수치 첨부 (`baseline.md` Before → After 목표 인용)

**완료 조건**
- [x] 개선 이슈 모두 등록됨 (M10 4건 · M11 5건)

---

## W10 — CS 개선 1차 (M10)

**주차 목표**: 가장 임팩트 큰 3가지 개선 — Before/After 그래프로 시연 가능

### #W10-1 (GH #203) [improvement] N+1 해결 (페치 조인 / @EntityGraph) ✅
**라벨**: `type:improvement`, `domain:catalog`, `M`
**선행**: #W9-6

**작업 내용**
- [x] 상품 목록 조회 → `@EntityGraph` 적용 (`AlbumRepository` artist/genre/label, count 쿼리 분리; Order/Payment/Review Repository 도 동반)
- [x] Hibernate Statistics 재측정
- [x] 응답 시간 비교
- [x] `docs/improvements/n-plus-one.md` 작성 (Before/After 코드 + 측정)

**완료 조건**
- [x] 쿼리 수 측정 결과 기록 (`docs/improvements/n-plus-one.md`)
- [x] 코드 diff 보존

---

### #W10-2 (GH #204) [improvement] 검색 인덱스 추가 (Flyway V21) ✅
**라벨**: `type:improvement`, `domain:catalog`, `M`
**선행**: #W10-1
**비고**: 마이그레이션 번호는 계획 시점 "V6" 이었으나 실제로는 **V21** 로 적용됐다(V6 은 이미 `V6__init_album.sql` 이 선점, V20 은 member Java 마이그레이션이 선점 → V21 배정). 키워드 경로는 단일 B-Tree 로 해소 불가(선행 와일드카드 + cross-table OR)라 `artist_name` 비정규화 컬럼 + FULLTEXT(ngram) 로 단일 테이블화했다.

**작업 내용**
- [x] `V21__add_search_indexes.sql` 작성 — `artist_name` 비정규화 + `FULLTEXT ft_album_keyword(ngram)` + 복합 인덱스 4종(status_created·search·year·limited)
- [x] 적용 후 EXPLAIN 결과 비교 (풀 스캔 → 인덱스 사용)
- [x] k6 search.js 재실행 → 응답 시간 비교
- [x] `docs/improvements/search-index.md`

**완료 조건**
- [x] EXPLAIN 결과 Before/After 명확 (type=ALL rows≈49,803 → 인덱스)
- [x] 응답 시간 개선 수치 기록 (search p95 930ms > SLO 800ms → 개선)

---

### #W10-3 (GH #205) [improvement] 동시성 — 비관적 락 적용 + flash-sale 재측정
**라벨**: `type:improvement`, `domain:order`, `L`
**선행**: #W10-2

**작업 내용**
- [x] 재고 차감 부분에 비관적 락 (`SELECT ... FOR UPDATE`) 적용 — `AlbumRepository.findByIdForUpdate`
- [x] 동시성 테스트 재실행 → 오버셀 0건 검증 — `OversellingBaselineTest` active 검증
- [x] k6 flash-sale.js 재측정 (TPS, 에러율, 응답시간) — 100/500/1000 VU
- [x] `docs/improvements/concurrency.md` (단계별 비교)
  - 단계 a: 락 없음 (오버셀 발생) — `placeWithoutLock` baseline 보존
  - 단계 b: 비관적 락 (정합성 OK, TPS 측정)
  - (c: 낙관적 락 / Redis 분산락 비교 → #W11-4 로 이연)

**완료 조건**
- [x] 오버셀 0건 측정 검증 — created 211~222 → 100, lost-update 111~122 → 0
- [x] TPS·응답시간 Before/After 표 작성 — 5xx 5.8~9.9% → 0%, order p95(1000VU) 1.25s → 342ms

---

### #W10-4 (GH #206) [docs] W10 개선 사례 README 정리 ✅
**라벨**: `type:docs`, `M`
**선행**: #W10-1 ~ #W10-3

**작업 내용**
- [x] README에 "성능 개선 사례" 섹션 추가
- [x] 3개 개선 모두 Before/After 표 + 그래프
- [x] 트레이드오프 명시 (왜 비관적 락 선택했는지, Redis 미도입 이유)

**완료 조건**
- [x] 개선 사례 3건 모두 README에 시연 가능한 형태로 기록

---

## W11 — CS 개선 2차 (M11)

**주차 목표**: 정합성 시연 마무리, 시간 여유 시 추가 개선

### #W11-1 (GH #207) [test] 결제 멱등성 통합 테스트 ✅
**라벨**: `type:test`, `domain:payment`, `M`
**선행**: #W10-4

**작업 내용**
- [x] 동시 동일 Idempotency-Key 요청 → 단일 결제 생성 검증 (RANDOM_PORT 동시성)
- [x] 웹훅 중복 수신 → 상태 전이 1회 검증
- [x] 멱등성 키 만료 후 재사용 시나리오
- [x] `docs/improvements/idempotency.md`

**완료 조건**
- [x] 모든 멱등성 시나리오 통합 테스트 통과

---

### #W11-2 (GH #208) [chore] 코드 클린업 + TODO/FIXME 정리 ✅
**라벨**: `type:chore`, `M`
**선행**: #W10-4
**비고**: 정리 과정에서 W10 으로 미뤄둔 TODO(POST /payments·게스트 주문조회 rate limit)를 #208 와 함께 실제 해소(커밋 `9c661f8`).

**작업 내용**
- [x] 전체 TODO/FIXME 주석 검토 (W10 잔여 rate limit TODO 해소)
- [x] 미사용 코드/임포트 정리
- [x] 메서드/클래스 네이밍 일관성 점검
- [x] 의존성 정리 (사용 안 하는 starter 제거)

**완료 조건**
- [x] TODO/FIXME 0건 또는 명시적 v2 표시
- [x] `./gradlew check` 경고 최소화

---

### #W11-3 (GH #209) [improvement] 단일 재고 희귀반 시연 (선택) ✅
**라벨**: `type:improvement`, `priority:optional`, `M`
**선행**: #W10-3

**작업 내용**
- [x] stock=1 상품에 동시 100 요청 → 정확히 1건 성공 (커밋 `2599f6e`, 경계 시연)
- [x] 측정 데이터 보존 + README 보강

**완료 조건**
- [x] 시연 자료 추가됨

---

### #W11-4 (GH #210) [improvement] Redis 분산락 비교 (선택) — ✂️ 컷 (NOT_PLANNED, 2026-06-12)
**라벨**: `type:improvement`, `priority:optional`, `L`
**선행**: #W10-3
**결정**: **미진행으로 종료**(GitHub `not planned`). 단일 인스턴스 + MySQL 비관적 락(#205)으로 오버셀 0건·목표 TPS 를 이미 달성해 분산락 도입의 한계 효용이 낮고, Redis 운영 복잡도(인프라 추가·장애 모드)가 포트폴리오 범위를 넘어선다. 분산 환경 확장 시점에 재검토(미도입 사유는 #W10-4 README 트레이드오프에 기재). 코드/Compose 변경 없음.

**작업 내용 (미수행 — 컷)**
- [ ] ~~Redis + Redisson 의존성 추가~~
- [ ] ~~Docker Compose에 Redis 추가~~
- [ ] ~~InventoryService에 분산락 옵션 추가~~
- [ ] ~~비관적 락 vs 분산락 측정 비교~~
- [ ] ~~트레이드오프 문서화~~

**완료 조건**
- [x] 미도입 의사결정 기록 (README 트레이드오프 + 본 항목)

---

### #W11-5 (GH #211) [improvement] Virtual Threads 활성화 + 영향 측정 (선택) — ✂️ 컷 (NOT_PLANNED, 2026-06-12)
**라벨**: `type:improvement`, `priority:optional`, `M`
**선행**: #W11-2
**결정**: **미진행으로 종료**(GitHub `not planned`). 현 병목은 스레드 풀 포화가 아니라 DB 락/쿼리(W10 에서 해소)였고, 측정 게이트(G3)와 개선 사례가 이미 확보돼 옵션 항목으로 컷. `spring.threads.virtual.enabled` 미설정(코드 변경 없음). 추후 I/O 바운드 부하 재현 시 재검토.

**작업 내용 (미수행 — 컷)**
- [ ] ~~`spring.threads.virtual.enabled=true` 또는 명시 설정~~
- [ ] ~~동일 시나리오 측정 비교~~
- [ ] ~~결과에 따라 채택 여부 결정~~

**완료 조건**
- [x] 미채택 의사결정 기록 (본 항목)

---

## W12 — 문서화 + 시연 (M12) ★ G4 게이트

**주차 목표**: 평가자가 30분 안에 가치를 파악할 수 있는 상태

### #W12-1 [docs] README 최종 정리
**라벨**: `type:docs`, `L`
**선행**: #W11-2

**작업 내용**
- [ ] 프로젝트 소개 + 차별화 포인트 (서두 강력하게)
- [ ] 빠른 시작 가이드
- [ ] 아키텍처 요약 (다이어그램)
- [ ] 핵심 기술 결정 + 트레이드오프
- [ ] 성능 개선 사례 (Before/After)
- [ ] 트러블슈팅 / 의사결정 기록 3건+

**완료 조건**
- [ ] 30분 안에 프로젝트 가치 파악 가능
- [ ] PRD §11 DoD 모든 항목 충족

---

### #W12-2 [docs] 의사결정 기록(ADR) 정리
**라벨**: `type:docs`, `M`
**선행**: 진행 중 누적된 결정사항

**작업 내용**
- [ ] `docs/decisions/` 디렉토리 정리
- [ ] 주요 결정 ADR 형식으로 작성 (Why X, Why not Y, 트레이드오프)
- [ ] 최소 5건: PG 모킹, 동시성 전략, 이벤트 vs 큐, 패키지 구조, 테스트 전략

**완료 조건**
- [ ] ADR 5건 이상 작성

---

### #W12-3 [docs] 모든 설계 문서 최신화
**라벨**: `type:docs`, `M`
**선행**: #W11-2

**작업 내용**
- [ ] PRD.md / ARCHITECTURE.md / ERD.md / API.md 코드와 동기화
- [ ] 각 문서 버전 번호 업데이트
- [ ] 변경 이력(CHANGELOG) 추가

**완료 조건**
- [ ] 모든 설계 문서가 최종 코드 상태와 일치

---

### #W12-4 [docs] 시연 자료 (영상 또는 GIF)
**라벨**: `type:docs`, `M`
**선행**: #W12-1

**작업 내용**
- [ ] E2E 흐름 시연 영상 또는 GIF 1개 (회원가입 → 결제 → 배송 자동 진행)
- [ ] 한정반 동시성 시연 영상 또는 그래프 (Before/After)
- [ ] README 상단에 임베드

**완료 조건**
- [ ] 시각 자료가 README에 포함됨

---

### #W12-5 [chore] 최종 점검 + G4 게이트
**라벨**: `type:chore`, `M`
**선행**: #W12-1 ~ #W12-4

**작업 내용**
- [ ] G4 게이트 체크리스트 전수 점검
- [ ] 신규 환경에서 `git clone` → `docker-compose up` → 시연 흐름 재현 검증
- [ ] 의존성 보안 점검 (`./gradlew dependencyCheckAnalyze` 또는 GitHub Dependabot 결과)
- [ ] GitHub 저장소 상단 설명·태그·README 배지 정리
- [ ] 포트폴리오용 링크/요약 작성

**완료 조건**
- [ ] G4 게이트 모든 항목 통과 ★
- [ ] 외부에서 1회 클린 셋업 성공

---

## 확장 — 쿠폰 시스템 (M13, ✅ 완료)

> **✅ 완료 (~2026-06-01)**: 전 Phase(GH #88~#93, PR #94~#101) 머지 완료. 마이그레이션 `V14`~`V16` 적용, 엔드포인트·관리자 API 구현, 원자적 조건부 UPDATE 채택, k6 부하·3종 Before/After 측정 완료. 아래 Phase별 체크리스트는 당시 계획 기록이며 실제 구현 결과가 정본(ERD/API/troubleshooting).
>
> W1~W12 본 로드맵 외 **확장 도메인**. 이커머스 핵심 기능(쿠폰)을 추가하되, **선착순 한정수량 발급의 대용량 동시성**을 단계적으로 시연해 W8~W12 의 미충족 산출물(특히 DoD #4 k6 부하테스트·#5 Before/After 개선 사례)을 함께 메운다.
> 설계 정본: [plans/coupon-system.md](plans/coupon-system.md) · 동시성 결정: [decisions/coupon-concurrency.md](decisions/coupon-concurrency.md) · 스키마: [ERD.md §4.15/§4.16](ERD.md) · API: [API.md §3.9](API.md) · 측정: [troubleshooting/coupon-issuance-concurrency.md](troubleshooting/coupon-issuance-concurrency.md).
> 1 Phase = 1 이슈 = 1 PR. 브랜치 `feat/{이슈번호}`, 커밋·PR 한글, `Closes #N`.

### #C-0 (GH #88) [docs] 쿠폰 설계·ERD·API 명세 + 마이그레이션 초안
**라벨**: `type:docs`, `domain:coupon`, `M`
**선행**: G2

**목적**: 구현 전 설계·스키마·API 를 문서로 확정한다.

**작업 내용**
- [x] ERD §4.15/§4.16(`coupon`/`member_coupon`) + `orders.discount_amount`
- [x] API §3.9(쿠폰)·§3.10(관리자 쿠폰) + 에러 코드 + rate limit
- [x] glossary 엔티티·enum·용어
- [x] 설계 plan + 동시성 ADR + 트러블슈팅 skeleton
- [ ] `V14__init_coupon.sql`·`V15__order_coupon_columns.sql` SQL 초안

**완료 조건**
- [x] ERD/API/glossary/plan/ADR 상호 링크 정합
- [ ] 마이그레이션 SQL 이 ERD 와 1:1

### #C-1 (GH #89) [feature] 쿠폰 도메인 모델 (Coupon · MemberCoupon)
**라벨**: `type:feature`, `domain:coupon`, `M`
**선행**: #C-0

**작업 내용**
- [ ] `Coupon`·`MemberCoupon` 엔티티 + `CouponDiscountType`/`CouponStatus`/`MemberCouponStatus` enum
- [ ] Repository + Flyway V14 적용
- [ ] `Coupon.calculateDiscount(subtotal)` (정액/정률/캡/최소금액)
- [ ] 상태 머신 `canTransitionTo`
- [ ] `COUPON_*` ErrorCode 추가

**완료 조건**
- [ ] 할인 계산 `@ParameterizedTest`(정액·정률·캡·최소금액 경계) 통과
- [ ] 상태 전이 위반 거부 검증

### #C-2 (GH #90) [feature] 선착순 쿠폰 발급 + 동시성 (헤드라인)
**라벨**: `type:feature`, `domain:coupon`, `L`
**선행**: #C-1

**목적**: 초과발급 없이 정확히 한정수량만 발급. 베이스라인→비관적 락→원자적 UPDATE 단계 시연.

**작업 내용**
- [ ] `CouponIssueService` — 베이스라인(레이스) + 비관적 락 + 원자적 조건부 UPDATE
- [ ] `POST /coupons/{id}/issue` (USER, Idempotency-Key) + `GET /coupons` + `GET /members/me/coupons`
- [ ] `UNIQUE(coupon_id, member_id)` 중복발급 방지
- [ ] 동시성 테스트(Testcontainers): 초과발급 재현(`@Disabled` 보존) + 원자적 UPDATE 정확성 검증

**완료 조건**
- [ ] 원자적 UPDATE 경로에서 `발급수 == total_quantity` (초과발급 0)
- [ ] 중복 요청 시 한 장만 발급

### #C-3 (GH #91) [feature] 주문/결제 쿠폰 통합 + 취소·환불 복원
**라벨**: `type:feature`, `domain:coupon`, `domain:order`, `L`
**선행**: #C-2

**작업 내용**
- [ ] `Order.discountAmount` + `getPayableAmount()`, `OrderCreateRequest.memberCouponId`, V15 적용
- [ ] `OrderService.place` 쿠폰 검증·적용(USED 전이), 게스트 거부
- [ ] `PaymentService` 청구액 `getPayableAmount()` 로 변경
- [ ] `OrderService.cancel` + `AdminOrderService` 환불 시 쿠폰 복원(USED→ISSUED)
- [ ] 통합테스트(발급→쿠폰주문→결제 청구액→취소/환불 복원)

**완료 조건**
- [ ] 쿠폰 주문의 결제 금액 = payable
- [ ] 취소·환불 양 경로 모두 쿠폰 복원 검증

### #C-4 (GH #92) [feature] 관리자 쿠폰 CRUD · 직접지급 · 만료 스케줄러
**라벨**: `type:feature`, `domain:coupon`, `domain:admin`, `M`
**선행**: #C-3

**작업 내용**
- [ ] `POST·GET /admin/coupons`, `PATCH /admin/coupons/{id}/status`, `POST /admin/coupons/{id}/grant`
- [ ] 직접지급(선착순 한정수량과 별개, `member_coupon` INSERT)
- [ ] 만료 스케줄러(ISSUED 중 `expires_at < now` → EXPIRED, `IdempotencyRecordCleanupTask` 패턴)

**완료 조건**
- [ ] 관리자 CRUD/grant 정상 + 권한 검증(ADMIN)
- [ ] 만료 배치 동작 검증

### #C-5 (GH #93) [improvement] k6 발급 부하 측정 + Before/After + 커버리지 게이트
**라벨**: `type:improvement`, `domain:coupon`, `M`
**선행**: #C-4

**목적**: DoD #4(k6)·#5(Before/After) 충족.

**작업 내용**
- [ ] k6 발급 부하 스크립트(베이스라인/비관적/원자적 3종 비교)
- [ ] [troubleshooting/coupon-issuance-concurrency.md](troubleshooting/coupon-issuance-concurrency.md) §3/§6 실측치 채움
- [ ] `build.gradle.kts` JaCoCo 게이트에 `com.groove.coupon.*` 80% 편입
- [ ] README 에 Before/After 사례 기재

**완료 조건**
- [ ] 3종 전략의 TPS·정확성 비교표 완성
- [ ] coupon 패키지 라인 커버리지 80% 통과

---

## 확장 — 데모 프론트엔드 (M14 → M15)

> W1~W12 본 로드맵 외 **확장**. 백엔드 전 기능을 브라우저에서 시연하기 위한 데모 UI. node-gradle 통합 빌드로 단일 JAR 에 묶여 정적 서빙된다(ARCHITECTURE §10.4).

### M14 — 데모 프론트엔드 (정적 Bootstrap, ✅ 완료 → 폐기)
- **✅ 완료 (2026-06-02) 후 M15 로 대체**: 바닐라 JS + Bootstrap 으로 인증·카탈로그·주문·결제·배송·리뷰·관리자 콘솔 UI 구현(GH #102~#110). 유지보수성·상태관리 한계로 폐기하고 Vue 로 재구축.

### M15 — Vue 프론트엔드 (✅ 완료)
- **✅ 완료 (2026-06-03)**: **Vue 3 + Vite + JS + Pinia + Tailwind v4** 로 전 기능 재구축(GH #113~#119, PR #120~#130). History 라우팅(`SpaForwardConfig` 필요) + node-gradle Gradle 빌드 통합.
- 시연 GOTCHA: 주문 `status` PAID 정체 → 리뷰는 관리자 DELIVERED 전환 후 작성.
- 쿠폰 발급은 고객용 선착순 `발급받기` 플로우만 둔다(인앱 "동시성 라이브 데모"는 은퇴). 동시성·부하 검증은 실 운영 방식대로 `loadtest/` k6 스파이크 + `CouponIssuanceConcurrencyTest` 로 한다.

---

## 4. 위험 신호 + 컷 정책

### 신호 1: W3 말 G1 미달
- **원인 가능성**: Spring Boot 4.0.x 의존성 호환성 이슈, Docker 셋업 미숙
- **대응**: W4 시작 전 1~2일 추가. 그래도 안 되면 의존성 일부 다운그레이드 (Spring Boot 3.x 마지막 안정 버전)

### 신호 2: W7 말 G2 미달
- **원인 가능성**: W4~W6 어딘가에서 일정 누적 지연
- **컷 우선순위**:
  1. 리뷰 도메인 컷 (#W7-7) — 배송 완료 시연만 유지
  2. 관리자 도메인 일부 컷 (CRUD만 남기고 환불·통계 제외)
  3. 게스트 주문 컷 (회원 흐름만 유지)
- **컷 결정은 W7 중반(W7-3 끝나는 시점)에 미리 판단**
- **갱신(W7 완료 검증)**: G2 통과. 컷 우선순위 2번 중 "관리자 주문 조작(조회/상태 강제 전환/환불)" 은 컷하지 않고 **#69 로 분리해 M8(W8) 범위에서 진행** (PRD §6.9 핵심 기능). 통계/대시보드만 후순위로 남김.

### 신호 3: W8 말 커버리지 50% 미만
- **원인 가능성**: 통합 테스트 작성 시간 과소평가
- **대응**: 핵심 흐름(주문/결제) 통합 테스트만 우선 보장. 단위 테스트는 도메인 로직만 한정

### 신호 4: W9 말 발견 문제 1건 이하
- **원인 가능성**: 시드 데이터 부족 또는 시나리오 단순함
- **대응**: 시드 데이터 추가 + 시나리오 강화 1~2일. 그래도 부족하면 한정반 동시성 단일 사례에 집중

### 신호 5: W11 말 개선 사례 1건 이하
- **대응**: W12 일부를 사용해 가장 임팩트 큰 1건만 마무리. 문서화 시간 압축. W11-3~5 옵션 항목 모두 컷.

---

## 5. 자가 회고 체크리스트 (매주 금요일)

매주 말 다음 5가지를 30분 내에 자문한다. 결과는 GitHub Discussions 또는 `docs/retrospective/W{N}.md`에 짧게 기록.

1. **이번 주 닫은 이슈는 몇 개인가?** 미완 이슈는 다음 주에 어떻게 흡수할지 명시.
2. **다음 주 작업이 명확한가?** 막연한 이슈가 있다면 일요일에 풀어둔다.
3. **블로커가 있는가?** 있다면 즉시 해소 방법(검색·문서·커뮤니티)을 정한다.
4. **컷해야 할 것이 있는가?** 일정에 영향을 주는 항목을 미리 표시한다.
5. **README/ADR에 기록할 의사결정/트러블슈팅이 있는가?** 있다면 즉시 짧게라도 적는다 (W12에 몰아 쓰면 90% 잊는다).

---

## 6. 진행 표기 (포트폴리오 외부 노출용)

GitHub README 상단에 진행 상황 표기:

```
핵심 로드맵: ▓▓▓▓▓▓▓▓▓▓▓░ W1~W11 완료 (G2·G3 통과, CS 개선 완료)
확장:        쿠폰(M13) · 데모 프론트(M14→M15 Vue) 완료
남은 로드맵:  W12 문서/시연(G4) 단 하나
```

각 마일스톤은 GitHub [Milestones](https://github.com/heygw44/groove/milestones) 에서 자동 진행률 계산되므로 README 갱신 부담이 적다. 완료 마일스톤(**M1~M11, M13~M15**)은 closed, 남은 로드맵(**M12** 문서화)만 open 으로 유지한다. (M8~M11 은 산하 이슈 100% 종료 시점에 closed 처리.)

---

## 7. 부록 — 이슈 작성 시 팁

- **제목은 동사로 시작**하지 않는다. `[domain] 짧은 명사구` 형식이 GitHub 목록에서 가독성 좋음
  - ✅ `[auth] JWT Provider + JwtAuthenticationFilter`
  - ❌ `JWT Provider 만들기`
- **DoD는 측정 가능하게**: "잘 동작한다" 대신 "X 시나리오에서 Y 응답"
- **이슈 1개 = PR 1개 = 머지 1번**. 작업이 커지면 이슈를 쪼갠다
- **작업 시작 시 본인에게 Assign**, 진행 중에는 `in-progress` 라벨
- **Closes #N** 키워드를 PR 본문 또는 커밋에 포함하면 머지 시 자동 종료
