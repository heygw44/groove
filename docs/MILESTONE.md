# 마일스톤 & 이슈 가이드: Groove

| 항목 | 값 |
|---|---|
| 버전 | 1.1 (이슈 단위 확장) |
| 작성일 | 2026-05-05 |
| 진행 기간 | 12주 (단독, 전업) |
| 주당 가용 시간 | 35~45시간 |
| 표기 방식 | 상대 (W1 ~ W12) |
| 관련 문서 | PRD.md, ARCHITECTURE.md, ERD.md, API.md |

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

| 단계 | 주차 | 핵심 산출물 | 게이트 | 이슈 수 |
|---|---|---|---|---|
| 설계 | W1~W2 | PRD·아키텍처·ERD·API 명세 확정 | — | 5 |
| 인프라/스켈레톤 | W3 | Docker Compose, 패키지·Security 베이스 | **G1** | 6 |
| 도메인 구현 | W4~W7 | 인증→카탈로그→장바구니/주문→결제/배송/리뷰 | **G2** | 24 |
| 통합/시드 | W8 | 통합 테스트, 시드 데이터, Postman 컬렉션 | — | 5 |
| 측정 | W9 | k6 시나리오, 베이스라인, 문제 식별 | **G3** | 6 |
| CS 개선 | W10~W11 | N+1·인덱스·동시성·멱등성 + Before/After | — | 8 |
| 문서화 | W12 | README, 트러블슈팅, 시연 자료 | **G4** | 5 |

**총 이슈 수: 약 60개** (W11 옵션 포함 시 65개)

---

## 2. 마일스톤 게이트 (자가 점검)

각 게이트는 **다음 단계 진입 가능 여부**를 판단하는 시점이다.

### G1 — 인프라 게이트 (W3 말) ✅ 통과 (2026-04 무렵)
- [x] `docker-compose up -d` 한 번으로 앱 + MySQL 정상 기동
- [x] `/actuator/health` 200 OK 응답
- [x] Spring Security FilterChain 동작 (보호된 엔드포인트 401 응답 확인)
- [x] Flyway 초기 마이그레이션 적용됨
- [x] GlobalExceptionHandler가 ProblemDetail로 응답

### G2 — 기능 완성 게이트 (W7 말) ★ 가장 중요
- [ ] 회원가입 → 로그인 → 토큰 갱신 동작
- [ ] 상품 검색·필터·페이징 동작
- [ ] 장바구니 → 주문 → 결제 → 웹훅 → 배송 → 리뷰 E2E 흐름 통과
- [ ] 게스트 주문 흐름 동작
- [ ] 관리자 상품·주문 조작 동작
- [ ] 결제 실패·보상 트랜잭션 동작

### G3 — 측정 게이트 (W9 말)
- [ ] k6 시나리오 4개 작성 및 동작
- [ ] 모든 시나리오의 베이스라인 수치 기록
- [ ] 발견된 문제 3건 이상 명시
- [ ] W10~W11 개선 작업 계획 확정

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
- [ ] `Cart`, `CartItem` 엔티티 + Repository
- [ ] Flyway 마이그레이션
- [ ] `GET /cart` (회원 자동 생성)
- [ ] `POST /cart/items` (수량 누적)
- [ ] `PATCH /cart/items/{itemId}`, `DELETE /cart/items/{itemId}`, `DELETE /cart`
- [ ] 비활성 상품 검증 (HIDDEN/SOLD_OUT 거부)

**완료 조건**
- [ ] 모든 CRUD 동작
- [ ] 같은 상품 재추가 시 수량 누적
- [ ] 비활성 상품 추가 시 422

---

### #W6-2 [order] Order 엔티티 + 상태 머신
**라벨**: `type:feature`, `domain:order`, `M`
**선행**: #W6-1

