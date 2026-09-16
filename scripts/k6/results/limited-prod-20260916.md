# 한정반 구매 운영 부하 재측정 — Nginx 슬롯 확장 후 (2026-09-16)

`limited-prod-20260909.md`에서 1000 VU가 Nginx `worker_connections 768` 한계에 막혀 21.7%
실패한 걸 확인했다. #405에서 `worker_connections`를 4096으로 올리고 upstream keepalive를
붙인 뒤, 그 조치가 실제로 효과가 있었는지, 그리고 슬롯을 풀었을 때 다음 병목이 어디서
나타나는지를 다시 잰 결과다.

## 이 측정이 답하는 질문 / 답하지 못하는 질문

답하는 것:
- Nginx 슬롯 확장(768→4096, upstream keepalive)이 09-09에 관측한 실패를 실제로 없앴는가.
- 슬롯을 풀었을 때, 그 다음으로 무너지는 지점은 어디인가(VU를 09-09보다 한 단계 더 올려
  2000까지 확인했다).
- 그 지점에서도 초과 판매가 나지 않는가.

답하지 못하는 것:
- 순수한 A/B 비교. 09-09 이후 코드도 #392~#415(결제 대사·취소 3단계·그레이스풀 셧다운·
  Redis 정책·한정반 대사/재적재·서킷 폴백·refresh 세션·주문 멱등키)만큼 바뀌었다. 이번
  측정은 "09-09 대비 지금이 어떤가"를 보여줄 뿐, Nginx 설정 하나만 바꾼 순수 실험이 아니다.
