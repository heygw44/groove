#!/usr/bin/env bash
# 한정반 러시 도중에 장애를 주입하고(Redis 재시작/키 유실/앱 강제종료), 대사 뒤 상태를
# infra/k6/verify-oversell.sh --local --chaos 로 판정한다. limited-chaos.js 가 setup
# 직후 찍는 CHAOS_RUSH_START 로그를 기준 시각으로 삼아 장애를 주입하므로, 두 프로세스는
# 이 로그 형식(계약)을 통해서만 맞물린다.
set -euo pipefail

cd "$(dirname "$0")/../../.."

usage() {
	cat <<EOF
사용법: $(basename "$0") <redis-restart|redis-key-loss|app-kill> [--label NAME] [--out DIR]

환경변수:
  BASE_URL(기본 http://localhost:8080), INJECT_DELAY_SEC(기본 2)
  REDIS_DOWN_SEC(기본 0 - redis-restart 전용, 0 초과면 stop/sleep/start 로 정지 시간 늘림)
  VERIFY_DELAY_SEC(기본 70), BACKEND_CONTAINER(기본 groove-backend)
  REDIS_CONTAINER(기본 groove-redis), HEALTH_TIMEOUT_SEC(기본 120)
  그리고 limited-chaos.js 가 읽는 MEMBERS/STOCK/RATE/RUSH_DURATION/TAIL_RATE/TAIL_DURATION 등은
  그대로 k6 에 전달된다.
EOF
}

SCENARIO="${1:-}"
[ $# -ge 1 ] && shift

LABEL=""
OUT_DIR_ARG=""
while [ $# -gt 0 ]; do
	case "$1" in
	--label)
		LABEL="$2"
		shift 2
		;;
	--out)
		OUT_DIR_ARG="$2"
		shift 2
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

case "$SCENARIO" in
redis-restart | redis-key-loss | app-kill) ;;
*)
	echo "알 수 없는 시나리오: '${SCENARIO}'" >&2
	usage
	exit 2
	;;
esac

BASE_URL="${BASE_URL:-http://localhost:8080}"
INJECT_DELAY_SEC="${INJECT_DELAY_SEC:-2}"
REDIS_DOWN_SEC="${REDIS_DOWN_SEC:-0}"
VERIFY_DELAY_SEC="${VERIFY_DELAY_SEC:-70}"
BACKEND_CONTAINER="${BACKEND_CONTAINER:-groove-backend}"
REDIS_CONTAINER="${REDIS_CONTAINER:-groove-redis}"
HEALTH_TIMEOUT_SEC="${HEALTH_TIMEOUT_SEC:-120}"

# --- 사전 점검 ---
for tool in k6 docker node curl; do
	command -v "$tool" > /dev/null 2>&1 || {
		echo "사전 점검 실패: ${tool} 명령을 찾을 수 없다." >&2
		exit 2
	}
done

if ! curl -fsS -m 10 "${BASE_URL}/actuator/health" 2> /dev/null | grep -q '"status":"UP"'; then
	echo "사전 점검 실패: ${BASE_URL}/actuator/health 가 UP 이 아니다." >&2
	exit 2
fi

check_container_running() {
	local name="$1" state
	state=$(docker inspect -f '{{.State.Running}}' "$name" 2> /dev/null || echo "")
	if [ "$state" != "true" ]; then
		echo "사전 점검 실패: 컨테이너 ${name} 가 running 상태가 아니다." >&2
		exit 2
	fi
}
check_container_running "$BACKEND_CONTAINER"
check_container_running "$REDIS_CONTAINER"

# --- 출력 디렉토리 ---
if [ -n "$OUT_DIR_ARG" ]; then
	OUT_DIR="$OUT_DIR_ARG"
else
	SUFFIX="$SCENARIO"
	[ -n "$LABEL" ] && SUFFIX="${SUFFIX}-${LABEL}"
	OUT_DIR="infra/k6/results/chaos-${SUFFIX}-$(date +%Y%m%d-%H%M%S)"
fi
mkdir -p "$OUT_DIR"

RUN_LOG="${OUT_DIR}/run.log"
FAULT_LOG="${OUT_DIR}/fault.log"
K6_STDOUT="${OUT_DIR}/k6-stdout.log"

log() {
	echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "$RUN_LOG"
}

fault_log() {
	echo "$*" >> "$FAULT_LOG"
}

now_ms() {
	if command -v perl > /dev/null 2>&1; then
		perl -MTime::HiRes=time -e 'printf "%d\n", time*1000'
	else
		python3 -c 'import time; print(int(time.time()*1000))'
	fi
}

