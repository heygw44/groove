# Bruno API 테스트 컬렉션

전체 엔드포인트의 요청·응답 검증 스크립트와 엣지 케이스를 담은 [Bruno](https://www.usebruno.com/) 컬렉션입니다. 요청당 `.bru` 평문 파일이라 git에서 그대로 diff·리뷰됩니다.

| 경로 | 내용 |
|------|------|
| [`bruno/`](.) | 전체 엔드포인트 + 응답 검증 스크립트 + 환경변수 자동 저장 (요청당 `.bru` 1개) |
| [`environments/Groove Local.bru`](environments/Groove%20Local.bru) | `Groove Local` 환경 — `./gradlew bootRun`(직접 `:8080`) 대상(baseUrl·토큰·관리자 계정 등) |
| [`environments/Groove Compose.bru`](environments/Groove%20Compose.bru) | `Groove Compose` 환경 — `docker-compose up`(nginx 리버스 프록시 `http://localhost`) 대상 |

> **사전 조건**: 백엔드가 떠 있고 데모 시드가 적재돼 있어야 합니다. 대상에 따라 환경을 고릅니다 — `./gradlew bootRun`(직접 `:8080`)은 **Groove Local**, `docker-compose up`(nginx 경유 `http://localhost`)은 **Groove Compose**. `local` 프로파일로 기동하면 `LocalDataSeeder`가 앨범 12장·데모 회원(`demo@groove.dev`)·**관리자(`admin@groove.dev` / `admin1234`)** 를 자동 생성합니다. 관리자 요청은 이 시드 계정으로 로그인합니다(환경변수 `adminEmail`/`adminPassword`).
>
> ⚠️ **웹훅 폴더를 Compose 대상으로 돌릴 때**는 **Groove Compose** 의 `webhookSecret` 변수를 실행 중인 `.env` 의 `PAYMENT_MOCK_WEBHOOK_SECRET` 와 같은 값으로 맞춰야 서명 검증이 통과합니다.

## Bruno GUI

1. Bruno 앱에서 `bruno/` 폴더를 열고(Open Collection) 우상단에서 대상에 맞는 환경(`Groove Local` 직접 `:8080` / `Groove Compose` nginx `http://localhost`)을 선택합니다.
2. `1. Auth > Login` 실행 → `accessToken` 자동 저장(refresh 토큰은 HttpOnly 쿠키로 내려가 쿠키 jar에 보관, #163).
3. `6. Member Flow (E2E) ★` 폴더를 위에서부터 순서대로 실행하면 장바구니 → 주문 → 결제 → 배송 → 리뷰 흐름이 1클릭으로 이어집니다.
   - 리뷰 작성(`8) Write Review`)은 주문이 `DELIVERED` 이상이어야 201입니다. 결제 PAID(Mock 웹훅/폴링 자동) 후 `8. Admin > Change Order Status`에서 `target`을 `PREPARING` → `SHIPPED` → `DELIVERED`로 순차 전환해야 통과합니다(그 전에는 422).

> 재사용 시 401이 나면 `bru.cookies.jar().clear()`로 쿠키를 비웁니다.

## CLI (헤드리스 / @usebruno/cli)

```bash
npm install              # @usebruno/cli 설치 (루트 devDependency)
npm run test:bruno       # 결정론적 happy-path 만 실행 (Health/Auth/Catalog/Cart/MemberFlow/Guest/EdgeCases)
npm run test:bruno:admin     # 8. Admin (시드 관리자 계정 필요)
npm run test:bruno:full      # 파괴적 제외 전체 — 재실행 가능
npm run test:bruno:cleanup   # 10. Cleanup (비밀번호 변경·회원 탈퇴, 파괴적 — 마지막에만)
npm run test:bruno:ratelimit # 11. Rate Limit (429 검증, 로그인 버킷 소진 — 격리 실행)
npm run test:bruno:report    # test:bruno 와 동일 범위 + JUnit 리포트(reports/bruno-junit.xml)
```

> 폴더 선택은 각 요청 `meta.tags`의 카테고리(health/auth/…/cleanup/ratelimit)로 모델링했습니다. 위 스크립트는 `bru run`의 `--tags`/`--exclude-tags`로 해당 폴더 집합을 고릅니다.

- **`test:bruno`** 는 외부(DB 시드) 상태에만 의존하는 폴더를 실행합니다(Member Flow → Edge Cases 순서로 결제 멱등 등 직전 단계 상태를 그대로 사용). 데모 시드가 있으면 전부 통과하고, 로그인 버킷을 소진하지 않으므로 1분 내 반복 실행해도 깨지지 않습니다.
- **`test:bruno:full`** 은 파괴적 폴더(`10. Cleanup`)와 격리 폴더(`11. Rate Limit`)를 제외하므로 같은 시드 DB에서 반복 실행해도 깨지지 않습니다. 비밀번호 변경·회원 탈퇴는 `test:bruno:cleanup`으로 따로 돌립니다(실행하면 고정 회원 계정이 변경/삭제되므로 이후 재실행 전 DB 재시드가 필요합니다).
- **Rate Limit 검증**(`11. Rate Limit`)은 `POST /auth/login` IP당 분당 10회 제한을 씁니다. 요청의 pre-request가 본 호출 전 `bru.sendRequest`로 10회 선요청해 버킷을 소진시키므로, 이 요청 1회 실행으로 429 + `Retry-After`가 반드시 반환됩니다. 단, 로그인 버킷을 비우므로 happy-path와 섞지 말고 `npm run test:bruno:ratelimit`로 격리 실행합니다(버킷은 1분 뒤 리필).
- `5. Coupons > Issue Coupon`은 발급 가능한 ACTIVE 쿠폰(환경변수 `couponId` 기본값 `1`, 데모 시드 또는 `8. Admin > Create Coupon Policy`)이 선행돼야 하고, `Sign Up`은 분당 3회 제한이라 1분 내 4회 이상 연속 재실행 시 429가 날 수 있습니다(테스트는 429도 허용합니다).

자세한 엔드포인트 계약은 [docs/API.md](../docs/API.md)를 참고하세요.
