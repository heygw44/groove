# ADR: JWT 라이브러리 선정

| 항목 | 값 |
|---|---|
| 상태 | Accepted |
| 날짜 | 2026-05-06 |
| 연관 이슈 | #9 (W3-6 G1 게이트 점검 + W4 준비) |
| 작성자 | ParkGunWoo |
| 후속 작업 | #W4-2 JWT 발급/검증 모듈 구현 |

---

## Context

W4(인증/회원) 도메인에서 Access Token 과 Refresh Token 의 발급·검증·회전을 구현해야 한다. ARCHITECTURE.md ADR-5 에서 Stateless JWT 기반 인증을 채택했고, PRD/API 명세상 만족해야 할 요건은 다음과 같다.

| 항목 | 값 |
|---|---|
| 서명 알고리즘 | HS256 (대칭키) |
| Access Token TTL | 30분 (`JWT_ACCESS_TOKEN_TTL_SECONDS=1800`) |
| Refresh Token TTL | 14일 (`JWT_REFRESH_TOKEN_TTL_SECONDS=1209600`) |
| 회전 정책 | Refresh 사용 시 신규 Access + Refresh 재발급 |
| 페이로드 | `sub`, `iat`, `exp`, `roles` (단일 문자열 클레임) |
| JWE / 비대칭 키 | 미사용 (단일 서비스, 외부 IdP 없음) |

JWT 를 직접 구현하면 타임 비교, 알고리즘 혼동, base64url 처리 같은 데서 보안 위험이 크다. 그래서 검증된 외부 라이브러리를 쓰는 쪽으로 일찌감치 방향을 잡았고, 남은 건 "어느 라이브러리냐"였다.

---

## Decision

`io.jsonwebtoken:jjwt` (0.12.x) 를 채택한다. 런타임 의존성은 다음처럼 나눈다.

```kotlin
implementation("io.jsonwebtoken:jjwt-api:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6") // Jackson 직렬화 (Spring Boot 기본)
```

이유를 추리면 이렇다. 이 프로젝트는 HS256 단일 알고리즘만 쓰고 JWE·JWK·OIDC 흐름이 없으니, API 가 단순한 jjwt 가 딱 맞는다. Nimbus JOSE 는 JWE/JWS/JWT/JWK 를 다 아우르는 범용 라이브러리라 우리 스코프엔 표면적이 너무 넓다. jjwt 0.12.x 는 fluent builder 와 `JwtParser` 가 분리된 설계라 발급·검증 코드가 명료하다. Spring Security 의 `oauth2-resource-server` 를 들이는 건 JwkSet·이슈어 검증·리소스 서버 설정 같은 복잡성을 프로젝트 규모에 비해 과하게 떠안는 일이다.

---

## Considered Options

### Option A — JJWT (`io.jsonwebtoken:jjwt`) ★ 채택

HS256 + 단일 시크릿 케이스에 최적화돼 있어 발급·검증 코드가 짧고 의도가 분명하다. Jackson 모듈은 Spring Boot 가 이미 포함하므로 추가 비용도 없다. 0.x 버전대라 마이너 API 변경 가능성이 있고(0.11→0.12 에서 builder API 가 바뀐 이력이 있다), Nimbus 보다 알고리즘 폭은 좁지만 우리 쓰임새엔 영향이 없다.

| 항목 | 내용 |
|---|---|
| 버전 / 라이선스 | 0.12.6 (2024+) / Apache 2.0 |
| Java 21 · Spring Boot 4.x | 호환 ○ |
| API | fluent builder, `Jwts.builder()`, `Jwts.parser()` |
| 의존성 무게 | api + impl + jackson (impl/jackson 은 runtimeOnly) |
| 알고리즘 커버리지 | HS/RS/ES/PS, EdDSA + JWE 가능(불필요) |
| 유지보수 / 문서 | 활발 (월 단위 릴리스, stars 9k+), 공식 README·Javadoc 충실 |

### Option B — Nimbus JOSE + JWT (`com.nimbusds:nimbus-jose-jwt`)