**작업 내용**
- [ ] `Order`, `OrderItem` 엔티티
- [ ] `OrderStatus` enum + `canTransitionTo()` 메서드
- [ ] Flyway 마이그레이션
- [ ] `Order.changeStatus(next)` 도메인 메서드 (전이 검증)
- [ ] 단위 테스트 (모든 합법/불법 전이)

**완료 조건**
- [ ] 합법 전이만 허용, 불법 전이는 `IllegalStateTransitionException`
- [ ] 단위 테스트로 전이 표 전수 검증

---

### #W6-3 [order] 주문 생성 API (회원 + 게스트, 단순 재고 차감)
**라벨**: `type:feature`, `domain:order`, `L`
**선행**: #W6-2

**작업 내용**
- [ ] `POST /api/v1/orders` 회원/게스트 분기
- [ ] 게스트 정보 검증 (email + phone)
- [ ] 다중 상품 처리 + OrderItem 가격 스냅샷
- [ ] 트랜잭션 내 재고 검증 + 차감 (※ **락 없이 단순 구현** — W10 시연 시작점)
- [ ] orderNumber 발급 (예: `ORD-20260505-XXXX`)
- [ ] 통합 테스트 (정상/재고부족/비활성상품)

**완료 조건**
- [ ] 회원/게스트 모두 주문 생성 성공
- [ ] 재고 부족 시 409
- [ ] 단일 스레드 환경에서 정합성 OK (동시성 문제는 #W6-6에서 재현)

---

### #W6-4 [order] 주문 조회 + 취소 API
**라벨**: `type:feature`, `domain:order`, `M`
**선행**: #W6-3

**작업 내용**
- [ ] `GET /orders/{orderNumber}` (회원 본인)
- [ ] `POST /orders/{orderNumber}/guest-lookup` (게스트, email 매칭)
- [ ] `GET /members/me/orders` (회원 주문 목록, 페이징)
- [ ] `POST /orders/{orderNumber}/cancel` (PENDING 한정, 재고 복원)
- [ ] 권한 검증 (타인 주문 조회 시 404)

**완료 조건**
- [ ] 모든 조회 시나리오 동작
- [ ] 취소 시 재고 복원 검증

---

### #W6-5 [test] 장바구니 + 주문 통합 테스트
**라벨**: `type:test`, `domain:order`, `M`
**선행**: #W6-4

**작업 내용**
- [ ] 장바구니 → 주문 생성 E2E
- [ ] 게스트 주문 시나리오
- [ ] 주문 취소 + 재고 복원
- [ ] 권한 검증

**완료 조건**
- [ ] 주문 도메인 라인 커버리지 75%+

---

### #W6-6 [test] 동시성 테스트 — 오버셀 재현 (시연 자료 보존)
**라벨**: `type:test`, `domain:order`, `M`
**선행**: #W6-3

**목적**: W10 시연용 "Before" 상태 명확히 기록

**작업 내용**
- [ ] CountDownLatch + ExecutorService 기반 동시 주문 테스트
- [ ] 재고 100짜리 상품에 동시 200 요청 → 오버셀 발생 검증
- [ ] 결과 로그 / 스크린샷 보존 (`docs/troubleshooting/overselling-baseline.md`)

**완료 조건**
- [ ] 오버셀 재현 테스트가 의도적으로 실패하는 형태로 보존됨 (`@Disabled` + 주석으로 W10 개선 시 활성화 명시)
- [ ] 또는 별도 시연 디렉토리에 별도 보존

---

## W7 — 결제 + 배송 + 리뷰 (M7) ★ G2 게이트

**주차 목표**: 핵심 흐름 E2E 완성. 12주 중 가장 무거운 주차.

### #W7-1 [payment] PaymentGateway 인터페이스 + Mock 구현체
**라벨**: `type:feature`, `domain:payment`, `M`
**선행**: #W6-6

**작업 내용**
- [ ] `PaymentGateway` 인터페이스 (`request`, `query`, `refund`)
- [ ] DTO: `PaymentRequest`, `PaymentResponse`, `RefundRequest`, `RefundResponse`
- [ ] `MockPaymentGateway` 구현체 (`@Profile`로 격리)
- [ ] 처리 지연 시뮬레이션 (랜덤 100~500ms)
- [ ] 성공률 설정 가능 (`payment.mock.success-rate`)
- [ ] `MockWebhookSimulator`: 1~5초 후 비동기 웹훅 콜백 발사

**완료 조건**
- [ ] Strategy 패턴 정상 동작 (실 PG 추가 시 구현체만 교체 가능)
- [ ] Mock PG 호출 후 비동기 웹훅 수신 확인

---

### #W7-2 [payment] IdempotencyService + 멱등성 레코드 테이블
**라벨**: `type:feature`, `domain:payment`, `M`
**선행**: #W7-1

**작업 내용**
- [ ] `IdempotencyRecord` 엔티티 + Repository
- [ ] Flyway 마이그레이션
- [ ] `IdempotencyService.execute(key, supplier)` (락 + 결과 캐싱)
- [ ] `Idempotency-Key` 헤더 검증 인터셉터 또는 어노테이션
- [ ] TTL 정리 스케줄러 (24시간)

**완료 조건**
- [ ] 동일 키 + 처리 완료 시 기존 응답 반환
- [ ] 처리 중 동일 키 시 409
- [ ] 키 미지정 시 400

---

### #W7-3 [payment] 결제 요청 API + 상태 조회
**라벨**: `type:feature`, `domain:payment`, `L`
**선행**: #W7-2

**작업 내용**
- [ ] `Payment` 엔티티 + `PaymentStatus` enum
- [ ] Flyway 마이그레이션
- [ ] `POST /api/v1/payments` (Idempotency-Key 필수)
  - 주문 상태 검증 (PENDING)
  - PG 호출 → Payment(PENDING) 저장
  - 응답 202 + paymentId
- [ ] `GET /api/v1/payments/{paymentId}` 상태 조회
- [ ] 통합 테스트

**완료 조건**
- [ ] 결제 요청 정상 처리 (PENDING 응답)
- [ ] 멱등성 정상 동작

---

### #W7-4 [payment] 웹훅 콜백 + 보상 트랜잭션 + 폴링 스케줄러
**라벨**: `type:feature`, `domain:payment`, `L`
**선행**: #W7-3

**작업 내용**
- [ ] `POST /api/v1/payments/webhook` 엔드포인트
- [ ] 서명 검증 (Mock에서는 단순 헤더 매칭)
- [ ] PAID/FAILED 분기 처리
- [ ] 멱등성 (동일 webhook 중복 수신 시 무시)
- [ ] 실패 시 보상 트랜잭션: Payment FAILED + Order PAYMENT_FAILED + 재고 복원
- [ ] 폴링 스케줄러: PENDING 결제 N분마다 PG `query()` 호출 → 동기화

**완료 조건**
- [ ] 정상 웹훅 처리 시 Order PAID 전환
- [ ] 실패 웹훅 시 보상 트랜잭션 정상 동작
- [ ] 중복 웹훅 무해함

---

### #W7-5 [order] OrderPaidEvent + AFTER_COMMIT 리스너
**라벨**: `type:feature`, `domain:order`, `M`
**선행**: #W7-4

**작업 내용**
- [ ] `OrderPaidEvent` 정의
- [ ] Order 결제 완료 처리 시 이벤트 발행
- [ ] Spring Application Event + `@TransactionalEventListener(AFTER_COMMIT)`
- [ ] (배송 도메인 #W7-6에서 구독)

**완료 조건**
- [ ] 트랜잭션 롤백 시 이벤트 미발행 검증
- [ ] AFTER_COMMIT 시점에 리스너 호출 확인

---

### #W7-6 [shipping] Shipping 도메인 + 자동 진행 스케줄러
**라벨**: `type:feature`, `domain:shipping`, `L`
**선행**: #W7-5

**작업 내용**
- [ ] `Shipping` 엔티티 + `ShippingStatus` enum
- [ ] Flyway 마이그레이션
- [ ] `OrderPaidEvent` 구독 → Shipping(PREPARING) 생성 + 운송장 번호 발급(UUID)
- [ ] `GET /api/v1/shippings/{trackingNumber}` 조회 API
- [ ] `@Scheduled` 진행 스케줄러: PREPARING → SHIPPED → DELIVERED (시연용 짧은 간격)
- [ ] 안전 포장 요청 플래그 처리

**완료 조건**
- [ ] 결제 완료 → 배송 자동 생성
- [ ] 스케줄러로 상태 자동 진행 확인

---

### #W7-7 [review] Review 도메인 (작성/조회/삭제)
**라벨**: `type:feature`, `domain:review`, `M`
**선행**: #W7-6

**작업 내용**
- [ ] `Review` 엔티티 + Repository
- [ ] Flyway V_n 마이그레이션
- [ ] `POST /api/v1/reviews` (DELIVERED 검증, 1주문-1상품-1리뷰 제약)
- [ ] `GET /api/v1/albums/{id}/reviews` (페이징, 회원명 마스킹)
- [ ] `DELETE /api/v1/reviews/{reviewId}` (본인만)
- [ ] Album 평균 평점 / 리뷰 수 계산 로직

**완료 조건**
- [ ] 모든 API 정상 동작
- [ ] 배송 완료 전 리뷰 시 422
- [ ] 중복 리뷰 시 409

---

### #W7-8 [test] 결제 / 배송 E2E 통합 테스트
**라벨**: `type:test`, `domain:payment`, `L`
**선행**: #W7-7

**작업 내용**
- [ ] E2E 시나리오: 회원가입 → 로그인 → 장바구니 → 주문 → 결제 → 웹훅 → 배송 → 리뷰
- [ ] 게스트 주문 E2E
- [ ] 결제 실패 시 보상 트랜잭션 검증
- [ ] 멱등성 검증 (동일 키 동시 요청)
- [ ] G2 게이트 점검

**완료 조건**
- [ ] G2 게이트 모든 항목 통과 ★

---

## W8 — 통합 테스트 강화 + 시드 + Postman (M8)

**주차 목표**: 신뢰성 있는 코드베이스 + 측정 가능한 데이터셋 확보

### #W8-1 [test] 통합 테스트 보강 (커버리지 60%+)
**라벨**: `type:test`, `M`
**선행**: #W7-8

**작업 내용**
- [ ] JaCoCo 리포트 분석 → 미커버 영역 식별
- [ ] 핵심 도메인(주문/결제/인증) 커버리지 80% 이상
- [ ] 엣지 케이스 추가 (잘못된 입력, 권한 부족, 상태 위반)

**완료 조건**
- [ ] 전체 라인 커버리지 60%+
- [ ] 핵심 도메인 80%+

---

### #W8-2 [test] 단위 테스트 강화 (도메인 로직)
**라벨**: `type:test`, `M`
**선행**: #W8-1

**작업 내용**
- [ ] 상태 전이 메서드 단위 테스트 (Order, Payment, Shipping)
- [ ] 검증 규칙 단위 테스트
- [ ] BCrypt, JWT 등 보안 모듈 단위 테스트

**완료 조건**
- [ ] 모든 도메인 로직 단위 테스트 존재

---

### #W8-3 [chore] 시드 데이터 스크립트 작성
**라벨**: `type:chore`, `L`
**선행**: #W1-3

**작업 내용**
- [ ] `db/seed/` 디렉토리
- [ ] Genre 10~15건, Label 50~100건, Artist 1k~3k건
- [ ] Album 5만~10만 건 (다양한 가격·연도·장르 분포)
- [ ] 한정반(`is_limited=true`) 30~50건
- [ ] 단일 재고(stock=1) 5~10건
- [ ] 테스트 회원 50~100명 (k6 다중 사용자용)
- [ ] ADMIN 계정 1개
- [ ] 시드 적용 스크립트 (Spring CLI 또는 Docker exec)

**완료 조건**
- [ ] `./scripts/seed.sh` 한 번으로 시드 적용
- [ ] 검색 슬로우 쿼리 재현 가능 (EXPLAIN 풀 스캔 확인)

---

### #W8-4 [docs] Postman 컬렉션 완성
**라벨**: `type:docs`, `M`
**선행**: #W7-8

**작업 내용**
- [ ] 모든 엔드포인트 요청 등록
- [ ] 환경 변수 자동 저장 스크립트 (로그인 응답 → accessToken/refreshToken)
- [ ] E2E 시나리오 폴더 (회원 흐름, 게스트 흐름, 관리자 흐름)
- [ ] Edge Cases 폴더 (Idempotency, Rate Limit, 상태 위반)
- [ ] 응답 검증 스크립트 (필수 필드 확인)
- [ ] 컬렉션 export → `postman/groove.collection.json` 커밋

**완료 조건**
- [ ] Postman으로 회원 E2E 흐름 1클릭 시연 가능

---

### #W8-5 [docs] README v1 작성
**라벨**: `type:docs`, `M`
**선행**: #W8-3, #W8-4

**작업 내용**
- [ ] 프로젝트 한 줄 소개 + 차별화 포인트
- [ ] 빠른 시작 (`docker-compose up` + 시드)
- [ ] 아키텍처 다이어그램 임베드
- [ ] API 문서 링크 (Swagger / API.md)
- [ ] 기술 스택 표

**완료 조건**
- [ ] 처음 보는 사람이 5분 안에 프로젝트 시작 가능

---

## W9 — 측정 (M9) ★ G3 게이트

**주차 목표**: 무엇을 개선할지 데이터로 결정

### #W9-1 [loadtest] k6 환경 셋업 + search.js 시나리오
**라벨**: `type:test`, `M`
**선행**: #W8-5

**작업 내용**
- [ ] `loadtest/` 디렉토리 + k6 Docker 실행 스크립트
- [ ] 공통 헬퍼 (auth 토큰 발급, 시드 사용자 풀)
- [ ] `search.js` — 다양한 필터 조합 검색 부하

**완료 조건**
- [ ] `k6 run loadtest/search.js` 정상 실행 + JSON 결과 출력

---

### #W9-2 [loadtest] order.js + payment.js 시나리오
**라벨**: `type:test`, `M`
**선행**: #W9-1

**작업 내용**
- [ ] `order.js` — 주문 생성 부하 (다중 상품)
- [ ] `payment.js` — 결제 + 멱등성 검증 (동일 키 재요청)
- [ ] 결과 비교용 표 양식 정의

**완료 조건**
- [ ] 두 시나리오 정상 실행

---

### #W9-3 [loadtest] flash-sale.js 시나리오 (한정반 동시 주문) ★
**라벨**: `type:test`, `L`
**선행**: #W9-2

**작업 내용**
- [ ] `flash-sale.js` — 한정반 상품(재고 100) 동시 1000 요청
- [ ] 정합성 검증 로직 (성공 응답 수 + DB 최종 재고 매칭)
- [ ] 다양한 부하 단계(100, 500, 1000) 시나리오 옵션
- [ ] **이 시점 결과는 오버셀 발생** (시연 Before 자료)

**완료 조건**
- [ ] 오버셀이 측정으로 재현됨 (성공 응답 > 100)
- [ ] 결과 JSON 보존

---

### #W9-4 [measurement] N+1 측정 + 슬로우 쿼리 EXPLAIN
**라벨**: `type:test`, `M`
**선행**: #W8-5

**작업 내용**
- [ ] Hibernate Statistics 활성화 (테스트 환경)
- [ ] 상품 목록 조회 시 발생 쿼리 수 측정 (예: `1+N`)
- [ ] 검색 쿼리 EXPLAIN 결과 캡처 (풀 스캔 확인)
- [ ] 결과 정리 → `docs/measurement/baseline.md`

**완료 조건**
- [ ] N+1 발생 수치 기록됨
- [ ] EXPLAIN 결과 캡처 보존

---

### #W9-5 [measurement] 베이스라인 측정 결과 정리
**라벨**: `type:docs`, `M`
**선행**: #W9-3, #W9-4

**작업 내용**
- [ ] 모든 시나리오 베이스라인 (TPS, p50/p95/p99, 에러율) 표 작성
- [ ] 발견된 문제 목록 + 우선순위
- [ ] W10~W11 개선 작업 계획 확정

**완료 조건**
- [ ] G3 게이트 통과
- [ ] 개선 작업 우선순위가 데이터로 뒷받침됨

---

### #W9-6 [chore] 측정 이슈 트래킹 정리
**라벨**: `type:chore`, `S`
**선행**: #W9-5

**작업 내용**
- [ ] 발견된 문제별로 W10/W11 이슈 신규 생성 (#W10-N 형태)
- [ ] 각 이슈에 베이스라인 수치 첨부

**완료 조건**
- [ ] 개선 이슈 모두 등록됨

---

## W10 — CS 개선 1차 (M10)

**주차 목표**: 가장 임팩트 큰 3가지 개선 — Before/After 그래프로 시연 가능

### #W10-1 [improvement] N+1 해결 (페치 조인 / @EntityGraph)
**라벨**: `type:improvement`, `domain:catalog`, `M`
**선행**: #W9-6

**작업 내용**
- [ ] 상품 목록 조회 → 페치 조인 또는 `@EntityGraph` 적용
- [ ] Hibernate Statistics 재측정 (예: 401 → 1)
- [ ] 응답 시간 비교
- [ ] `docs/improvements/n-plus-one.md` 작성 (Before/After 코드 + 측정)

**완료 조건**
- [ ] 쿼리 수 측정 결과 README에 표/그래프로 기록
- [ ] 코드 diff 보존

---

### #W10-2 [improvement] 검색 인덱스 추가 (Flyway V6)
**라벨**: `type:improvement`, `domain:catalog`, `M`
**선행**: #W10-1

**작업 내용**
- [ ] `V6__add_search_indexes.sql` 작성 (ERD §5.2 인덱스 후보)
- [ ] 적용 후 EXPLAIN 결과 비교 (풀 스캔 → 인덱스 사용)
- [ ] k6 search.js 재실행 → 응답 시간 비교
- [ ] `docs/improvements/search-index.md`

**완료 조건**
- [ ] EXPLAIN 결과 Before/After 명확
- [ ] 응답 시간 개선 수치 기록

---

### #W10-3 [improvement] 동시성 — 비관적 락 적용 + flash-sale 재측정
**라벨**: `type:improvement`, `domain:order`, `L`
**선행**: #W10-2

**작업 내용**
- [ ] 재고 차감 부분에 비관적 락 (`SELECT ... FOR UPDATE`) 적용
- [ ] 동시성 테스트 재실행 → 오버셀 0건 검증
- [ ] k6 flash-sale.js 재측정 (TPS, 에러율, 응답시간)
- [ ] `docs/improvements/concurrency.md` (단계별 비교)
  - 단계 a: 락 없음 (오버셀 발생)
  - 단계 b: 비관적 락 (정합성 OK, TPS 측정)
  - (시간 여유 시 c: 낙관적 락 또는 Redis 분산락 비교)

**완료 조건**
- [ ] 오버셀 0건 측정 검증
- [ ] TPS·응답시간 Before/After 표 작성

---

### #W10-4 [docs] W10 개선 사례 README 정리
**라벨**: `type:docs`, `M`
**선행**: #W10-1 ~ #W10-3

**작업 내용**
- [ ] README에 "성능 개선 사례" 섹션 추가
- [ ] 3개 개선 모두 Before/After 표 + 그래프
- [ ] 트레이드오프 명시 (왜 비관적 락 선택했는지, Redis 미도입 이유)

**완료 조건**
- [ ] 개선 사례 3건 모두 README에 시연 가능한 형태로 기록

---

## W11 — CS 개선 2차 (M11)

**주차 목표**: 정합성 시연 마무리, 시간 여유 시 추가 개선

### #W11-1 [test] 결제 멱등성 통합 테스트
**라벨**: `type:test`, `domain:payment`, `M`
**선행**: #W10-4

**작업 내용**
- [ ] 동시 동일 Idempotency-Key 요청 → 단일 결제 생성 검증
- [ ] 웹훅 중복 수신 → 상태 전이 1회 검증
- [ ] 멱등성 키 만료 후 재사용 시나리오
- [ ] `docs/improvements/idempotency.md`

**완료 조건**
- [ ] 모든 멱등성 시나리오 통합 테스트 통과

---

### #W11-2 [chore] 코드 클린업 + TODO/FIXME 정리
**라벨**: `type:chore`, `M`
**선행**: #W10-4

**작업 내용**
- [ ] 전체 TODO/FIXME 주석 검토
- [ ] 미사용 코드/임포트 정리
- [ ] 메서드/클래스 네이밍 일관성 점검
- [ ] 의존성 정리 (사용 안 하는 starter 제거)

**완료 조건**
- [ ] TODO/FIXME 0건 또는 명시적 v2 표시
- [ ] `./gradlew check` 경고 최소화

---

### #W11-3 [improvement] 단일 재고 희귀반 시연 (선택)
**라벨**: `type:improvement`, `priority:optional`, `M`
**선행**: #W10-3

**작업 내용**
- [ ] stock=1 상품에 동시 100 요청 → 정확히 1건 성공
- [ ] 측정 데이터 보존 + README 보강

**완료 조건**
- [ ] 시연 자료 추가됨

---

### #W11-4 [improvement] Redis 분산락 비교 (선택)
**라벨**: `type:improvement`, `priority:optional`, `L`
**선행**: #W10-3

**작업 내용**
- [ ] Redis + Redisson 의존성 추가
- [ ] Docker Compose에 Redis 추가
- [ ] InventoryService에 분산락 옵션 추가
- [ ] 비관적 락 vs 분산락 측정 비교
- [ ] 트레이드오프 문서화

**완료 조건**
- [ ] 두 방식 측정 비교표 작성

---

### #W11-5 [improvement] Virtual Threads 활성화 + 영향 측정 (선택)
**라벨**: `type:improvement`, `priority:optional`, `M`
**선행**: #W11-2

**작업 내용**
- [ ] `spring.threads.virtual.enabled=true` 또는 명시 설정
- [ ] 동일 시나리오 측정 비교
- [ ] 결과에 따라 채택 여부 결정

**완료 조건**
- [ ] 측정 데이터 기록
- [ ] 채택/미채택 의사결정 기록

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
Progress: ▓▓▓▓▓▓▓▓░░░░ 8/12 weeks
M1~M12 마일스톤 진행률은 GitHub Milestones 페이지 참조
```

각 마일스톤은 GitHub Milestones에서 자동 진행률 계산되므로 README 갱신 부담이 적다.

---

## 7. 부록 — 이슈 작성 시 팁

- **제목은 동사로 시작**하지 않는다. `[domain] 짧은 명사구` 형식이 GitHub 목록에서 가독성 좋음
  - ✅ `[auth] JWT Provider + JwtAuthenticationFilter`
  - ❌ `JWT Provider 만들기`
- **DoD는 측정 가능하게**: "잘 동작한다" 대신 "X 시나리오에서 Y 응답"
- **이슈 1개 = PR 1개 = 머지 1번**. 작업이 커지면 이슈를 쪼갠다
- **작업 시작 시 본인에게 Assign**, 진행 중에는 `in-progress` 라벨
- **Closes #N** 키워드를 PR 본문 또는 커밋에 포함하면 머지 시 자동 종료
