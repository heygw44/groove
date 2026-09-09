# 한정반 구매 운영 부하 재측정 (2026-09-09)

로컬(`limited-20260908.md`)까지만 잰 한정반 구매 시나리오를 처음으로 운영 환경(EC2)에 대고
돌린 결과다. 50 → 200 → 500 → 1000 VU 로 단계를 올리며 어디서 무너지는지, 무너질 때 초과
판매가 나는지를 확인했다.

## 이 측정이 답하는 질문 / 답하지 못하는 질문

답하는 것:
- 운영 환경에서 동시 구매가 몰릴 때 초과 판매가 나는가.
- 어느 VU 구간에서 요청이 실패하기 시작하는가, 그리고 그 실패가 애플리케이션(JVM) 때문인지
  앞단(Nginx) 때문인지.
- 로컬 대비 순수 네트워크 왕복이 얼마나 차지하는가.

답하지 못하는 것:
- 1000 VU 에서 `worker_connections` 를 올리면 어디까지 버티는지. 설정을 바꿔 재측정하지
  않았다.
- 실패한 139건이 정확히 어느 시점에 끊겼는지. access.log 에 기록이 없어 Nginx 가 슬롯을
  못 잡고 끊은 것으로 추정할 뿐, alert 로그 외에 확증은 없다.
- 운영 환경에서 Redis ON/OFF 비교. 이번 측정 범위 밖이다(로컬 비교는 `limited-20260904.md`).
- 단일 클라이언트 1대에서 낸 부하라, 여러 지역·여러 클라이언트에서 오는 실제 트래픽 형태와는
  다르다.

## 프로토콜

- 대상: EC2 운영 서버(`groove-lp.duckdns.org`). 배포본은 `main` 의 `aafa76b`(이미지 태그
  `BACKEND_IMAGE_TAG=aafa76b...`). 측정을 위해 서버 설정을 바꾸지 않았다.
- 시나리오: `scripts/k6/limited-purchase.js`, VU 50 / 200 / 500 / 1000 4단계를 순서대로 실행.
  단계 재고는 VU 의 절반을 상한으로 둔다(50 VU 는 재고 25, 200/500/1000 VU 는 재고 100).
  재고가 VU 보다 많으면 전원이 성공해도 재고가 남아 "성공 수 == 재고" 판정이 구조적으로
  불가능해진다 — 1차 시도에서 이 규칙을 지키지 않아 한 번 헛돌았다.
- 실행: 단계 순회·자원 수집·판정·요약을 `scripts/k6/prod-run.sh` 가 한 번에 한다.

```bash
# scripts/k6/.env.prod 에 ADMIN_EMAIL / ADMIN_PASSWORD 를 두고(chmod 600, gitignore 대상)
scripts/k6/prod-run.sh
```

각 단계 종료 직후, 쿨다운 전에 `verify-oversell.sh` 로 초과 판매를 판정한다. 한정반 구매는
PENDING 주문이라 만료 스케줄러가 10분 뒤 `limited_purchase` 를 지우고 재고를 되돌리기 때문에,
판정 유효 시간은 k6 종료 직후부터 10분 이내다.

## 결과

### 단계별 `purchase` 지연/처리량 (태그 `name:purchase`, 단위 ms 단, RPS 는 req/s)

| VU | 재고 | min | p50 | p90 | p95 | p99 | max | waiting p50 | waiting p95 | 성공(201) | 품절(409) | 서버에러(5xx) | 총요청 | 실패율 | RPS |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 50 | 25 | 86.1 | 499.9 | 676.5 | 752.9 | 793.9 | 804.0 | 38.5 | 577.5 | 25 | 25 | 0 | 210 | 0% | 16.2 |
| 200 | 100 | 65.0 | 1106.1 | 2343.4 | 2485.7 | 2694.0 | 2759.4 | 58.0 | 1985.3 | 100 | 100 | 0 | 810 | 0% | 31.3 |
| 500 | 100 | 152.1 | 10603.6 | 11422.5 | 11576.9 | 11862.4 | 11980.6 | 118.5 | 11175.4 | 100 | 400 | 0 | 2010 | 0% | 35.0 |
| 1000 | 100 | 0 | 3367.7 | 4249.9 | 4479.3 | 4730.3 | 4919.5 | 194.4 | 4054.0 | 100 | 683 | 217 | 4510 | 4.81% | 29.6 |

**500 VU p95(11.6s)가 1000 VU p95(4.5s)보다 크다고 "1000 VU 가 더 빠르다"로 읽으면 안 된다.**
1000 VU 에서는 217건이 실패해 통계에서 빠졌고, 살아남아 응답을 받은 요청만 집계된 생존 편향이다.
실패율이 0%인 500 VU 와 4.81%인 1000 VU 의 p95 는 애초에 같은 모집단을 재고 있지 않다.
1000 VU 행의 min 0 도 같은 이유로, 응답을 받지 못한 요청이 0ms 로 기록된 값이다.