- Tomcat accept 백로그를 직접 늘렸을 때 얼마나 나아지는지. 이번 측정은 문제를 찾아냈을
  뿐 고치지 않았다 — 후속(#426) 몫이다.
- 배포 직후(콜드 JVM)와 충분히 예열된 JVM의 차이. 30분 뒤에 쟀지만 그게 완전한 워밍업인지는
  확인하지 않았다.

## 프로토콜

- 대상: EC2 운영 서버(`groove-lp.duckdns.org`). 배포본은 `main`(#425, Nginx 슬롯 확장 포함).
  배포 직후 30분 뒤에 측정을 시작했다 — 백엔드 컨테이너 `StartedAt`과 측정 시작 시각 차이가
  약 30분이라, JVM 입장에서는 콜드에 가깝다.
- 설정 변화(09-09 대비): Nginx `worker_connections` 768→4096, `worker_rlimit_nofile 8192`,
  `multi_accept on`, `upstream groove_backend { keepalive 64; }` + `proxy_set_header
  Connection ""`(#405).
- 코드 변화(09-09 대비): #392~#415 다수 병합. 순수 A/B가 아니라는 뜻이라 위 "답하지 못하는
  것"에도 적었다.
- 시나리오: `scripts/k6/limited-purchase.js`, VU 1000 → 2000 2단계. 재고는 두 단계 모두 100
  (VU보다 항상 적어야 "성공 수 == 재고" 판정이 가능하다 — 09-09 문서와 같은 규칙).
- 네트워크 기준선: VPN 해제 확인(경로 `en0`) 후 `/api/v1/health` 3회 — conn 약 14ms, TLS 약
  30ms, TTFB 약 48ms. VPN 경유 시 이 경로는 애초에 측정이 안 된다(09-09 문서 "폐기한 1차
  측정" 참고).
- 판정 유효 시간 10분(`OrderExpirationScheduler`가 60초마다 PENDING 만료 처리) 안에
  `verify-oversell.sh`를 k6 종료 직후 실행했다 — 1000 VU는 최초 구매로부터 경과 20초, 2000
  VU는 65초 시점.
- 실행:

```bash
MAX_MEMBERS=4000 scripts/k6/prod-run.sh --vus "1000 2000" --stock 100
```

- 측정이 끝난 뒤 `cleanup-prod-loadtest.sh --apply`로 이번 측정이 만든 회원·상품·앨범·
  드롭·주문을 전부 지웠다(사후 검증 0건). 운영에 이미 떠 있던 드롭(11번)과 그 Redis 키는
  건드리지 않았다.

## 결과

### 단계별 `purchase` 지연/처리량

| VU | 재고 | 구매 시도 | 201 | 409 SOLD_OUT | 실패 | purchase p50 | p95 | p99 | min | 전체 req | http_req_failed(전체 기준) | k6 exit |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1000 | 100 | 1000 | 100 | 900 | 0 | 13.80s | 15.23s | 15.45s | 7.65s | 5010 | 0% | 99(p95 임계 초과) |
| 2000 | 100 | 2000 | 100 | 1869 | 31 (1.55%) | 25.52s | 30.05s | 31.82s | 0s | 9010 | 0.34% | 99(checks 98.45%) |

`k6 exit 99`는 실패가 아니라 임계값(threshold) 초과 표시다 — p95 1초 기준을 훨씬 넘겼다는
뜻이고, 초과판매 판정과는 별개다. 2000 VU의 `http_req_failed 0.34%`는 로그인·설정 요청까지
포함한 전체 9010건 기준이고, "실패 31건(1.55%)"은 구매 시도 2000건만의 실패율이다 — 분모가
다르니 섞어 읽지 않는다. 2000 VU의 min이 0s인 이유는 09-09와 같다: 응답을 못 받은 요청이
0ms로 기록됐다.

### 09-09 대비(1000 VU)

| 항목 | 09-09 | 09-16 |
|---|---|---|
| 실패 | 217 | **0** |
| `worker_connections` 부족 경고 | 199 | **0** |
| TIME_WAIT 최대 | 2909 | 1766 |
| purchase p95 | 4.48s | 15.23s |

p95가 늘어난 게 퇴보로 보이지만 그렇게 읽으면 안 된다. 09-09의 4.48s는 217건이 탈락하고
살아남은 요청만 집계한 생존 편향값이다. 09-16은 1000건 전원이 완주하면서 대기 시간이 전부
집계됐다 — 다른 모집단을 재고 있어 직접 비교가 안 된다는 09-09 문서의 경고가 이번에도
그대로 적용된다.

### 단계별 자원 피크 (1초 간격 샘플)

| VU | 샘플수 | backend CPU max(%) | backend RSS max(MB) | mysql CPU max(%) | available min(MB) | swap max(MB) | cpu steal max(%) | tcp estab max | TIME_WAIT max |
|---|---|---|---|---|---|---|---|---|---|
| 1000 | 172 | 212 | 430 | 30 | 46 | 695 | 65(설정 구간, 러시 평균 0.9%) | 4013 | 1766 |
| 2000 | 272 | 214 | 415 | 40 | 42 | 886 | 84(설정 구간, 러시 끝 18:34:00~02에 14~40%) | 7819 | 3635 |

steal 최댓값은 두 단계 모두 회원 생성 단계(BCrypt 해싱 구간)에서 나왔고, 실제 구매 러시
구간 평균은 1000 VU 0.9%, 2000 VU 2.9%로 낮다. 09-09 문서가 "steal이 실패 원인일 수 있다"고
추정으로 남겨둔 부분은 이번 측정으로 기각 쪽에 무게가 실린다 — 아래 "읽기" 참고.

### 2000 VU 실패 31건 내역

| 구분 | 건수 | 시각 | 원인 |
|---|---|---|---|
| Nginx 502 | 3 | 18:33:53 | `upstream prematurely closed connection` — 백엔드가 연결을 끊음(백로그·SYN 재전송 중 RST로 추정) |
| Nginx 504 | 1 | 18:34:02 | `upstream timed out` — `proxy_read_timeout 30s` 초과 |
| access.log 미기록 | 27 | 18:33:32(12건), 18:34:31(15건) | 클라이언트가 Nginx 앞에서 끊김: k6 `read: connection reset` 12건(러시 시작 1초 후), 클라이언트 요청 타임아웃 15건(러시 시작 60초 후) |

09-09의 "로그 없는 139건"과 같은 성격(Nginx가 access.log를 쓰기 전에 클라이언트 쪽이 먼저
끊긴 경우)이지만 규모는 139 → 27로 줄었다.

### Nginx/컨테이너 증거 (post-check 델타)

| 항목 | 1000 VU | 2000 VU |
|---|---|---|
| Nginx 5xx | +0 | +4 |
| `worker_connections` 부족 경고 | +0 | +0 |
| 업스트림 오류(`nginx_upstream_err`) | +0 | +3 |
| Redis `evicted_keys` | 0 | 0 |
| 컨테이너 RestartCount | 0 | 0 |
| OOMKilled | false | false |
| 백엔드 로그 ERROR | 0건(WARN `LIMITED_SOLD_OUT`만) | 0건(WARN `LIMITED_SOLD_OUT`만) |

`worker_connections 부족 경고`가 두 단계 모두 0이라는 게 핵심이다. 09-09에서 199건 찍히던
alert가 완전히 사라졌다 — Nginx 슬롯 확장이 09-09가 지목한 원인을 실제로 없앴다는 직접
증거다.

### 다음 병목: Tomcat accept 백로그

Nginx 슬롯을 풀고 나니 러시 시작 직후 백엔드·MySQL·Redis CPU가 거의 놀면서 요청이 앱에
닿지 않는 공백이 드러났다. 이 공백은 VU에 비례해서 커진다.

**초당 응답 분포**

| 단계 | 러시 시작 | 연결 즉시 established | 앱 첫 응답(공백) | 완판(`sold_out_at`) |
|---|---|---|---|---|
| 1000 VU | 18:28:31 | 4001개 | 18:28:38 (7초 공백) | 18:28:46 |
| 2000 VU | 18:33:31 | — | 18:33:53 (약 20초 공백, 그 사이 산발적으로 6·15·7·1·1·3건) | 18:33:57 |

| 1000 VU 초(purchase 응답 완료) | :38 | :39 | :40 | :41 | :42 | :43 | :44 | :45 | :46 | :47 |
|---|---|---|---|---|---|---|---|---|---|---|
| 건수 | 4 | 9 | 10 | 18 | 22 | 140 | 194 | 241 | 191 | 171 |

| 1000 VU 초(앱 `LIMITED_SOLD_OUT` 처리) | :43 | :44 | :45 | :46 | :47 |
|---|---|---|---|---|---|
| 건수 | 125 | 183 | 233 | 315 | 44 |

| 2000 VU 초(purchase 응답 완료) | :54 | :55 | :56 | :57 | :58 | :59 | 18:34:00 | :01 |
|---|---|---|---|---|---|---|---|---|
| 건수 | 41 | 119 | 186 | 459 | 229 | 213 | 254 | 115 |

응답이 한꺼번에 쏟아지지 않고 계단형으로 늘다가 폭증하는 모양이, 아래 커널 카운터가
가리키는 SYN 재전송 타이머(1·2·4·8초 간격)와 맞아떨어진다. 관측된 최소 지연 7.65초도
1+2+4초 재전송 합과 근접하다.

**커널 카운터 (측정 뒤 채집, 누적값)**

| 위치 | 카운터 | 값 | 의미 |
|---|---|---|---|
| 백엔드 컨테이너 netns | `ListenOverflows` = `ListenDrops` | 6563 | 리슨 백로그가 6563번 넘침 |
| 백엔드 컨테이너 netns | `TCPReqQFullDoCookies` | 560 | 백로그가 꽉 차 syncookie로 전환한 횟수 |
| 백엔드 컨테이너 netns | `SyncookiesSent` / `SyncookiesFailed` | 503 / 3 | 발급한 syncookie 대부분은 성공 |
| 백엔드 컨테이너 netns | `TCPSynRetrans` | 260 | SYN 재전송 |
| 백엔드 컨테이너 | 리슨 소켓 `ss -ltn` Send-Q | 100 | `server.tomcat.accept-count` 기본값과 일치 |
| 백엔드 컨테이너 | sysctl | `somaxconn 4096`, `tcp_max_syn_backlog 128`, `syncookies 1` | 커널 쪽 여유는 충분, Tomcat 쪽이 병목 |
| 호스트 netns | `TcpExtTCPSynRetrans` | 21,301 | |
| 호스트 netns | `TcpRetransSegs` | 30,500 | |
| 호스트 netns | `TcpExtTCPReqQFullDoCookies` | 2,547 | 호스트도 `tcp_max_syn_backlog 128`이라 공인 쪽 SYN도 syncookie로 처리 |
| 호스트 netns | `ListenDrops` / `ListenOverflows` | 3 / 0 | 호스트 리슨 자체는 거의 무사 |

Nginx→백엔드 경로는 `docker-proxy`(127.0.0.1:8080 → 컨테이너 IP 172.18.0.4:8080) + DNAT를
거친다. 백엔드 컨테이너의 리슨 백로그(Tomcat `accept-count` 기본값 100)가 병목이라는 게
`ss -ltn`의 Send-Q 100과 `ListenOverflows` 누적치로 직접 확인된다.

**해석**: Nginx가 1000~2000개 업스트림 연결을 동시에 열면 Tomcat 리슨 백로그(100)를 넘겨
SYN/ACK 완료가 지연되고, 재전송 타이머(1·2·4·8초)를 타고서야 늦게 들어온다. 09-09에는
`worker_connections 768`이 동시 업스트림 연결을 약 384개로 눌러놨기 때문에 이 오버플로가
가려져 있었다 — Nginx 슬롯을 풀자 진짜 병목(Tomcat `accept-count` 100)이 드러난 것이다.
JVM 워밍업 편향이 섞여 있을 가능성도 있지만, 공백이 VU에 비례해서 커지고 커널 카운터가
직접 증거이므로 백로그가 주원인이라고 본다. Tomcat 워커는 200 스레드 전부 소진됐고
(`exec-202`까지 로그에 등장), HikariCP 대기 로그는 없었다 — DB 커넥션 풀이 아니라 그
앞단(요청이 스레드에 배정되기 전)에서 막힌다는 뜻이다.

### 초과판매 판정 (`verify-oversell.sh`)

| VU | `limited_purchase` | `stock.quantity` | `sold_count` | Redis buyers | 판정 |
|---|---|---|---|---|---|
| 1000 | 100 | 0 | 100 | 100 | PASS |
| 2000 | 100 | 0 | 100 | 100 | PASS |

두 단계 모두 4항목이 정확히 일치했고, Redis 구매자 집합과 DB 구매자 집합도 일치했다(pending
잔여 0건). 2000 VU에서 31건(1.55%)이 실패하는 와중에도 초과 판매는 0건이다.

## 읽기

확정한 것:
- Nginx 슬롯 확장(768→4096 + upstream keepalive)이 09-09가 지목한 원인을 없앴다. 1000
  VU 실패 217건 → 0건, `worker_connections 부족` alert 199건 → 0건. 다른 지표(RestartCount,
  OOMKilled, Redis evicted_keys)도 전부 무사해 이 슬롯 확장이 부작용 없이 목표한 문제를
  고쳤다고 볼 수 있다.
- 초과 판매는 1000·2000 VU 모두 0건. Nginx 슬롯을 4096까지 올려 더 많은 동시 연결이
  앱까지 도달하게 만든 상태에서도 이중 방어(Redis 선점 + DB 조건부 UPDATE)는 정확히
  재고 상한만큼만 팔았다.
- 다음 병목은 Tomcat 리슨 백로그(`accept-count` 기본값 100)다. `ListenOverflows`/`Send-Q`
  같은 커널 카운터가 직접 가리키고, 응답이 늦게 들어오는 시점이 SYN 재전송 타이머 간격과
  맞아떨어진다.
- cpu steal은 이번 측정에서 실패의 원인으로 보기 어렵다. 러시 구간 평균이 1000 VU 0.9%,
  2000 VU 2.9%로 낮았고, steal이 실제로 높았던 구간(회원 생성 단계)에는 오히려 실패가
  없었다. 09-09가 추정으로 남겨뒀던 "steal이 실패에 기여했을 수 있다"는 가설은 이번
  측정으로 기각 쪽에 무게가 실린다.

추정인 것:
- 30분 워밍업이 JVM 성능에 얼마나 기여/방해했는지는 분리하지 못했다. 백로그 오버플로가
  VU에 비례해 커지는 패턴과 커널 카운터라는 직접 증거가 있어 워밍업보다 백로그가 주된
  설명이라고 보지만, 콜드 JVM이 완전히 무관하다고 단정하지는 않는다.
- access.log에 기록이 없는 27건이 정확히 어느 지점(Nginx 자체 타임아웃인지, 클라이언트
  단의 순수 타임아웃인지)에서 끊겼는지는 k6 쪽 에러 메시지(`read: connection reset`,
  요청 타임아웃)로 정황만 파악했고, Nginx 쪽에서 직접 확증하지는 못했다.
- 2000 VU에서 Redis `attempts` 해시의 `SOLD_OUT` 카운트(1870)와 access.log 기준 409
  건수(1869)가 1건 어긋난다. 어느 쪽이 정확한 집계인지, 타이밍 문제인지는 원인을 더
  파지 않았다 — 초과판매 판정 자체에는 영향이 없다(둘 다 재고 상한 100 안에서 일어난
  거절 사유 집계일 뿐이다).

## 한계

- 순수 A/B 비교가 아니다. Nginx 설정 외에도 #392~#415 코드 변경이 09-09와 09-16 사이에
  전부 섞여 있다.
- 단일 클라이언트(로컬 macOS 1대)에서 낸 부하다. 여러 지역·여러 클라이언트가 동시에
  들어오는 실제 트래픽 형태와는 다르다.
- 배포 후 30분 시점의 JVM은 완전히 예열됐다고 보기 어렵다. 콜드에 가까운 상태에서 잰
  값이라는 점을 감안해야 한다.
- VU를 2000까지만 확인했다. Tomcat `accept-count`를 올렸을 때 그 다음 병목이 어디서
  나타나는지는 이번 측정 범위 밖이다.

## 후속

- #426 — `server.tomcat.accept-count`와 `tcp_max_syn_backlog`(컨테이너 128, 호스트 128)를
  재검토하고, 값을 올린 뒤 같은 시나리오로 재측정한다. upstream keepalive 값(현재 64)도
  이 병목이 풀린 뒤 다시 볼 만하다.

## 재현

VPN이 켜져 있으면 반드시 끈다.

```bash
MAX_MEMBERS=4000 scripts/k6/prod-run.sh --smoke                          # VU 10 / 재고 3 으로 한 바퀴 확인
MAX_MEMBERS=4000 scripts/k6/prod-run.sh --vus "1000 2000" --stock 100    # 본측정
scripts/k6/cleanup-prod-loadtest.sh --drop-ids "<drop-ids.txt 내용>"          # dry-run
scripts/k6/cleanup-prod-loadtest.sh --drop-ids "<drop-ids.txt 내용>" --apply
```

체크리스트:
- VPN 해제 후 `curl -w` 3회로 로컬 → 운영 기준선을 확인한다(경로가 `utun*`이면 중단).
- `MAX_MEMBERS`를 회원 총량(두 단계 합)보다 크게 잡는다. 1000+2000 VU 세션은 회원
  3000명이 생기므로 기본값 4000을 그대로 둔다(`cleanup-prod-loadtest.sh`).
- 배포 워크플로와 겹치지 않는 시간대에 돈다.

이 문서의 모든 수치는 `scripts/k6/results/prod-20260916-182552/`의 `summary.tsv`, `run.log`,
`vu-1000/`·`vu-2000/` 안의 `verify.txt`·`pre-check.txt`·`post-check.txt`·`resources.csv`로
역추적된다(결과 디렉토리는 gitignore 대상이라 로컬에만 있다). Nginx per-second 응답 분포와
커널 카운터는 측정 중 별도 SSH 세션에서 직접 채집한 값으로, 이 결과 디렉토리에는 원본 로그가
남아 있지 않다.
