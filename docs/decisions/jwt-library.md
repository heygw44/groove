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

W4(인증/회원) 도메인에서 Access Token + Refresh Token 발급·검증·회전을 구현해야 한다.

ARCHITECTURE.md ADR-5에서 **Stateless JWT 기반 인증**을 채택했고, PRD/API 명세상 다음 요건을 만족해야 한다.

| 항목 | 값 |
|---|---|
| 서명 알고리즘 | HS256 (대칭키) |
| Access Token TTL | 30분 (`JWT_ACCESS_TOKEN_TTL_SECONDS=1800`) |
| Refresh Token TTL | 14일 (`JWT_REFRESH_TOKEN_TTL_SECONDS=1209600`) |
| 회전 정책 | Refresh 사용 시 신규 Access + Refresh 재발급 |
| 페이로드 | `sub`, `iat`, `exp`, `roles` (단일 문자열 클레임) |
| JWE / 비대칭 키 | 미사용 (단일 서비스, 외부 IdP 없음) |

JWT 자체 구현은 보안 위험(타임 비교, 알고리즘 혼동, base64url 처리 등)이 크므로 검증된 외부 라이브러리를 채택한다.

---

## Decision

**`io.jsonwebtoken:jjwt` (0.12.x) 채택.**

런타임 의존성은 다음과 같이 분리한다.

```kotlin
implementation("io.jsonwebtoken:jjwt-api:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6") // Jackson 직렬화 (Spring Boot 기본)
```

근거 요약:
- 본 프로젝트는 HS256 단일 알고리즘만 사용하며 JWE/JWK/OIDC 흐름이 없다 → API 단순한 jjwt가 적합.
- Nimbus JOSE는 JWE/JWS/JWT/JWK 전 영역을 다루는 범용 라이브러리로, 본 스코프 대비 표면적이 과도하다.
- jjwt 0.12.x는 fluent builder + `JwtParser` 분리 설계로 발급/검증 코드가 명료하다.
- Spring Security `oauth2-resource-server` 도입은 본 프로젝트 규모 대비 과한 복잡성(JwkSet, 이슈어 검증, 리소스 서버 설정)을 들인다.

---

## Considered Options

### Option A — JJWT (`io.jsonwebtoken:jjwt`) ★ 채택

| 항목 | 내용 |
|---|---|
| 버전 | 0.12.6 (2024+) |
| 라이선스 | Apache 2.0 |
| Java 21 / Spring Boot 4.x 호환 | ○ |
| API | fluent builder, `Jwts.builder()`, `Jwts.parser()` |
| 의존성 무게 | api + impl + jackson 3개 (impl/jackson은 runtimeOnly) |
| 알고리즘 커버리지 | HS/RS/ES/PS, EdDSA |
| JWE 지원 | ○ (불필요하지만 가능) |
| 유지보수 | 활발 (월 단위 릴리스, GitHub stars 9k+) |
| 문서 | 공식 README + Javadoc 충실 |

**장점**
- 발급/검증 코드가 짧고 의도 명확.
- HS256 + 단일 시크릿 케이스에 최적화된 사용성.
- Jackson 모듈을 Spring Boot가 이미 포함하므로 추가 비용 없음.

**단점**
- 0.x 버전대 지속 → API 마이너 변경 가능성 (0.11 → 0.12에서 builder API 변경 이력 있음).
- Nimbus 대비 알고리즘 폭은 좁다 (본 프로젝트 영향 없음).

### Option B — Nimbus JOSE + JWT (`com.nimbusds:nimbus-jose-jwt`)

| 항목 | 내용 |
|---|---|
| 버전 | 9.40+ |
| 라이선스 | Apache 2.0 |
| Java 21 / Spring Boot 4.x 호환 | ○ |
| API | 명시적 객체(`SignedJWT`, `JWSSigner`, `JWSVerifier`) |
| 의존성 무게 | 단일 jar, 다만 BouncyCastle 등 선택 의존성 |
| 알고리즘 커버리지 | JWS + JWE + JWK + JWT 전 영역 |
| JWE 지원 | ◎ (강점) |
| 유지보수 | 활발 (Connect2id 상용 백엔드) |
| 문서 | 풍부하나 케이스가 광범위해 진입 장벽 |

**장점**
- OIDC, JWE, JWK 회전 등 고급 시나리오 대응 강력.
- Spring Security `oauth2-resource-server`가 내부적으로 사용 → 향후 마이그레이션 정합성.

**단점**
- 단순 HS256 발급/검증 대비 보일러플레이트가 많다.
- 본 프로젝트가 사용하지 않을 기능까지 학습/문서화 부담.

### Option C — Spring Security OAuth2 Resource Server (`spring-boot-starter-oauth2-resource-server`)

| 항목 | 내용 |
|---|---|
| 의존성 | Nimbus JOSE 내부 사용 |
| 추상화 수준 | 매우 높음 (JwtDecoder, JwkSet, Issuer URI 등) |
| 적합 시나리오 | 외부 IdP/AuthServer 연동, JWK 회전, OIDC |

**장점**
- 표준 OIDC/JWT 검증을 가장 적은 코드로 구성.

**단점 (탈락 사유)**
- 본 프로젝트는 자체 발급 + HS256 + 단일 서비스. JwkSet/Issuer 검증 흐름이 오히려 부담.
- Refresh 토큰 로직은 어차피 직접 구현해야 함 → starter의 이점이 작다.

---

## Consequences

**긍정적**
- HS256 + Access/Refresh 회전 구현이 짧은 코드로 끝나 W4-2 일정 단축.
- 서명·검증·예외 처리 보일러플레이트 최소화로 보안 결함 발생 여지 감소.
- 의존성이 가벼워 Docker 이미지 크기 영향 미미.

**부정적 / 트레이드오프**
- 추후 JWE 또는 외부 IdP 연동이 필요해지면 라이브러리 교체 또는 병행 도입을 재검토해야 한다 → 그때 본 ADR을 Superseded 처리.
- Spring Security의 `JwtAuthenticationFilter` 표준 흐름과는 별도로 커스텀 필터를 작성하게 되므로, OAuth2 표준 토큰 자원 서버 흐름과는 차이가 있다.

**보안 고려사항 (구현 시점에 적용)**
- `JWT_SECRET`은 32바이트(256비트) 이상으로 강제 검증 (`Keys.hmacShaKeyFor(...)` 시 길이 부족 즉시 예외).
- `parseClaimsJws` 사용 (서명 미포함 토큰을 차단하기 위해 `parseClaimsJwt` 금지).
- `clock skew`는 60초로 명시 (`JwtParserBuilder.setAllowedClockSkewSeconds(60)`).
- 시크릿은 환경변수만으로 주입(`application*.yaml`에 평문 보관 금지).

---

## References

- [JJWT GitHub](https://github.com/jwtk/jjwt)
- [JJWT 0.12.x Migration Guide](https://github.com/jwtk/jjwt#breaking-changes-in-0120)
- [Nimbus JOSE + JWT](https://connect2id.com/products/nimbus-jose-jwt)
- [Spring Security Reference — OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- 연관 ARCHITECTURE: [../ARCHITECTURE.md](../ARCHITECTURE.md) ADR-5 JWT 채택
- 연관 이슈: #9 (G1 게이트), #W4-2 (JWT 발급/검증 모듈)
