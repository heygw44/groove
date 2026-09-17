#!/usr/bin/env bash
# 운영 Redis 를 읽기 전용으로 측정해 메모리 사용량·prefix 별 키 분포 보고서를 만든다.
# maxmemory 산정의 근거 데이터를 얻는 용도다.
#
# 원격 명령은 ssh 한 번에 bash 스크립트 전체를 넘겨 실행한다(명령마다 SSH 를 새로
# 여는 방식은 EC2 t3.micro 에서 핸드셰이크 비용이 누적된다). 스크립트 안에서는
# `docker exec -i` 를 쓰지 않는다 — SSH 세션 stdin 은 이 스크립트 본문을 전달하는
# 채널이라, -i 로 컨테이너까지 그 stdin 을 열어두면 아직 안 읽은 나머지 스크립트를
# 컨테이너 쪽 프로세스가 먹어버려 이후 명령이 통째로 사라진다
# (infra/k6/verify-oversell.sh 의 run_sql 주석 참고). 여러 키의 TTL 을 한 번에
# 읽어야 하는 구간(e)은 stdin 대신 Redis EVAL 인자로 키 목록을 넘겨 같은 문제를
# 피한다. 키 열거는 항상 `KEYS` 대신 `--scan --pattern` 을 쓴다 — KEYS 는 운영
# 키스페이스 크기에서 스캔 도중 이벤트 루프를 막을 수 있다.
# shellcheck disable=SC2086 # SSH_OPTS 는 여러 -o 플래그를 담는 문자열이라 의도적으로 언쿼팅
set -euo pipefail

SSH_KEY="${SSH_KEY:-}"
SSH_HOST="${SSH_HOST:-}"
SSH_OPTS="${SSH_OPTS:--o ConnectTimeout=8 -o BatchMode=yes}"
REDIS_CONTAINER="${REDIS_CONTAINER:-groove-redis}"

usage() {
	cat <<EOF
사용법: $(basename "$0") [--local]

  --local  SSH 대신 로컬 docker 컨테이너로 같은 측정을 한다(개발 중 검증용).
           REDIS_CONTAINER(기본 groove-redis)가 없으면 docker ps 에서 이름에
           redis 가 들어간 컨테이너를 찾아 대신 쓴다.

환경변수: SSH_KEY(필수, pem 경로), SSH_HOST(필수, 예: ubuntu@<EC2-IP>), SSH_OPTS - --local 없을 때 사용
          REDIS_CONTAINER(기본 groove-redis)

출력: infra/k6/results/redis-memory-<YYYYMMDD-HHmmss>/raw.txt 에 원자료 저장,
      표준출력에 한글 요약(used/peak/maxmemory, prefix 별 키 수, TTL 없는 prefix, bigkeys 상위).
EOF
}

LOCAL_MODE=0
while [ $# -gt 0 ]; do
	case "$1" in
		--local)
			LOCAL_MODE=1
			shift
			;;
		-h | --help)
			usage
			exit 0
			;;
		*)
			echo "알 수 없는 옵션: $1" >&2
			usage
			exit 2
			;;
	esac
done

if [ "$LOCAL_MODE" = "1" ]; then
	if ! docker inspect "$REDIS_CONTAINER" >/dev/null 2>&1; then
		found=$(docker ps --format '{{.Names}}' | grep -i redis | head -1 || true)
		if [ -z "$found" ]; then
			echo "로컬 Redis 컨테이너를 찾을 수 없음(REDIS_CONTAINER=${REDIS_CONTAINER} 없음, docker ps 에도 redis 없음)." >&2
			echo "docker compose up -d redis 로 띄운 뒤 다시 실행해라." >&2
			exit 1
		fi
		echo "REDIS_CONTAINER=${REDIS_CONTAINER} 없음, docker ps 에서 찾은 ${found} 를 대신 쓴다." >&2
		REDIS_CONTAINER="$found"
	fi
else
	: "${SSH_HOST:?SSH_HOST(예: ubuntu@<EC2-IP>)를 지정하세요}"
	: "${SSH_KEY:?SSH_KEY(pem 경로)를 지정하세요}"
fi