OIDC·JWE·JWK 회전 같은 고급 시나리오에 강하고, Spring Security 의 `oauth2-resource-server` 가 내부적으로 쓰는 라이브러리라 나중에 그쪽으로 옮길 때 정합성이 좋다. 하지만 단순 HS256 발급·검증만 놓고 보면 명시적 객체(`SignedJWT`·`JWSSigner`·`JWSVerifier`)를 다루는 보일러플레이트가 많고, 우리가 쓰지도 않을 기능까지 학습·문서화 부담이 따라온다.

| 항목 | 내용 |
|---|---|
| 버전 / 라이선스 | 9.40+ / Apache 2.0 |
| API | 명시적 객체(`SignedJWT`, `JWSSigner`, `JWSVerifier`) |
| 알고리즘 커버리지 | JWS + JWE + JWK + JWT 전 영역 (JWE 강점) |
| 단점 | 단순 HS256 대비 보일러플레이트 과다 |

### Option C — Spring Security OAuth2 Resource Server (`spring-boot-starter-oauth2-resource-server`)

표준 OIDC/JWT 검증을 가장 적은 코드로 구성해 주는 추상화(JwtDecoder·JwkSet·Issuer URI)다. 외부 IdP/AuthServer 연동이나 JWK 회전이 필요한 환경에 어울린다. 그런데 우리는 자체 발급 + HS256 + 단일 서비스라 JwkSet/Issuer 검증 흐름이 오히려 짐이 되고, Refresh 토큰 로직은 어차피 직접 짜야 해서 starter 의 이점이 크지 않다.

| 항목 | 내용 |
|---|---|
| 의존성 | Nimbus JOSE 내부 사용 |
| 추상화 수준 | 매우 높음 (JwtDecoder, JwkSet, Issuer URI) |
| 적합 시나리오 | 외부 IdP/AuthServer 연동, JWK 회전, OIDC |

---

## Consequences

**긍정적**
- HS256 + Access/Refresh 회전 구현이 짧은 코드로 끝나 W4-2 일정이 단축된다.
- 서명·검증·예외 처리 보일러플레이트가 최소라 보안 결함이 끼어들 여지가 줄어든다.
- 의존성이 가벼워 Docker 이미지 크기에 거의 영향이 없다.

**부정적 / 트레이드오프**
- 나중에 JWE 나 외부 IdP 연동이 필요해지면 라이브러리 교체나 병행 도입을 다시 검토해야 한다. 그때 이 ADR 을 Superseded 처리한다.
- Spring Security 의 `JwtAuthenticationFilter` 표준 흐름과 별개로 커스텀 필터를 작성하게 되므로, OAuth2 표준 리소스 서버 흐름과는 차이가 있다.

**보안 고려사항 (구현 시점에 적용)**
- `JWT_SECRET` 은 32바이트(256비트) 이상으로 강제 검증한다(`Keys.hmacShaKeyFor(...)` 시 길이가 부족하면 즉시 예외).
- `parseSignedClaims` 를 쓴다(서명 없는 토큰을 막기 위해 `parseUnsecuredClaims` 금지) — jjwt 0.12 빌더 API.
- clock skew 는 30초로 명시한다(`Jwts.parser().clockSkewSeconds(30)`, `JwtProvider.CLOCK_SKEW_SECONDS`).
- 시크릿은 환경변수로만 주입한다(`application*.yaml` 평문 보관 금지).

---

## References

- [JJWT GitHub](https://github.com/jwtk/jjwt)
- [JJWT 0.12.x Migration Guide](https://github.com/jwtk/jjwt#breaking-changes-in-0120)
- [Nimbus JOSE + JWT](https://connect2id.com/products/nimbus-jose-jwt)
- [Spring Security Reference — OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- 연관 ARCHITECTURE: [../ARCHITECTURE.md](../ARCHITECTURE.md) ADR-5 JWT 채택
- 연관 이슈: #9 (G1 게이트), #W4-2 (JWT 발급/검증 모듈)
