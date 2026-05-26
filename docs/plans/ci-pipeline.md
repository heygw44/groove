# 계획: CI 파이프라인 (GitHub Actions)

| 항목 | 값 |
|---|---|
| 상태 | **구현 완료** — `.github/workflows/ci.yml` (#86) |
| 작성일 | 2026-05-22 (구현 2026-05-26) |
| 라벨 | `type:infra`, effort `S`~`M` |
| 목적 | PR마다 build + test + JaCoCo 커버리지 게이트를 자동 실행해, "실 운영 수준 백엔드"라는 프로젝트 철학의 마지막 구멍(자동 검증 부재)을 메운다 |
| 관련 문서 | [MILESTONE.md](../MILESTONE.md) §0.3 (브랜치/PR 규칙), build.gradle.kts (JaCoCo 게이트) |

> **왜 이 문서가 있는가**: 기능(W1~W7)은 완성됐고 로컬 `./gradlew check`로 검증 가능하나, **PR 단계의 자동 게이트가 없다**. `.github/`에 PR·이슈 템플릿은 있지만 `workflows/`가 비어 있어, 테스트·커버리지 미달 PR이 기계적으로 막히지 않는다. 가성비가 가장 높은 인프라 보강이라 우선순위 후보로 남긴다.

---

## 1. 현재 상태 (사실 확인 완료 — 2026-05-22)

- 빌드: **Gradle 9.4.1 wrapper**(`./gradlew`), **Java 21 toolchain**, Spring Boot 4.0.6, JaCoCo 0.8.13
- `./gradlew build` 한 번이 컴파일 → test → `jacocoTestReport` → `jacocoTestCoverageVerification`(= `check` 경유)까지 수행
- 커버리지 게이트: `com.groove.auth.* / member.* / catalog.*` 패키지 **라인 80%** (build.gradle.kts `jacocoTestCoverageVerification`)
- 테스트는 **Testcontainers MySQL** 사용 (`src/test/java/com/groove/support/TestcontainersConfig.java`) → 러너에 **Docker 필요**
- **test 프로파일이 시크릿 자급자족** (`application-test.yaml`에 `jwt.secret`, `payment.mock.webhook-secret` 내장) → **CI에 외부 GitHub Secrets 불필요**
- `.github/` 에 `PULL_REQUEST_TEMPLATE.md`, `ISSUE_TEMPLATE/*` 존재, **`workflows/` 없음**

## 2. 설계 결정 & Why

| 결정 | 선택 | 근거 / 트레이드오프 |
|---|---|---|
| 러너 | `ubuntu-latest` | Docker 사전 설치 → Testcontainers 그대로 동작. macOS/Windows 러너는 Docker 미보장 |
| MySQL 제공 방식 | **Testcontainers 그대로** (워크플로우에 `services: mysql` 추가 **금지**) | 테스트가 컨테이너 수명을 직접 관리. GitHub `services:`로 띄우면 포트/자격이 이중화돼 충돌. 로컬과 동일 경로 유지가 핵심 |
| JDK 제공 | `actions/setup-java` (temurin 21) | Gradle toolchain auto-provisioning보다 빠르고 결정적. 캐시도 연동 |
| 빌드 캐시 | `gradle/actions/setup-gradle` | 의존성·빌드 캐시 자동 관리로 시간 단축 |
| 실행 커맨드 | `./gradlew build` | `build`→`check`→test+커버리지 게이트가 한 번에. 게이트 미달 시 비제로 종료로 CI 빨강 |
| 시크릿 | **주입 안 함** | test 프로파일 자급자족 확인됨. 운영 시크릿을 CI에 두지 않아 노출면 0 |
| 커버리지 리포트 | 아티팩트 업로드 + (선택) PR 코멘트 | 서드파티 코멘트 액션은 권한 최소화 + commit SHA 핀 권장 |
| 동시성 | PR당 in-progress 취소 | 푸시 연타 시 러너 분 절약 |
| 권한 | `contents: read` 기본, PR 코멘트 시에만 `pull-requests: write` | 최소 권한 원칙 |

## 3. 작업 내용 (체크리스트)

- [ ] `.github/workflows/ci.yml` 신규 작성 (§5 예시 기반)
- [ ] 트리거: `pull_request` → `main`, `push` → `main`
- [ ] `actions/checkout` → `actions/setup-java`(temurin 21) → `gradle/actions/setup-gradle`
- [ ] `./gradlew build` 실행 (test + 커버리지 게이트 포함)
- [ ] 실패해도(`if: always()`) 테스트 리포트(`build/reports/tests/`)·JaCoCo HTML(`build/reports/jacoco/`) 아티팩트 업로드
- [ ] (선택) JaCoCo PR 코멘트 액션 추가 — 권한 `pull-requests: write`, 액션 SHA 핀
- [ ] `concurrency` 그룹으로 PR당 이전 실행 취소
- [ ] 1회 PR로 그린 확인 후 → **main 브랜치 보호 규칙에 CI 상태 체크 "필수"로 등록** (GitHub Settings, 코드 아님 / 별도 후속)
- [ ] README에 CI 배지 추가 (W8-5 / W12-1 README 작업과 합류 가능)

## 4. 완료 조건 (DoD)

- [ ] PR 생성·갱신 시 CI가 자동 실행되어 PR 체크에 통과/실패가 표시됨
- [ ] 테스트 실패 **또는** 커버리지 게이트(auth/member/catalog 80%) 미달 시 CI 빨강
- [ ] 테스트 리포트·JaCoCo HTML이 실패 시에도 아티팩트로 회수됨
- [ ] main 브랜치 보호 규칙에 CI 필수 체크 등록됨
- [ ] 외부 시크릿 없이 CI가 그린 (test 프로파일 자급자족 검증)

## 5. 예시 워크플로우 (`.github/workflows/ci.yml`)

```yaml
name: CI

on:
  pull_request:
    branches: [main]
  push:
    branches: [main]

concurrency:
  group: ci-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest   # Docker 사전 설치 → Testcontainers 동작
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Build, test & coverage gate
        run: ./gradlew build --no-daemon
        # build → check → test(Testcontainers MySQL) → jacocoTestCoverageVerification
        # 외부 시크릿 불필요: SPRING_PROFILES_ACTIVE=test 가 자급자족 (jwt.secret/webhook-secret 내장)

      - name: Upload test & coverage reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: reports
          path: |
            build/reports/tests/
            build/reports/jacoco/
          retention-days: 7
```

### (선택) 커버리지 PR 코멘트 추가 시
별도 job 또는 step으로 `Madrapps/jacoco-report` 류 사용. 이때만 job 레벨에 추가:
```yaml
    permissions:
      contents: read
      pull-requests: write
```
서드파티 액션은 태그 대신 **commit SHA로 핀**해 공급망 위험을 줄인다.

## 6. 함정 / 주의

- **`services: mysql` 넣지 말 것**: 테스트는 Testcontainers로 MySQL을 직접 띄운다. 서비스 컨테이너를 추가하면 `application-test.yaml`의 `@ServiceConnection` 경로와 충돌·혼선.
- **첫 실행은 느릴 수 있음**: Testcontainers가 MySQL 이미지를 pull. Gradle 캐시(setup-gradle)로 의존성은 줄지만 Docker 이미지 pull 시간은 남는다. 필요 시 이미지 캐시 전략 추가 검토(초기엔 불필요).
- **`fork PR`의 시크릿/권한**: 본 CI는 시크릿이 없어 fork PR도 안전하게 돈다. 커버리지 코멘트 액션 도입 시 fork PR에서 `pull-requests: write`가 제한될 수 있음 — 단독 포트폴리오라 보통 무관.
- **toolchain auto-download**: setup-java로 21을 명시하므로 Gradle toolchain이 별도로 JDK를 내려받지 않게 일치시킬 것.

## 7. 향후 확장 (이 이슈 범위 아님)

- 빌드 결과를 Docker 이미지로 빌드·푸시 (GHCR) — 배포 데모용
- `dependency-check` / Dependabot 결과를 CI 게이트로 (MILESTONE #W12-5 보안 점검과 합류)
- 커버리지 게이트를 주문·결제 도메인으로 확장(현 80% 범위는 auth/member/catalog) — MILESTONE #W8-1과 함께