# 원격(또는 로컬) 호스트에서 실행할 측정 스크립트 본문. $1 = REDIS_CONTAINER.
# set -e 를 안 쓰는 이유: grep -c/-E 무매치가 exit 1 을 내는 지점이 여러 곳이라
# set -e 면 정상적인 "0건" 상황에서도 스크립트가 죽는다. 대신 곳곳에 `|| true`.
MEASURE_SCRIPT=$(cat <<'REMOTE_SCRIPT_EOF'
set -uo pipefail
REDIS_CONTAINER="$1"

WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT

PREFIXES=(
	"refresh:"
	"refresh-sessions:"
	"limited:stock:"
	"limited:buyers:"
	"limited:attempts:"
	"limited:pending:"
	"limited:rebuild:"
	"recommend:bought-together:"
	"recent-view:"
	"idem:order:"
)
UNMATCHED_PATTERN=""
for p in "${PREFIXES[@]}"; do
	UNMATCHED_PATTERN="${UNMATCHED_PATTERN}^${p}|"
done
UNMATCHED_PATTERN="${UNMATCHED_PATTERN%|}"

rc() {
	docker exec "$REDIS_CONTAINER" redis-cli "$@" 2>&1
}

echo "=== 기본 정보 ==="
echo "[INFO memory]"
rc INFO memory | tr -d '\r'
echo
echo "[INFO stats 발췌]"
rc INFO stats | tr -d '\r' | grep -E '^(evicted_keys|expired_keys|keyspace_hits|keyspace_misses|rejected_connections|total_commands_processed):' || true
echo
echo "[INFO persistence 발췌]"
rc INFO persistence | tr -d '\r' | grep -E '^(aof_enabled|aof_rewrite|loading):' || true
echo
echo "[INFO server 발췌]"
rc INFO server | tr -d '\r' | grep -E '^(redis_version|uptime_in_days):' || true
echo
echo "[DBSIZE]"
rc DBSIZE
echo
echo "[CONFIG GET maxmemory]"
rc CONFIG GET maxmemory
echo
echo "[CONFIG GET maxmemory-policy]"
rc CONFIG GET maxmemory-policy

echo
echo "=== bigkeys 요약 ==="
rc --bigkeys | sed -n '/summary/,$p'

echo
echo "=== prefix 별 키 개수 ==="
ALL_KEYS_FILE="${WORKDIR}/all-keys.txt"
docker exec "$REDIS_CONTAINER" redis-cli --scan --pattern '*' >"$ALL_KEYS_FILE" 2>/dev/null || true
TOTAL_KEY_COUNT=$(wc -l <"$ALL_KEYS_FILE" | tr -d ' ')
echo "전체 키 개수(SCAN): ${TOTAL_KEY_COUNT}"
for p in "${PREFIXES[@]}"; do
	cnt=$(grep -c "^${p}" "$ALL_KEYS_FILE" || true)
	cnt=${cnt:-0}
	printf '%-32s %s\n' "$p" "$cnt"
done
echo "--- 어떤 prefix 에도 안 걸리는 키 ---"
UNMATCHED_FILE="${WORKDIR}/unmatched.txt"
grep -vE "$UNMATCHED_PATTERN" "$ALL_KEYS_FILE" | grep -v '^$' >"$UNMATCHED_FILE" || true
UNMATCHED_COUNT=$(wc -l <"$UNMATCHED_FILE" | tr -d ' ')
echo "미매칭 키 개수: ${UNMATCHED_COUNT}"
if [ "$UNMATCHED_COUNT" -gt 0 ]; then
	echo "샘플(최대 10개):"
	head -10 "$UNMATCHED_FILE"
fi

echo
echo "=== prefix 별 샘플 MEMORY USAGE / TTL ==="
echo "prefix key bytes ttl_seconds"
for p in "${PREFIXES[@]}"; do
	grep "^${p}" "$ALL_KEYS_FILE" | head -5 | while IFS= read -r k; do
		[ -z "$k" ] && continue
		bytes=$(docker exec "$REDIS_CONTAINER" redis-cli MEMORY USAGE "$k" 2>/dev/null)
		ttl=$(docker exec "$REDIS_CONTAINER" redis-cli TTL "$k" 2>/dev/null)
		echo "${p} ${k} ${bytes:-N/A} ${ttl:-N/A}"
	done
done

echo
echo "=== TTL 없는 키의 prefix 분포 ==="
TTL_SOURCE_FILE="$ALL_KEYS_FILE"
if [ "$TOTAL_KEY_COUNT" -gt 100000 ]; then
	echo "(키 ${TOTAL_KEY_COUNT}개 — 10만 개 초과라 처음 1000개만 샘플링한다)"
	TTL_SOURCE_FILE="${WORKDIR}/ttl-sample.txt"
	head -1000 "$ALL_KEYS_FILE" >"$TTL_SOURCE_FILE"
fi

# 키마다 TTL 을 따로 물으면 왕복이 N번이라 느리다. 1000개씩 묶어 EVAL 인자로
# 넘기고 스크립트 안에서 PTTL 을 돌려 한 번의 왕복으로 배치 결과를 받는다.
NO_TTL_FILE="${WORKDIR}/no-ttl.txt"
: >"$NO_TTL_FILE"
PTTL_SCRIPT='local out = {}
for i, k in ipairs(KEYS) do
  out[i] = redis.call("PTTL", k)
end
return out'

BATCH=()
flush_batch() {
	if [ "${#BATCH[@]}" -eq 0 ]; then
		return
	fi
	local results
	results=$(docker exec "$REDIS_CONTAINER" redis-cli EVAL "$PTTL_SCRIPT" "${#BATCH[@]}" "${BATCH[@]}" 2>/dev/null)
	local i=0
	while IFS= read -r pttl; do
		i=$((i + 1))
		if [ "$pttl" = "-1" ]; then
			echo "${BATCH[$((i - 1))]}" >>"$NO_TTL_FILE"
		fi
	done <<<"$results"
	BATCH=()
}

while IFS= read -r k; do
	[ -z "$k" ] && continue
	BATCH+=("$k")
	if [ "${#BATCH[@]}" -ge 1000 ]; then
		flush_batch
	fi
done <"$TTL_SOURCE_FILE"
flush_batch

for p in "${PREFIXES[@]}"; do
	cnt=$(grep -c "^${p}" "$NO_TTL_FILE" || true)
	cnt=${cnt:-0}
	printf '%-32s %s\n' "$p" "$cnt"
done
other_cnt=$(grep -vE "$UNMATCHED_PATTERN" "$NO_TTL_FILE" 2>/dev/null | grep -vc '^$' || true)
other_cnt=${other_cnt:-0}
echo "미매칭 prefix: ${other_cnt}"

echo
echo "=== 호스트 자원 ==="
echo "[free -m]"
free -m || true
echo
echo "[docker stats --no-stream]"
docker stats --no-stream --format '{{.Name}} {{.MemUsage}} {{.CPUPerc}}' || true
echo
echo "[vm.overcommit_memory]"
cat /proc/sys/vm/overcommit_memory || true
echo
echo "[docker inspect ${REDIS_CONTAINER} HostConfig.Memory]"
docker inspect "$REDIS_CONTAINER" --format '{{.HostConfig.Memory}}' || true

exit 0
REMOTE_SCRIPT_EOF
)