### 단계별 EC2 자원 피크 (1초 간격 샘플)

| VU | 샘플수 | backend CPU max(%) | backend RSS max(MB) | available min(MB) | swap max(MB) | cpu steal max(%) | tcp estab max | tcp timewait max |
|---|---|---|---|---|---|---|---|---|
| 50 | 16 | 166.4 | 362.1 | 104 | 552 | 5.4 | 159 | 389 |
| 200 | 29 | 173.9 | 382.1 | 76 | 565 | 1.4 | 789 | 1275 |
| 500 | 63 | 177.2 | 355.7 | 104 | 769 | 33.5 | 1992 | 2874 |
| 1000 | 154 | 180.7 | 364.0 | 58 | 780 | 75.3 | 3063 | 2909 |

backend CPU 160~180%는 대부분 러시(구매 트래픽) 구간이 아니라 setup 단계, 즉 회원 N명의
signup/login 에서 BCrypt 해싱이 도는 구간이다. 2 vCPU 를 이 구간이 혼자 채운다. CPU 그래프를
구매 러시의 지표로 오독하면 안 된다.

cpu steal 은 500 VU 에서 33.5%, 1000 VU 에서 75.3%까지 올랐다. 다만 500 VU 는 실패 0건이었으므로
steal 이 실패의 원인이라고 단정할 근거는 없다. 관측된 사실로만 남긴다.

### 네트워크 기준선 (로컬 → 운영, `/api/v1/health`, VPN 해제 후)

| 구간 | 값 |
|---|---|
| conn | 16.6~17.6ms |
| TLS(appconnect) | 35.3~40.0ms |
| TTFB | 52.1~58.4ms |

3회 측정 전후 모두 동일 수준. 네트워크 왕복 바닥은 약 53ms 이고, 이 중 서버 처리분은
TTFB - TLS ≈ 17ms 다. 이 항목은 로컬 측정(`limited-20260904.md`, `limited-20260908.md`)에는
없던 것이므로, 로컬 p95(802ms/1.25s)와 운영 p95 를 그냥 나란히 놓고 비교하면 안 된다.
비교하려면 순수 처리 시간에 가까운 `http_req_waiting` 을 같이 봐야 한다.

VPN(utun4) 경유 시 같은 측정: conn 55~60ms / TLS 78~83ms / TTFB 100~106ms. 이 경로로는 정상
측정이 되지 않는다(아래 "폐기한 1차 측정" 참고).

### 1000 VU 실패 217건의 내역

- Nginx access.log 의 purchase 요청: 201=100건, 409=683건, 500=78건 (합 861건)
- k6 가 관측한 실패: connection reset by peer 131건 + EOF 8건 = 139건 (access.log 에는 기록 없음)
- 78 + 139 = 217 = k6 가 센 `purchase_server_error` 수와 정확히 일치
- 861 + 139 = 1000 = 전체 구매 시도 수와 정확히 일치

### Nginx 증거

- `768 worker_connections are not enough while connecting to upstream` alert 199건. 구매 요청
  경로를 그대로 지목한다.
- 설정: `worker_processes auto`(=2), `worker_connections 768`. 프록시 요청 1건이 클라이언트+
  업스트림 2슬롯을 소비한다.
- 업스트림 keepalive 없음(upstream 블록 자체가 없음) → 요청마다 새 TCP 연결, TIME_WAIT 최대 2909.
- 반대 증거(JVM/커널 쪽은 무사): 커널 TCP 큐 카운터(`TcpExtListenOverflows`, `TcpExtListenDrops`,
  `TcpExtTCPBacklogDrop`) 전부 0. 컨테이너 `OOMKilled=false`, `RestartCount 0`(backend/mysql/
  redis 전부). Redis `evicted_keys 0`(사용량 1.3MB / maxmemory 64MB).

### 초과판매 판정 (`verify-oversell.sh`, 매 단계 k6 종료 직후 실행)

| VU | `limited_purchase` 건수 | `stock.quantity` | `sold_count` | Redis buyers | 판정 |
|---|---|---|---|---|---|
| 50 | 25 | 0 | 25 | 25 | PASS |
| 200 | 100 | 0 | 100 | 100 | PASS |
| 500 | 100 | 0 | 100 | 100 | PASS |
| 1000 | 100 | 0 | 100 | 100 | PASS |

네 단계 전부 4항목이 정확히 일치했다. 1000 VU 에서 21.7%의 요청이 실패하는 와중에도 초과
판매는 0건이다. 1000 VU 의 Redis `attempts` 해시에서 `SOLD_OUT` 사유가 683건으로, access.log
의 409 건수와 일치한다.

## 읽기

