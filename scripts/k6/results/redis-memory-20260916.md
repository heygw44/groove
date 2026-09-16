# Redis 메모리 운영 실측 (2026-09-16)

#396 이 Redis `noeviction` + AOF 전환을 준비하면서 `--maxmemory 64mb` 를 "측정 전 임시값"으로
남겨뒀다. 이 값을 확정하려면 실제 운영 사용량이 필요했다 — `scripts/redis-memory-report.sh` 로
처음 재본 결과다.

## 이 측정이 답하는 질문 / 답하지 못하는 질문

답하는 것:
- 운영 Redis 의 현재 메모리 사용량(used/peak)이 `maxmemory 64mb` 대비 얼마나 여유가 있는가.
- prefix 별로 몇 개의 키가 있고, 키 하나가 몇 바이트인가 — `refresh:`/`limited:*` 처럼
  사라지면 안 되는 키와 `recommend:*`/`recent-view:` 같은 캐시 키가 실제로 어떤 비율로
  섞여 있는가.
- TTL 이 없는 키가 설계대로 `limited:*` 뿐인가, 아니면 다른 prefix 로 새고 있는가.
- `recent_view_push.lua` 의 OOM 우회(본문 참고)가 실제로 하루에 몇 바이트씩 쌓이는 문제인가.

답하지 못하는 것:
- 트래픽 피크(한정반 오픈 순간, 캠페인 등) 때의 사용량. 이번 측정은 평시 스냅샷 한 번이다.
- 회원 수가 지금의 몇 배로 늘었을 때의 실측치. 상한 어림은 "결과 → 읽기"에서 로컬 키
  바이트 수를 그대로 곱해 추정한 값이지, 실측한 값이 아니다.
