# k6 부하 테스트

## 목적

- `product-list.js` — 상품 목록 API(`GET /api/v1/products`) 부하 테스트. p95 300ms(NFR-03) 검증.
- `limited-purchase.js` — 한정반 선착순 구매 API(`POST /api/v1/limited-drops/{id}/purchase`) 부하 테스트. 초과 판매 0건(NFR-02)과 p95 1초 검증.

## 사전 준비

1. k6 설치: `brew install k6` (또는 https://k6.io/docs/get-started/installation/)
2. 백엔드까지 컨테이너로 띄운다: `docker compose --profile full up --build -d`
3. 헬스체크로 뜰 때까지 기다린다: `curl http://localhost:8080/actuator/health`
4. local 프로파일이므로 관리자 계정(`admin@groove.com` / `admin1234!`)이 시드되어 있어야 한다.

## 실행

웜업으로 소규모부터 확인한다.

```bash
VUS=50 STOCK=10 k6 run scripts/k6/limited-purchase.js
```

본 실행(기본값 VUS=1000, STOCK=100):

```bash
k6 run scripts/k6/limited-purchase.js
```

Redis ON/DB 락만 비교 실행. 백엔드 컨테이너를 `LIMITED_REDIS_ENABLED=false` 로 재기동한 뒤 같은 시나리오를 돌리고, 끝나면 원복한다.

```bash
LIMITED_REDIS_ENABLED=false docker compose --profile full up -d backend
k6 run scripts/k6/limited-purchase.js

# 원복
docker compose --profile full up -d backend
```

`docker compose up -d` 는 설정이 그대로면 컨테이너를 재사용한다. 콜드 상태에서 재측정하려면 `--force-recreate` 를 붙이고, 반대로 워밍업 뒤 본측정은 재기동 없이 이어서 돌린다. 호스트 8080 을 다른 프로세스가 쓰고 있으면 `BACKEND_PORT=18080 docker compose --profile full up -d backend` 로 포트를 옮기고 `BASE_URL=http://localhost:18080` 을 준다.

## 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | 대상 서버 |
| `VUS` | `1000` | 동시 구매를 시도할 가상 사용자 수(= 회원 수) |
| `STOCK` | `100` | 한정반 총 수량 |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `admin@groove.com` / `admin1234!` | local 시드 관리자 계정 |
| `OPEN_DELAY_SEC` | `8` | 회원 준비가 끝난 뒤 오픈 시각까지 두는 여유(초) |
| `MEMBER_PASSWORD` | `load1234!` | 테스트용으로 생성하는 회원 비밀번호 |
| `RESULT_DIR` | `scripts/k6/results` | 결과 JSON 저장 위치 |

## 판정 기준

- HTTP 201(성공) 개수 == `STOCK`
- 아래 쿼리로 실측 확인:

```bash
docker compose exec mysql mysql -ugroove -pgroove1234 groove -e "SELECT COUNT(*) FROM limited_purchase WHERE drop_id=<dropId>; SELECT quantity FROM stock WHERE product_id=<productId>; SELECT sold_count, status FROM limited_drop WHERE id=<dropId>;"
docker compose exec redis redis-cli GET limited:stock:<dropId>
docker compose exec redis redis-cli SCARD limited:buyers:<dropId>
```

- `limited_purchase` 행 수 == `STOCK`
- `stock.quantity` == 0
- Redis `limited:stock:{dropId}` == 0, `SCARD limited:buyers:{dropId}` == `STOCK`
- Redis OFF 모드로 돌린 실행에서는 위 두 Redis 키가 애초에 존재하지 않는다(정상).

## 결과 파일

- 실행마다 `results/limited-<YYYYMMDD-HHmmss>.json` 이 남는다(gitignore 대상, 로컬 보관용).
- 여러 번 실행해 비교했다면 `results/limited-YYYYMMDD.md` 로 정리한다. 이 마크다운만 예외적으로 커밋 추적 대상이다.

## 주의

- 컨테이너 백엔드는 `JAVA_TOOL_OPTIONS`(Dockerfile)로 `-Xmx384m` + SerialGC 제약을 받는다. VUS 를 크게 올리면 GC 압박으로 응답 지연이 커질 수 있다.
- `VUS=1000` 기준 setup 에서 로그인만 1000건이라 준비 단계가 수십 초 걸릴 수 있다. `setupTimeout: '10m'` 로 여유를 뒀다.
- 실행마다 새 상품/한정반을 만들고 지우지 않는다. 반복 실행하면 관리자 상품/한정반 목록에 `LIMITED-LOADTEST-*` 항목이 계속 쌓이므로, 필요하면 수동으로 정리한다.
