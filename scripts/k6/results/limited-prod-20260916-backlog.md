# 한정반 구매 운영 부하 재측정 — 접속 큐 3단 확장 후 (2026-09-16, #426)

`limited-prod-20260916.md`(이하 "1차")에서 Nginx 슬롯을 4096으로 풀자 다음 병목으로 Tomcat
리슨 백로그(`accept-count` 기본값 100)가 드러났다. #426에서 Nginx listen 백로그·컨테이너
SYN 큐·Tomcat accept-count 세 값을 전부 4096(Nginx는 2048)으로 맞춘 뒤, 그 조치가 실제로
오버플로를 없앴는지, 없앴다면 남는 지연은 무엇인지를 다시 잰 결과다.

## 이 측정이 답하는 질문 / 답하지 못하는 질문

답하는 것:
- 접속 큐 3단(Nginx listen 백로그·컨테이너 SYN 큐·Tomcat accept-count) 확장이 1차가 지목한
  리슨 백로그 오버플로를 실제로 없앴는가.
- 없앤 뒤에도 러시 초반 공백이 남는다면, 그 정체는 무엇인가.
- 공백을 지나 완판까지 이어지는 트리클링(201이 한꺼번에 안 쏟아지고 초당 몇 건씩만 나오는
  현상)의 정체는 무엇인가.
- 초과 판매는 여전히 0인가.

이번 측정은 1차·09-09와 달리 **거의 순수한 A/B다**. 두 측정 사이 main에 들어간 변경은
#426(`application.yml`·`docker-compose.prod.yml`·`nginx/groove.conf`·`scripts/server-init.sh`
설정 4개, 비즈니스 로직 없음) 하나뿐이다(`git log 8a4277d`). 이전 문서들이 매번 달고 다니던
"코드도 같이 바뀌어 순수 비교가 아니다" 경고가 이번엔 대부분 해당하지 않는다.

답하지 못하는 것:
- 배포 직후(21:17) 3분 만에 잰 1000 VU 단계가 얼마나 콜드 JVM 영향을 받았는지. 2000 VU는
  1000 VU 실행 뒤라 상대적으로 웜이지만, 완전한 워밍업 상태와의 차이는 분리하지 못했다.