- `noeviction` 전환(#396) 이후의 실제 거동. 이번 측정 시점엔 아직 배포 전이라 운영 정책은
  `allkeys-lru`, AOF 는 꺼진 상태였다(아래 프로토콜 참고).

## 프로토콜

- 대상: EC2 운영 서버(`groove-lp.duckdns.org`)의 `groove-redis` 컨테이너. 측정은 읽기 전용
  명령(`INFO`/`DBSIZE`/`CONFIG GET`/`--scan`/`MEMORY USAGE`/`TTL`/`EVAL`+`PTTL`)만 쓴다 —
  쓰기 명령은 하나도 실행하지 않았다.
- 시각: 2026-09-16 17:20 KST.
- 측정 당시 운영 Redis 정책: `redis_version 7.4.11`, `uptime_in_days 10`, `maxmemory-policy
  allkeys-lru`, `aof_enabled 0`. #396(`noeviction` + AOF)은 이 시점에 아직 배포 전이었다 —
  이 실측치는 **정책 전환과 무관하게 유효**하다. 측정한 건 키 개수·바이트·TTL 분포라 축출
  정책이 바뀐다고 값이 달라지지 않는다.
- 스크립트: `scripts/redis-memory-report.sh`(원자료는 `scripts/k6/results/
  redis-memory-20260916-172058/raw.txt`, gitignore 대상).

```bash
scripts/redis-memory-report.sh
```

- 로컬 비교치(`scripts/k6/results/redis-memory-20260916-170740/raw.txt`, `--local`)는 회원
  1000명 이상으로 세션이 많은 개발 DB 를 그대로 잰 것이다. **이 문서에서 로컬 수치는 오직
  "키 하나가 몇 바이트인가"의 근거로만 쓴다** — 절대 사용량(3.72M)이나 키 개수(5,261개)는
  운영과 회원 규모가 다르므로 비교 대상이 아니다.

## 결과

### 메모리(운영)

| 항목 | 값 |
|---|---|
| used_memory | 1,641,720 B (1.57M) |
| used_memory_peak | 1,989,344 B (1.90M), peak_perc 82.53% |
| used_memory_rss | 1,941,504 B (1.85M), fragmentation ratio 1.21 |
| used_memory_startup | 948,520 B (0.90M) — 빈 인스턴스 고정 오버헤드 |
| used_memory_dataset | 688,488 B (0.66M) — 고정 오버헤드를 뺀 순수 데이터 |
| maxmemory | 67,108,864 B (64.00M) |
| maxmemory_policy | `allkeys-lru`(측정 시점, 배포 후 `noeviction`) |
| DBSIZE | 7 |
| evicted_keys / expired_keys | 0 / 0 |
| keyspace_hits / keyspace_misses | 5,839 / 718 |
| aof_enabled | 0(측정 시점, 배포 후 1) |

used_memory 의 82%(1.36M)가 `used_memory_startup`(0.90M)과 나머지 고정 오버헤드다. 실제 키가
차지하는 건 `used_memory_dataset` 0.66M 뿐이다.

### prefix 별 키 개수(운영, 전체 7개)

| prefix | 개수 | TTL 없는 키 |
|---|---|---|
| `refresh:` | 4 | 0 |
| `refresh-sessions:` | 0 | 0 |
| `limited:stock:` | 1 | 1 |
| `limited:buyers:` | 0 | 0 |
| `limited:attempts:` | 0 | 0 |
| `limited:pending:` | 0 | 0 |
| `limited:rebuild:` | 0 | 0 |
| `recommend:bought-together:` | 0 | 0 |
| `recent-view:` | 2 | 0 |
| `idem:order:` | 0 | 0 |
| 미매칭 | 0 | 0 |

TTL 이 없는 키는 `limited:stock:11` 하나뿐이다 — OPEN 상태 드롭 11의 재고 카운터로,
`clear()`로만 지워지는 설계대로다(§5.9). 새는 prefix 는 없다.

`bigkeys` 요약: 가장 큰 키는 `recent-view:3`(list, 20 items). `refresh:1017`이 가장 큰
string(211 bytes).

### 키당 바이트(운영 샘플 + 로컬 근거)

| prefix | 운영 샘플 | 바이트 | TTL(초) | 비고 |
|---|---|---|---|---|
| `refresh:` | `refresh:1017` 등 4개 | 280 | 270,466~949,039 | **구 형식**(string). 새 형식은 `refresh:{memberId}:{sid}`(hash) |
| `limited:stock:` | `limited:stock:11` | 64 | -1 | OPEN 드롭, 정상 |
| `recent-view:` | `recent-view:3` | 120 | 2,331,509 | 20개 채운 리스트 |
| `recent-view:` | `recent-view:1017` | 80 | 2,086,637 | 항목이 적은 리스트 |
| `refresh:`(로컬 근거) | `refresh:{memberId}:{sid}` | 544 | — | 새 형식 hash, 운영엔 아직 없음(TTL 14일이 아직 안 돈 구 세션만 있거나, 코드 배포 이후 로그인이 적었다는 뜻) |
| `refresh-sessions:`(로컬 근거) | `refresh-sessions:{memberId}` | 160~224 | — | 로컬 5개 샘플 기준 |

운영에 남아있는 `refresh:` 키는 전부 구 형식(문자열 280B)이고, 새 형식(hash) 세션이나
`refresh-sessions:` 인덱스 키가 하나도 없다. 세션당 예산은 로컬에서 관찰한 새 형식 기준으로
잡는다: hash 544B + set 160~224B ≈ **세션 1개당 약 700B**.

### 호스트 자원(운영)

| 항목 | 값 |
|---|---|
| free -m | total 911 / used 672 / free 71 / available 238 |
| swap | 2,047MB 중 659MB 사용 중 |
| docker stats | backend 224.7MiB, mysql 43.28MiB, redis 4.406MiB |
| vm.overcommit_memory | 0 |
| redis 컨테이너 메모리 상한(`HostConfig.Memory`) | 0(cgroup 제한 없음, `--maxmemory` 로만 막음) |

## 읽기

**결론: `--maxmemory 64mb` 유지로 확정한다.**

- 산정 규칙("피크 × 2 이상"): 1.90M × 2 = 3.8M ≪ 64M. 30배 넘는 여유가 있다.
- 상한을 세션 수로 환산: (64M − 0.9M 고정 오버헤드) / 700B(세션당) ≈ **9만 세션**.
  `recent-view:` 만 기준으로 하면(키당 120B) ≈ **50만 회원 키**. 현재 운영 회원 규모(운영
  키 7개 수준)와 몇 자릿수 차이라, 회원이 지금보다 몇 배로 늘어도 한동안 문제없다.
- 올릴 이유가 없다: 호스트가 이미 `free 71MB`(available 238MB)·`swap 659MB 사용 중`이라, Redis 예산을
  늘리면 JVM(`-Xmx384m`)·MySQL(`buffer pool 128M`) 쪽을 압박한다.
- 내릴 이유도 없다: 64M 이 이미 사용량의 34배 수준이고, 운영 중 세션이 갑자기 몰리는 경우에
  대비한 여유로 남겨둘 값이다.

**`recent_view_push.lua` OOM 우회(§5.9)의 실제 크기.** OOM 상태에서 `LPUSH` 가 `LREM` 뒤에
실행돼 `maxmemory` 검사를 우회하지만(deny-oom 은 스크립트 안에서 첫 쓰기에만 적용), 실측으로
보면 이게 걱정할 크기가 아니다 — `recent-view:` 키가 10일 가동에 단 2개다. 일 신규 키가 1개에
한참 못 미치고, 키당 최대 120B(20개 채운 리스트)이므로 OOM 구간의 일 증가량은 1KB 미만이다.
경보 인프라를 따로 두지 않고 스모크 때 `used_memory`/`maxmemory` 비율을 눈으로 보는 것으로
갈음한다는 기존 결정(#404 경보 폐기)을 유지할 근거가 된다.

**측정 시점 정책이 아직 `allkeys-lru`였던 점.** #396 이 아직 배포되지 않아 이번 측정은
`noeviction` 전환 전 상태에서 이뤄졌다. 다만 이 문서가 답하려는 질문(피크 사용량, prefix 별
키 분포, 키당 바이트)은 축출 정책과 무관한 값이라 그대로 쓸 수 있다. `evicted_keys 0`은
`allkeys-lru`에서도 축출이 일어날 만큼 차지 않았다는 뜻이지, `noeviction`으로 바뀌었을 때의
거동을 보장하지 않는다 — OOM 시 실제 거동은 §5.9의 로컬 강제 재현 검증으로 이미 따로
확인했다.

## 재현

```bash
scripts/redis-memory-report.sh          # 운영(SSH)
scripts/redis-memory-report.sh --local  # 로컬 컨테이너(개발 검증용)
```

이 문서의 모든 수치는 `scripts/k6/results/redis-memory-20260916-172058/raw.txt`(운영)와
`scripts/k6/results/redis-memory-20260916-170740/raw.txt`(로컬 비교)로 역추적된다(둘 다
gitignore 대상이라 로컬에만 있다).