K6_PID=""
# shellcheck disable=SC2329 # trap 으로만 호출되어 shellcheck 가 직접 호출을 못 찾는다
cleanup() {
	if [ -n "$K6_PID" ] && kill -0 "$K6_PID" 2> /dev/null; then
		log "중단됨: 백그라운드 k6(pid=${K6_PID}) 를 종료한다"
		kill "$K6_PID" 2> /dev/null || true
	fi
}
trap cleanup EXIT INT TERM

log "=== chaos run 시작: scenario=${SCENARIO} label=${LABEL:-없음} out=${OUT_DIR} ==="

# --- k6 백그라운드 실행 ---
RESULT_DIR="$OUT_DIR" RUN_LABEL="$SCENARIO" BASE_URL="$BASE_URL" \
	k6 run --out "json=${OUT_DIR}/raw.json" infra/k6/chaos/limited-chaos.js > "$K6_STDOUT" 2>&1 &
K6_PID=$!
log "k6 실행 시작 (pid=${K6_PID})"

# --- setup 완료 + 러시 시작 로그 파싱 (최대 10분, 0.2초 간격) ---
DROP_ID=""
PRODUCT_ID=""
RUSH_START_MS=""
MAX_SETUP_ITER=3000
iter=0
while [ -z "$RUSH_START_MS" ] || [ -z "$DROP_ID" ]; do
	if [ -f "$K6_STDOUT" ]; then
		if [ -z "$DROP_ID" ]; then
			setup_line=$(grep -oE 'setup done: dropId=[0-9]+ productId=[0-9]+' "$K6_STDOUT" | tail -n1 || true)
			if [ -n "$setup_line" ]; then
				DROP_ID=$(echo "$setup_line" | grep -oE 'dropId=[0-9]+' | cut -d= -f2)
				PRODUCT_ID=$(echo "$setup_line" | grep -oE 'productId=[0-9]+' | cut -d= -f2)
			fi
		fi
		if [ -z "$RUSH_START_MS" ]; then
			rush_line=$(grep -oE 'CHAOS_RUSH_START ms=[0-9]+' "$K6_STDOUT" | tail -n1 || true)
			[ -n "$rush_line" ] && RUSH_START_MS=$(echo "$rush_line" | grep -oE '[0-9]+$')
		fi
	fi

	[ -n "$DROP_ID" ] && [ -n "$RUSH_START_MS" ] && break

	if ! kill -0 "$K6_PID" 2> /dev/null; then
		log "k6(pid=${K6_PID}) 가 setup 완료 전에 종료됐다. 로그 마지막 40줄:"
		tail -n 40 "$K6_STDOUT" | tee -a "$RUN_LOG"
		exit 1
	fi

	iter=$((iter + 1))
	if [ "$iter" -ge "$MAX_SETUP_ITER" ]; then
		log "setup 로그 파싱 타임아웃(600초). 로그 마지막 40줄:"
		tail -n 40 "$K6_STDOUT" | tee -a "$RUN_LOG"
		exit 1
	fi
	sleep 0.2
done

echo "${DROP_ID} ${PRODUCT_ID}" > "${OUT_DIR}/drop-id.txt"
log "setup 파싱 완료: dropId=${DROP_ID} productId=${PRODUCT_ID} rushStartMs=${RUSH_START_MS}"

# --- 장애 주입 시각까지 대기 ---
TARGET_MS=$((RUSH_START_MS + INJECT_DELAY_SEC * 1000))
while [ "$(now_ms)" -lt "$TARGET_MS" ]; do
	sleep 0.1
done

FAULT_START_MS=$(now_ms)
fault_log "FAULT_START scenario=${SCENARIO} ms=${FAULT_START_MS}"
log "장애 주입 시작: scenario=${SCENARIO} ms=${FAULT_START_MS}"

inject_redis_restart() {
	if [ "$REDIS_DOWN_SEC" -eq 0 ]; then
		fault_log "INFO docker compose restart redis"
		docker compose restart redis >> "$RUN_LOG" 2>&1
	else
		fault_log "INFO docker compose stop redis, ${REDIS_DOWN_SEC}초 대기 후 start"
		docker compose stop redis >> "$RUN_LOG" 2>&1
		sleep "$REDIS_DOWN_SEC"
		docker compose start redis >> "$RUN_LOG" 2>&1
	fi
	while ! docker exec "$REDIS_CONTAINER" redis-cli ping 2> /dev/null | grep -q PONG; do
		sleep 0.2
	done
	fault_log "INFO redis PONG 확인"
}