- 남은 공백(메모리 압박·스왑)과 그 뒤 트리클링(DB 행 락 컨보이)을 고쳤을 때 얼마나 나아지는지.
  이번 측정은 두 원인을 찾아냈을 뿐 고치지 않았다 — 후속(#430·#431) 몫이다.
- 2000 VU 호스트 `TCPSynRetrans` +1,458의 정확한 발생 지점. 서버 쪽 리슨 백로그는 전부
  오버플로 0이라 서버발 재전송은 배제되지만, 클라이언트(k6 실행 macOS)·중간 경로 쪽 재전송
  가능성은 원인을 더 파지 않았다.

## 프로토콜

- 대상: EC2 운영 서버(`groove-lp.duckdns.org`). 배포본은 `main`(PR #429, #426 반영분 포함).
  배포 2026-09-16 21:17 → 1000 VU 단계는 배포 약 3분 뒤(콜드에 가까움), 2000 VU 단계는
  1000 VU 완주 뒤라 웜.
- 설정 변화(1차 대비): `server.tomcat.accept-count` 100(기본값, 미설정)→4096,
  `server.tomcat.max-connections` 8192(명시), 컨테이너 `sysctls: net.ipv4.tcp_max_syn_backlog=4096`
  (기본 128), 호스트 `/etc/sysctl.d/99-groove.conf`로 같은 값 + `net.core.somaxconn=4096`
  수동 1회 적용, `nginx/groove.conf`의 `listen 443 ssl backlog=2048`(기본 511).
- 배포 후 검증: 컨테이너 netns `ss -ltn` 리슨 소켓 Send-Q 4096(`post-check.txt`로 재확인,
  아래 참고), Nginx 443 Send-Q 2048, 호스트·컨테이너 `tcp_max_syn_backlog` 4096.
- 시나리오: `scripts/k6/limited-purchase.js`, VU 1000 → 2000 2단계, 재고 두 단계 모두 100.
  1차와 같은 규칙("성공 수 == 재고"로 판정 가능하려면 재고가 VU보다 항상 적어야 한다).
- 네트워크 기준선: VPN 해제(경로 `en0`) 후 `/api/v1/health` 3회 — conn 약 14~17ms, TLS 약
  32~37ms, TTFB 약 55~148ms.
- 실행:

```bash
MAX_MEMBERS=4000 scripts/k6/prod-run.sh --vus "1000 2000" --stock 100
```

- `cleanup-prod-loadtest.sh --apply`로 이번 측정이 만든 회원·상품·드롭(15, 16)·주문을 전부
  지웠다(사후 검증 0건).

## 결과

### 단계별 `purchase` 지연/처리량

| VU | 재고 | 201 | 409 SOLD_OUT | 실패 | purchase p50 | p95 | p99 | min | 전체 req | http_req_failed | k6 exit |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1000 | 100 | 100 | 900 | **0** | 14.14s | 15.49s | 15.80s | 8.06s | 5010 | 0% | 99(p95 임계 초과) |
| 2000 | 100 | 100 | 1900 | **0** | 23.68s | 27.45s | 28.15s | 0.52s | 9010 | 0% | 99(p95 임계 초과) |

`k6 exit 99`는 1차와 같은 이유로 threshold(p95 1초) 초과 표시이고 초과판매 판정과는 별개다.

### 1차(09-16, 백로그 수정 전) 대비

| 항목 | 1차 | 이번 |
|---|---|---|
| 2000 VU 실패 | 31건(1.55%) | **0건** |
| `ListenOverflows`(측정 전체 누적) | 6,563 | **0** |
| 컨테이너 `TCPReqQFullDoCookies` | 560 | **0** |
| 컨테이너 `SyncookiesFailed` | 3 | **0** |
| 2000 VU purchase min | 0s(미응답 기록) | 0.52s |
| 1000 VU purchase p95 | 15.23s | 15.49s |
| 2000 VU purchase p95 | 30.05s | 27.45s |
| TIME_WAIT max(1000 / 2000 VU) | 1,766 / 3,635 | 1,756 / 3,737 |

1차의 `ListenOverflows` 6,563은 두 단계(1000+2000 VU) 합산 누적치이고, 이번 값도 두 단계 델타의 합(0)이라 같은 기준으로 비교했다. p95가 1차와
비슷하거나 소폭 늘어난 건 퇴보가 아니다 — 재전송 계단이 사라진 대신 아래에서 보듯 다른
원인(메모리 압박, DB 락 컨보이)이 그대로 지연에 반영되기 때문이다.

### 단계별 자원 피크 (1초 간격 샘플, 전 구간 min/max)

| VU | backend RSS min/max(MB) | mysql RSS min/max(MB) | swap_used min/max(MB) | mem_available min/max(MB) | tcp_estab max | TIME_WAIT max |
|---|---|---|---|---|---|---|
| 1000 | 335 / 425 | 26 / 79 | 443 / 705 | 51 / 166 | 4,014 | 1,756 |
| 2000 | 294 / 396 | 30 / 84 | 714 / 912 | 56 / 151 | 7,987 | 3,737 |

### 백로그 수정의 효과(목표 달성)

`post-check.txt` 델타(러시 구간, 컨테이너는 21:17 배포로 갓 재기동해 카운터가 0부터 시작):

| 카운터 | 1000 VU | 2000 VU |
|---|---|---|
| backend `ListenOverflows` | +0 | +0 |
| backend `ListenDrops` | +0 | +0 |
| backend `TCPReqQFullDoCookies` | +0 | +0 |
| backend `SyncookiesFailed` | +0 | +0 |
| host `TCPReqQFullDoCookies` | +0 | +0 |
| host `TCPSynRetrans` | +5 | +1,458 |
| Nginx 5xx / 업스트림 오류 | +0 / +0 | +0 / +0 |
| `worker_connections` 부족 경고 | +0 | +0 |
| RestartCount / OOMKilled / Redis evicted | 0 / false / 0 | 0 / false / 0 |

배포 직후 `ss -ltn`으로 확인한 컨테이너 리슨 소켓 Send-Q도 4096이었다. 리슨 백로그가 넘쳐
syncookie로 전환되는 지표(`TCPReqQFullDoCookies`, `ListenOverflows`)가 두 단계 모두 정확히
0으로 유지됐고, 1차에서 31건이던 2000 VU 실패도 0건, 응답을 아예 못 받아 0s로 기록되던
min도 0.52s로 바뀌었다. 목표한 병목(Tomcat 리슨 백로그)은 없앴다.

### 남은 공백의 정체: 메모리 압박(스왑)

목표는 달성했지만 러시 시작 직후 공백은 그대로 남았다 — 1000 VU는 7초(21:22:41 시작 →
21:22:48 첫 응답), 2000 VU는 약 20초(21:27:41 시작, 산발적 응답 뒤 21:27:53~21:28:02 사이
집중)다. `ListenOverflows`가 0인데도 공백이 남는다는 건 재전송 계단이 아닌 다른 원인이라는
뜻이다.

호스트 `/proc/stat` 초당 델타(2000 VU 단계, jiffies, 2 vCPU 상한 200):

| 시각 | user | sys | idle | iowait | steal |
|---|---|---|---|---|---|
| 21:27:41(러시 시작) | 15 | 8 | 138 | 35 | 1 |
| :42 | 115 | 43 | 2 | 25 | 1 |
| :44(3초 누적) | 108 | 90 | 0 | 373 | 4 |
| :47(3초 누적) | 18 | 57 | 0 | 453 | 1 |
| :48 | 21 | 25 | 0 | 210 | 2 |
| :50~:56 | 15~37 | 20~45 | 0~4 | 122~182 | 0~2 |
| :57~:59 | 7~36 | 14~25 | 14~67 | 93~133 | 1~2 |
| 21:28:00~:02 | 7~31 | 15~25 | 3~41 | 14~130 | 25~172 |
| :05~:09(409 홍수 구간) | 93~115 | 48~61 | 0~5 | 25~57 | 0~1 |

같은 구간 `resources.csv`: backend RSS 382→302MB(−80), mysql RSS 84→32MB(−52), swap
724→830MB(+106), `mem_available` 60~150MB 사이. backend cgroup CPU는 1~12%로 낮다. 1000 VU
단계도 같은 패턴: backend RSS 403→336MB, mysql 75→29MB, swap 493→654MB, `mem_available`
58~166MB, backend CPU 7~28%.

측정 직후 프로세스 스냅샷: backend VmRSS 261MB / VmSwap 344MB, mysql VmRSS 62MB / VmSwap
200MB, 호스트 swap 946MB/2047MB, `pswpin` 누적 1,986,130, `vm.swappiness` 10.

**해석**: 러시로 수천 개 TCP 연결이 한꺼번에 서면 커널이 소켓 버퍼를 확보하느라 JVM·MySQL이
쓰던 페이지를 스왑아웃한다. 앱이 첫 요청을 실제로 처리하려면 그 페이지를 다시 디스크(swap)에서
읽어와야 하고, 그동안 CPU는 놀면서(idle 0에 가깝고 user/sys도 낮음) iowait만 초당
100~450jiffies씩 쌓인다 — 공백이 곧 스왑인 대기 시간이다. 이게 공백이 연결 수(VU)에 비례해서
커지는 이유이자, 배포 직후 콜드 상태(1000 VU)에서 상대적으로 더 오래 걸리는 이유이기도 하다.
1차에서는 SYN 재전송 계단이 이 스왑 지연 위에 겹쳐 있어 원인이 하나로 안 보였다. 후속은 #430.

### 그 뒤 지연: DB 쓰기 경로 락 컨보이

공백을 지나도 201이 한꺼번에 쏟아지지 않고 트리클링한다. Nginx 응답 초별 201/409 건수:

**1000 VU** (러시 시작 21:22:41, 첫 응답 21:22:48)

| 초 | :48 | :49 | :50 | :51 | :52 | :53 | :54 | :55 | :56 |
|---|---|---|---|---|---|---|---|---|---|
| 201 | 8 | 17 | 22 | 5 | 16 | 23 | 5 | 3 | 1 |
| 409 | 0 | 0 | 0 | 70 | 38 | 48 | 232 | 266 | 246 |

**2000 VU** (러시 시작 21:27:41)

| 초 | :41 | :42 | :50 | :53 | :54 | :55 | :56 | :57 | 21:28:00 | :01 | :02 | :03 | :04 | :05 | :06 | :07 | :08 | :09 | :11 | :12 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 201 | 3 | 1 | 5 | 5 | 1 | 1 | 11 | 14 | 4 | 6 | 3 | 20 | 18 | 5 | 3 | 0 | 0 | 0 | 0 | 0 |
| 409 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 56 | 93 | 41 | 218 | 317 | 413 | 160 | 481 | 117 | 4 |

201은 첫 응답부터 재고가 0이 될 때까지 초당 한 자릿수~20여 건씩 새어 나오고(=락 대기 순서대로
당첨자가 하나씩 확정), 409는 재고가 실제로 0이 된 뒤에야(2000 VU는 :05~:09 구간) 한꺼번에
쏟아진다 — 탈락자 판정이 당첨자 판정 뒤에서 순서를 기다린다는 뜻이다.

같은 구간 InnoDB 카운터: `row_lock_waits` +100(=당첨자 수와 일치), `row_lock_time_avg`
982ms, `row_lock_time_max` 14,367ms, `Max_used_connections` 13(HikariCP 기본 10). 코드상
`LimitedPurchaseService.purchase()`는 `findById(dropId)`로 DB 를 먼저 읽고 나서야 Redis 선점을 부른다.
당첨자 100명은 그 뒤 `SELECT ... FOR UPDATE` 행 락을 기다리며 HikariCP 커넥션(10개)을 붙들고,
탈락자는 Redis 에 도달하기 전의 `findById` 에서 커넥션을 못 얻어 같은 줄에 선다 — Redis 1차
필터가 DB 커넥션 풀 뒤에 놓인 구조다. 후속은 #431.

cpu steal은 두 단계 모두 러시 구간 평균이 낮다(1000 VU 최대 1.5%, 2000 VU는 21:28:00~:02
409 홍수 직전에만 25~172jiffies로 튀었다 — 원인이 아니라 결과로 보인다). 09-16 1차의 "steal은
원인이 아니다" 결론이 이번에도 유지된다.

TIME_WAIT max는 1000 VU 1,756 / 2000 VU 3,737(1차 1,766 / 3,635)로 거의 그대로다 —
upstream keepalive 64 재검토는 미결로 남긴다(#430·#431 뒤에 다시 본다).

### 초과판매 판정 (`verify-oversell.sh`)

| VU | `limited_purchase` | `stock.quantity` | `sold_count` | Redis buyers | 판정 |
|---|---|---|---|---|---|
| 1000 | 100 | 0 | 100 | 100 | PASS |
| 2000 | 100 | 0 | 100 | 100 | PASS |

두 단계 모두 4항목 정확히 일치, Redis·DB 구매자 집합도 일치(pending 잔여 0건). 접속 큐를
전부 열어젖힌 상태에서도 이중 방어(Redis 선점 + DB 조건부 UPDATE)는 재고 상한만큼만 팔았다.

## 읽기

확정한 것:
- 접속 큐 3단 확장(Nginx listen 백로그 2048, 컨테이너 SYN 큐 4096, Tomcat accept-count
  4096)이 1차가 지목한 리슨 백로그 오버플로를 없앴다. `ListenOverflows`/`TCPReqQFullDoCookies`
  두 단계 모두 0, 2000 VU 실패 31건 → 0건. 부작용(RestartCount·OOMKilled·Redis evicted_keys)도
  없다.
- 목표를 달성해도 러시 초반 공백 자체는 없어지지 않았다. 정체가 SYN 재전송에서 메모리
  압박(스왑아웃 → iowait)으로 바뀌었을 뿐이다. 호스트 CPU 시간 분해(idle 0에 가깝고 iowait만
  급등)와 같은 구간 RSS 감소·swap 증가가 같은 방향을 가리킨다.
- 공백을 지난 뒤의 트리클링은 `LimitedPurchaseService.purchase()`가 Redis 선점보다 앞서
  `findById(dropId)`로 DB 행 락을 먼저 잡는 순서 때문이다. `row_lock_waits` 증가분이 정확히
  당첨자 수(100)와 일치하는 게 직접 증거다.
- 초과 판매는 1000·2000 VU 모두 0건. 접속 큐를 넓혀 훨씬 많은 연결이 앱까지 도달해도 이중
  방어는 정확했다.

추정인 것:
- 2000 VU 호스트 `TCPSynRetrans` +1,458의 정확한 출처. 서버 쪽 리슨 큐는 전부 오버플로 0이라
  서버발 재전송은 배제되지만, k6를 돌린 로컬 macOS 쪽이나 중간 경로에서 난 재전송인지는
  확증하지 못했다(`http_req_blocked` p99 8.6s, `tls_handshaking` max 9.03s와 시간대가
  겹친다 — 클라이언트 쪽 연결 확립 지연으로 보는 쪽에 무게를 둔다).
- 1000 VU 단계(배포 3분 뒤, 콜드 JVM)가 완전히 예열된 상태보다 얼마나 더 오래 걸렸는지는
  분리하지 못했다. 공백이 VU에 비례하는 패턴과 스왑 카운터가 직접 증거라 콜드 JVM이 주된
  설명은 아니라고 보지만, 기여분이 0이라고 단정하지는 않는다.

## 한계

- 단일 클라이언트(로컬 macOS 1대)에서 낸 부하다. 여러 지역·여러 클라이언트가 동시에 들어오는
  실제 트래픽과는 다르다.
- 1000 VU 단계는 배포 3분 뒤라 완전히 예열된 JVM은 아니다.
- VU를 2000까지만 확인했다. 메모리 예산이나 DB 락 순서를 고친 뒤 그 다음 병목이 어디서
  나타나는지는 이번 측정 범위 밖이다.
- InnoDB 락 카운터·프로세스 VmRSS/VmSwap 스냅샷은 측정 중 별도 SSH 세션에서 수기로 채집한
  값이라, 이 결과 디렉토리(`prod-20260916-211958/`)의 파일만으로는 재계산할 수 없다.

## 후속

- #430 — 러시 초반 스왑아웃을 줄인다. t3.micro 1GB에서 JVM 힙(384m)·MySQL 버퍼 풀(128M)·
  Redis(64mb)·커널 소켓 버퍼가 나눠 쓰는 메모리 예산을 다시 보거나, `net.core.rmem_max`
  같은 소켓 버퍼 상한을 조정하는 쪽을 검토한다.
- #431 — `LimitedPurchaseService.purchase()`가 DB 행 락을 Redis 선점보다 먼저 잡는 순서를
  바꿔 탈락자가 DB 커넥션 풀 뒤에 줄 서지 않게 한다.
- 위 둘이 끝난 뒤 upstream keepalive 64, TIME_WAIT 재검토.

## 재현

VPN이 켜져 있으면 반드시 끈다.

```bash
MAX_MEMBERS=4000 scripts/k6/prod-run.sh --smoke                          # VU 10 / 재고 3 으로 한 바퀴 확인
MAX_MEMBERS=4000 scripts/k6/prod-run.sh --vus "1000 2000" --stock 100    # 본측정
scripts/k6/cleanup-prod-loadtest.sh --drop-ids "<drop-ids.txt 내용>"          # dry-run
scripts/k6/cleanup-prod-loadtest.sh --drop-ids "<drop-ids.txt 내용>" --apply
```

체크리스트는 1차 문서와 같다 — VPN 해제 확인, `MAX_MEMBERS`를 회원 총량보다 크게, 배포
워크플로와 겹치지 않는 시간대.

이 문서의 수치는 `scripts/k6/results/prod-20260916-211958/`의 `summary.tsv`, `vu-1000/`·
`vu-2000/` 안의 `k6-stdout.log`·`verify.txt`·`pre-check.txt`·`post-check.txt`·
`resources.csv`, `host-cpu-2000.log`, `baseline-before/after.txt`로 역추적된다(결과 디렉토리는
gitignore 대상이라 로컬에만 있다). InnoDB 락 카운터와 프로세스 VmRSS/VmSwap 스냅샷은 측정 중
별도 SSH 세션에서 직접 채집한 값이라 이 디렉토리에 원본 로그가 남아 있지 않다.