확정한 것:
- 네 단계 전부 초과 판매 0건. 이중 방어(Redis 선점 + DB 조건부 UPDATE)는 운영 환경, 1000 VU,
  21.7% 요청 실패라는 악조건에서도 정확히 재고 상한만큼만 판매를 허용했다.
- 1000 VU 에서 먼저 무너진 것은 애플리케이션(JVM)이 아니라 Nginx `worker_connections 768`
  이다. 실패 217건 = 78(5xx) + 139(연결 리셋/EOF)로 정확히 맞아떨어지고, alert 199건이 같은
  구간을 지목하며, 반대로 커널 TCP 큐·OOM·컨테이너 재시작·Redis eviction 은 전부 무사하다.
  JVM 쪽 자원 고갈의 증거가 없다.

추정인 것:
- cpu steal 이 1000 VU 에서 75.3%까지 오른 게 실패에 실제로 얼마나 기여했는지는 확정하지
  못했다. 500 VU 에서는 steal 33.5%에도 실패가 0건이었으므로, steal 단독으로 실패를
  설명하기는 어렵다. Nginx 슬롯 고갈이 주 원인이라는 쪽에 더 무게가 실리지만, steal 이
  전혀 무관하다고도 말할 수 없다.
- 실패 139건(연결 리셋/EOF)이 Nginx 가 슬롯을 못 잡아 끊은 것이라는 설명은 alert 로그
  199건과 정황상 맞물리지만, access.log 자체에는 이 139건의 기록이 없어 정확한 시점이나
  경로를 직접 확인하지는 못했다.

## 폐기한 1차 측정 (VPN 경유, 같은 날 11:30~11:43)

같은 스크립트로 먼저 잰 값. 로컬 → 서버 트래픽이 VPN 터널(utun4, 10.1.0.4)을 타고 있었다.

- 50 VU: p95 1668ms (VPN 해제 후 753ms)
- 200 VU: p95 3274ms, 구매 실패 8건. DB 는 100건 판매인데 클라이언트가 받은 201 은 93건.
- 500 VU: 구매 요청 500건이 서버에 하나도 도달하지 않음. Nginx access.log 무기록, 백엔드
  CPU 0.2%, Redis 재고 100 그대로. 같은 순간 SSH 세션도 끊김(Connection reset by peer). 서버
  TCP 큐 카운터는 전부 0.

VPN 을 해제한 뒤 500 VU 를 다시 돌리자 실패 0건으로 통과했다. 서버가 아니라 측정 장비(VPN
터널)가 병목이었다는 뜻이다. 그래서 이 1차 측정은 폐기하고, VPN 해제 후 값만 위 결과로
채택했다.

## 로컬 기존 측정 (참고)

- `scripts/k6/results/limited-20260904.md`: Apple M4/16GB, 컨테이너 백엔드 직접 호출, 1000 VU
  / 재고 100. p50 681ms, p95 802ms, p99 880ms, 실패율 0%, 초과판매 0. Redis ON/OFF 비교도 여기
  있다.
- `scripts/k6/results/limited-20260908.md`: 같은 시나리오 재측정 p95 1.94s → (락 최적화 후)
  1.25s.

로컬과 운영은 네트워크 왕복(약 53ms)과 인스턴스 사양이 다르므로 절대값을 직접 비교하지
않는다.

## 재현

VPN 이 켜져 있으면 반드시 끈다. 켜진 채로는 500 VU 이상에서 측정 자체가 무의미하다
(`prod-run.sh` 가 경로를 검사해 터널이면 거부한다).

```bash
scripts/k6/prod-run.sh --smoke   # VU 10 / 재고 3 으로 한 바퀴 확인
scripts/k6/prod-run.sh           # 50 → 200 → 500 → 1000
scripts/k6/cleanup-prod-loadtest.sh --drop-ids "<drop-ids.txt 내용>"          # dry-run
scripts/k6/cleanup-prod-loadtest.sh --drop-ids "<drop-ids.txt 내용>" --apply
```

`prod-run.sh` 가 단계마다 `monitor-remote.sh` 로 EC2 자원을 1초 간격 CSV 로 받고, k6 종료
직후 `verify-oversell.sh` 로 초과 판매를 판정한 뒤, 종료 시 Nginx alert/5xx·Redis
`evicted_keys`·컨테이너 재시작 수를 실행 전후 델타로 남긴다. 이 문서의 모든 수치는
`scripts/k6/results/prod-novpn/` 의 JSON 요약·CSV·`verify.txt`·`post-check.txt` 로 역추적된다
(결과 디렉토리는 gitignore 대상이라 로컬에만 있다).

측정이 끝나면 생성된 회원·상품·주문을 반드시 정리한다. 정리 대상은 접두사
(`k6lt-`, `LIMITED-LOADTEST-`)로만 좁혀지고, 이번 측정에서는 회원 1,000 / 상품 9 / 앨범 9 /
드롭 9 / 주문 503 을 지웠다.