inject_redis_key_loss() {
	local deleted
	deleted=$(docker exec "$REDIS_CONTAINER" redis-cli DEL "limited:stock:${DROP_ID}" "limited:buyers:${DROP_ID}")
	fault_log "INFO DEL limited:stock:${DROP_ID} limited:buyers:${DROP_ID} deletedCount=${deleted}"
}

inject_app_kill() {
	fault_log "INFO docker kill -s KILL ${BACKEND_CONTAINER}"
	docker kill -s KILL "$BACKEND_CONTAINER" >> "$RUN_LOG" 2>&1
	docker start "$BACKEND_CONTAINER" >> "$RUN_LOG" 2>&1
	local waited_x2=0
	local max_x2=$((HEALTH_TIMEOUT_SEC * 2))
	while true; do
		if curl -fsS -m 5 "${BASE_URL}/actuator/health" 2> /dev/null | grep -q '"UP"'; then
			fault_log "INFO 헬스체크 UP 확인"
			break
		fi
		waited_x2=$((waited_x2 + 1))
		if [ "$waited_x2" -ge "$max_x2" ]; then
			fault_log "INFO 헬스체크 대기 ${HEALTH_TIMEOUT_SEC}초 초과, 포기하고 진행"
			log "경고: app-kill 복구 헬스체크가 ${HEALTH_TIMEOUT_SEC}초 안에 UP 이 되지 않았다"
			break
		fi
		sleep 0.5
	done
}

case "$SCENARIO" in
redis-restart) inject_redis_restart ;;
redis-key-loss) inject_redis_key_loss ;;
app-kill) inject_app_kill ;;
esac

FAULT_END_MS=$(now_ms)
fault_log "FAULT_END scenario=${SCENARIO} ms=${FAULT_END_MS}"
log "장애 복구 확인: scenario=${SCENARIO} ms=${FAULT_END_MS} (창 길이 $((FAULT_END_MS - FAULT_START_MS))ms)"

# --- k6 종료 대기 ---
set +e
wait "$K6_PID"
K6_EXIT=$?
set -e
echo "$K6_EXIT" > "${OUT_DIR}/exit-code.txt"
log "k6 종료 (exit=${K6_EXIT})"

# --- 대사 주기가 돌 시간을 준 뒤 판정 ---
log "대사 대기 ${VERIFY_DELAY_SEC}초"
sleep "$VERIFY_DELAY_SEC"

VERIFY_EXIT=0
if [ -x infra/k6/verify-oversell.sh ]; then
	set +e
	infra/k6/verify-oversell.sh --local --chaos "$DROP_ID" "$PRODUCT_ID" "${OUT_DIR}/verify.txt" | tee -a "$RUN_LOG"
	VERIFY_EXIT=${PIPESTATUS[0]}
	set -e
	log "verify-oversell.sh 종료 (exit=${VERIFY_EXIT})"
else
	log "verify-oversell.sh 가 아직 없어(또는 실행 권한 없음) 판정을 건너뜀"
	VERIFY_EXIT=2
fi

# --- 사후 분석 ---
if [ -f infra/k6/chaos/analyze-chaos.mjs ]; then
	node infra/k6/chaos/analyze-chaos.mjs "${OUT_DIR}/raw.json" "$FAULT_LOG" | tee "${OUT_DIR}/analysis.md"
else
	log "analyze-chaos.mjs 가 아직 없어 분석을 건너뜀"
fi

# --- 백엔드 로그 증거 ---
# 컨테이너를 다시 만들면(--force-recreate) 로그가 사라지므로 전체 로그를 파일로 남겨 둔다.
RUSH_START_SEC=$((RUSH_START_MS / 1000))
docker logs --since "$RUSH_START_SEC" "$BACKEND_CONTAINER" > "${OUT_DIR}/backend-full.log" 2>&1 || true
grep -E '한정반 (Redis 대사 보정|Redis 재고 키 재적재|Redis 서킷|Redis 선점 실패)' "${OUT_DIR}/backend-full.log" \
	> "${OUT_DIR}/backend-limited.log" || true

# --- 최종 요약 ---
FINAL_EXIT=1
if [ "$VERIFY_EXIT" -ne 0 ]; then
	FINAL_EXIT="$VERIFY_EXIT"
elif [ "$K6_EXIT" -eq 0 ]; then
	FINAL_EXIT=0
fi

log "=== chaos run 종료 ==="
log "outDir=${OUT_DIR} dropId=${DROP_ID} productId=${PRODUCT_ID}"
log "장애 창=$((FAULT_END_MS - FAULT_START_MS))ms verifyExit=${VERIFY_EXIT} k6Exit=${K6_EXIT} finalExit=${FINAL_EXIT}"

exit "$FINAL_EXIT"