TS=$(date +%Y%m%d-%H%M%S)
OUT_DIR="infra/k6/results/redis-memory-${TS}"
mkdir -p "$OUT_DIR"
RAW_FILE="${OUT_DIR}/raw.txt"

MODE_LABEL="remote"
[ "$LOCAL_MODE" = "1" ] && MODE_LABEL="local"
echo "Redis 메모리 측정 시작 (모드: ${MODE_LABEL}, 컨테이너: ${REDIS_CONTAINER})"

if [ "$LOCAL_MODE" = "1" ]; then
	bash -s -- "$REDIS_CONTAINER" <<<"$MEASURE_SCRIPT" >"$RAW_FILE" 2>&1
else
	ssh $SSH_OPTS -i "$SSH_KEY" "$SSH_HOST" bash -s -- "$REDIS_CONTAINER" <<<"$MEASURE_SCRIPT" >"$RAW_FILE" 2>&1
fi

echo "원자료 저장: ${RAW_FILE}"

section() {
	awk -v title="$1" '
		$0 == title {flag=1; next}
		flag && /^=== / {flag=0}
		flag {print}
	' "$RAW_FILE"
}

used_h=$(grep -m1 '^used_memory_human:' "$RAW_FILE" | cut -d: -f2 || true)
peak_h=$(grep -m1 '^used_memory_peak_human:' "$RAW_FILE" | cut -d: -f2 || true)
maxmem_h=$(grep -m1 '^maxmemory_human:' "$RAW_FILE" | cut -d: -f2 || true)
used_b=$(grep -m1 '^used_memory:' "$RAW_FILE" | cut -d: -f2 || true)
maxmem_b=$(grep -m1 '^maxmemory:' "$RAW_FILE" | cut -d: -f2 || true)

echo
echo "===== 요약 ====="
if [ -n "${maxmem_b:-}" ] && [ "${maxmem_b:-0}" -gt 0 ] 2>/dev/null; then
	pct=$(awk -v u="${used_b:-0}" -v m="$maxmem_b" 'BEGIN{printf "%.1f", u/m*100}')
	echo "[메모리] used=${used_h:-?} (maxmemory 대비 ${pct}%) peak=${peak_h:-?} maxmemory=${maxmem_h:-?}"
else
	echo "[메모리] used=${used_h:-?} peak=${peak_h:-?} maxmemory=${maxmem_h:-?}(설정 없음/0)"
fi

echo
echo "[prefix 별 키 개수]"
section "=== prefix 별 키 개수 ==="

echo
echo "[TTL 없는 키의 prefix 분포]"
section "=== TTL 없는 키의 prefix 분포 ==="

echo
echo "[bigkeys 상위]"
section "=== bigkeys 요약 ===" | grep -E 'Sampled|Biggest' || echo "(요약 파싱 실패, ${RAW_FILE} 확인)"

echo
echo "요약 끝. 전체 원자료는 ${RAW_FILE} 참고."
